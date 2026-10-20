package com.voidcraft.launcher.core

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class InstallArtifactProgress(
    val artifactIndex: Int,
    val artifactCount: Int,
    val artifact: MinecraftPlannedDownload,
    val fileProgress: FileDownloadProgress
)

data class MinecraftInstallResult(
    val versionId: String,
    val receipts: List<FileDownloadReceipt>
) {
    val totalBytesDownloaded: Long
        get() = receipts.fold(0L) { total, receipt ->
            if (Long.MAX_VALUE - total < receipt.bytesDownloaded) Long.MAX_VALUE
            else total + receipt.bytesDownloaded
        }
}

class IncompleteInstallPlanException(val reasons: List<String>) :
    IOException("Install plan is incomplete: " + reasons.joinToString("; "))

class InstallCommitException(message: String, cause: Throwable) : IOException(message, cause)

/**
 * Executes a fully-resolved download plan transactionally.
 *
 * Every artifact is downloaded and verified into a temporary staging tree first.
 * Final files are touched only after all downloads succeed. Existing destinations
 * are backed up while the commit runs; if a later move fails, completed moves
 * are rolled back in reverse order. If rollback itself fails, the staging tree
 * is retained as recovery data and its path is reported in InstallCommitException.
 *
 * Native extraction, inherited-version resolution, and runtime launch remain
 * outside this executor.
 */
class MinecraftInstallExecutor(
    private val downloader: SecureFileDownloader = SecureFileDownloader()
) {
    @Synchronized
    fun execute(
        plan: MinecraftInstallPlan,
        installRoot: File,
        isCancelled: () -> Boolean = { false },
        onProgress: (InstallArtifactProgress) -> Unit = {}
    ): MinecraftInstallResult {
        if (plan.warnings.isNotEmpty()) throw IncompleteInstallPlanException(plan.warnings)
        require(plan.downloads.isNotEmpty()) { "Install plan contains no downloads" }

        val relativePaths = plan.downloads.map { validateInstallPath(it.destinationRelativePath) }
        require(relativePaths.distinct().size == relativePaths.size) {
            "Install plan has duplicate destination paths"
        }

        val root = prepareRoot(installRoot)
        // Validate all final destinations for traversal/symlink escape before any network request.
        relativePaths.forEach { resolveInside(root, it, allowReserved = false) }

        val staging = try {
            Files.createTempDirectory(root.toPath(), ".voidcraft-install-").toFile().canonicalFile
        } catch (error: Exception) {
            throw IOException("Could not create staging directory under install root", error)
        }

        var retainStagingForRecovery = false
        try {
            val stagedReceipts = ArrayList<FileDownloadReceipt>(plan.downloads.size)
            plan.downloads.forEachIndexed { index, artifact ->
                if (isCancelled()) throw DownloadCancelledException()
                val stagedDestination = resolveInside(staging, relativePaths[index], allowReserved = false)
                val request = FileDownloadRequest(
                    url = artifact.url,
                    destination = stagedDestination,
                    expectedSha1 = artifact.expectedSha1,
                    expectedBytes = artifact.expectedBytes,
                    maxBytes = maxOf(
                        FileDownloadRequest.DEFAULT_MAX_BYTES,
                        artifact.expectedBytes ?: FileDownloadRequest.DEFAULT_MAX_BYTES
                    )
                )
                val receipt = downloader.download(
                    request = request,
                    isCancelled = isCancelled,
                    onProgress = { fileProgress ->
                        onProgress(InstallArtifactProgress(index + 1, plan.downloads.size, artifact, fileProgress))
                    }
                )
                stagedReceipts.add(receipt)
            }

            if (isCancelled()) throw DownloadCancelledException()
            try {
                commitStagedFiles(root, staging, relativePaths)
            } catch (error: InstallCommitException) {
                retainStagingForRecovery = true
                throw error
            }
            val finalReceipts = stagedReceipts.mapIndexed { index, receipt ->
                receipt.copy(file = resolveInside(root, relativePaths[index], allowReserved = false))
            }
            return MinecraftInstallResult(plan.versionId, finalReceipts)
        } finally {
            if (!retainStagingForRecovery) staging.deleteRecursively()
        }
    }

    private fun commitStagedFiles(root: File, staging: File, paths: List<String>) {
        val changes = mutableListOf<InstallChange>()
        try {
            paths.forEach { relativePath ->
                val stagedFile = resolveInside(staging, relativePath, allowReserved = false)
                if (!stagedFile.isFile) throw IOException("Staged artifact is missing: " + relativePath)

                val destination = resolveInside(root, relativePath, allowReserved = false)
                if (!destination.parentFile.exists() && !destination.parentFile.mkdirs()) {
                    throw IOException("Could not create destination directory for " + relativePath)
                }

                val change = InstallChange(destination)
                changes.add(change)
                if (destination.exists()) {
                    if (destination.isDirectory) {
                        throw IOException("Destination is a directory, not a file: " + relativePath)
                    }
                    val backupPath = RESERVED_BACKUP_PREFIX + relativePath
                    val backup = resolveInside(staging, backupPath, allowReserved = true)
                    if (!backup.parentFile.exists() && !backup.parentFile.mkdirs()) {
                        throw IOException("Could not create backup directory for " + relativePath)
                    }
                    moveFile(destination, backup)
                    change.backupFile = backup
                    change.originalMovedToBackup = true
                }

                moveFile(stagedFile, destination)
                change.newFileInstalled = true
            }
        } catch (error: Exception) {
            val rollbackErrors = rollback(changes)
            if (rollbackErrors.isNotEmpty()) {
                rollbackErrors.forEach { error.addSuppressed(it) }
                throw InstallCommitException(
                    "Install commit failed and rollback was incomplete. Recovery data was retained at " +
                        staging.absolutePath,
                    error
                )
            }
            throw IOException("Install commit failed; previous files were restored", error)
        }
    }

    private fun rollback(changes: List<InstallChange>): List<IOException> {
        val errors = mutableListOf<IOException>()
        changes.asReversed().forEach { change ->
            try {
                if (change.newFileInstalled && change.destination.exists() && !change.destination.delete()) {
                    throw IOException("Could not remove newly installed file: " + change.destination)
                }
                val backup = change.backupFile
                if (change.originalMovedToBackup && backup != null && backup.exists()) {
                    if (!change.destination.parentFile.exists() && !change.destination.parentFile.mkdirs()) {
                        throw IOException("Could not recreate destination directory: " + change.destination.parentFile)
                    }
                    moveFile(backup, change.destination)
                }
            } catch (error: Exception) {
                errors.add(if (error is IOException) error else IOException("Rollback failed", error))
            }
        }
        return errors
    }

    private fun prepareRoot(directory: File): File {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create install root directory")
        }
        val root = directory.canonicalFile
        if (!root.isDirectory) throw IOException("Install root is not a directory")
        return root
    }

    private fun validateInstallPath(relativePath: String): String {
        val checked = MinecraftMavenPath.validateRelativePath(relativePath)
        val firstSegment = checked.substringBefore('/')
        require(!firstSegment.startsWith(".voidcraft-")) {
            "Install path uses a reserved VoidCraft transaction name"
        }
        return checked
    }

    private fun resolveInside(root: File, relativePath: String, allowReserved: Boolean): File {
        val checked = MinecraftMavenPath.validateRelativePath(relativePath)
        if (!allowReserved && checked.substringBefore('/').startsWith(".voidcraft-")) {
            throw IOException("Install path uses a reserved VoidCraft transaction name")
        }
        val canonicalRoot = root.canonicalFile
        val target = File(canonicalRoot, checked).canonicalFile
        val rootPrefix = canonicalRoot.path.trimEnd(File.separatorChar) + File.separator
        if (!target.path.startsWith(rootPrefix)) {
            throw IOException("Resolved path escapes its root: " + relativePath)
        }
        return target
    }

    private fun moveFile(source: File, destination: File) {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (error: IOException) {
            throw IOException("Could not move " + source + " to " + destination, error)
        }
    }

    private data class InstallChange(
        val destination: File,
        var backupFile: File? = null,
        var originalMovedToBackup: Boolean = false,
        var newFileInstalled: Boolean = false
    )

    companion object {
        private const val RESERVED_BACKUP_PREFIX = ".voidcraft-txn-backups/"
    }
}
