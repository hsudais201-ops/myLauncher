package com.flame.launcher.content

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Locale

sealed interface MinecraftContentInstallTransactionResult {
    data class Installed(val installedPaths: List<String>) : MinecraftContentInstallTransactionResult
    data class Failed(
        val reason: String,
        val rolledBackPaths: List<String>,
        val rollbackFailures: List<String> = emptyList()
    ) : MinecraftContentInstallTransactionResult
}

/**
 * Filesystem boundary kept injectable so transaction failure and rollback can be tested
 * without touching a real Minecraft instance.
 */
interface MinecraftInstallFileOperations {
    fun canonical(file: File): File
    fun exists(file: File): Boolean
    fun isRegularFile(file: File): Boolean
    fun canRead(file: File): Boolean
    fun mkdirs(directory: File): Boolean
    fun copyAndSync(source: File, destination: File)
    fun moveNoReplace(source: File, destination: File)
    fun delete(file: File): Boolean
}

private object DefaultMinecraftInstallFileOperations : MinecraftInstallFileOperations {
    override fun canonical(file: File): File = file.canonicalFile
    override fun exists(file: File): Boolean = Files.exists(file.toPath())
    override fun isRegularFile(file: File): Boolean = Files.isRegularFile(file.toPath())
    override fun canRead(file: File): Boolean = file.canRead()
    override fun mkdirs(directory: File): Boolean = directory.mkdirs() || directory.isDirectory

    override fun copyAndSync(source: File, destination: File) {
        FileOutputStream(destination).use { output ->
            source.inputStream().use { input -> input.copyTo(output, 32 * 1024) }
            output.fd.sync()
        }
    }

    override fun moveNoReplace(source: File, destination: File) {
        // Same-directory move, deliberately without ATOMIC_MOVE or REPLACE_EXISTING.
        // The default provider performs a same-filesystem rename and must fail if target exists.
        Files.move(source.toPath(), destination.toPath())
    }

    override fun delete(file: File): Boolean = Files.deleteIfExists(file.toPath())
}

/**
 * Commits only verified staged downloads from a planner-produced plan.
 *
 * Preflight checks every source and destination before creating target directories.
 * Existing destination files are always rejected. If a later commit fails, only files
 * created by this transaction are deleted; pre-existing files are never touched.
 */
data class MinecraftAdditionalInstallFile(
    val stagedFile: File,
    val destination: File,
    val approvedRoot: File,
    val expectedSizeBytes: Long,
    val expectedSha256: String,
    val provider: ContentProviderId = ContentProviderId.MODRINTH,
    val projectId: String = "modpack",
    val versionId: String = "imported",
    val contentType: MinecraftContentType = MinecraftContentType.MODPACK
)

class MinecraftContentInstallTransaction(
    private val files: MinecraftInstallFileOperations = DefaultMinecraftInstallFileOperations
) {
    private data class ItemKey(
        val provider: ContentProviderId,
        val projectId: String,
        val versionId: String
    )

    private data class Prepared(
        val staged: File,
        val destination: File,
        val destinationDirectory: File,
        val expectedRoot: File
    )

    fun install(
        plan: MinecraftContentInstallPlan,
        stagedDownloads: List<MinecraftStagedDownloadResult.Downloaded>,
        stagingDirectory: File,
        appPrivateStagingDirectory: File? = null,
        additionalFiles: List<MinecraftAdditionalInstallFile> = emptyList()
    ): MinecraftContentInstallTransactionResult {
        if (plan.items.isEmpty()) return failed("Install plan is empty")
        if (stagedDownloads.size != plan.items.size) {
            return failed("Every planned item must have exactly one verified staged download")
        }

        val stagingRoot = try {
            files.canonical(stagingDirectory)
        } catch (_: Exception) {
            return failed("Invalid staging directory")
        }
        if (!stagingRoot.isDirectory) return failed("Staging directory does not exist")

        val appPrivateRoot = if (plan.items.any { it.action == MinecraftInstallAction.STAGE_MODPACK_ARCHIVE }) {
            val supplied = appPrivateStagingDirectory
                ?: return failed("App-private modpack staging directory is required")
            try {
                files.canonical(supplied)
            } catch (_: Exception) {
                return failed("Invalid app-private staging directory")
            }.also {
                if (!it.isDirectory) return failed("App-private staging directory does not exist")
            }
        } else null

        val downloadsByKey = stagedDownloads.groupBy {
            ItemKey(it.install.provider, it.install.projectId, it.install.versionId)
        }
        if (downloadsByKey.values.any { it.size != 1 }) {
            return failed("Duplicate staged download identity")
        }

        val prepared = mutableListOf<Prepared>()
        for (install in plan.items) {
            val key = ItemKey(install.provider, install.projectId, install.versionId)
            val download = downloadsByKey[key]?.singleOrNull()
                ?: return failed("Missing staged download for ${install.projectId}")
            if (download.install != install) {
                return failed("Staged download metadata does not match the install plan")
            }

            val staged = try {
                files.canonical(download.stagedFile)
            } catch (_: Exception) {
                return failed("Invalid staged file for ${install.projectId}")
            }
            if (!isWithin(stagingRoot, staged) || !files.isRegularFile(staged) || !files.canRead(staged)) {
                return failed("Staged file is missing, unreadable, or outside the staging directory")
            }
            val size = staged.length()
            if (size <= 0L || size > MAX_FILE_SIZE_BYTES ||
                (install.expectedSizeBytes != null && size != install.expectedSizeBytes)
            ) {
                return failed("Staged file size verification failed for ${install.projectId}")
            }
            if (!verifyDigest(staged, install.sha1, "SHA-1") ||
                !verifyDigest(staged, install.sha512, "SHA-512")
            ) {
                return failed("Staged file checksum verification failed for ${install.projectId}")
            }

            val expectedRoot = expectedDestinationRoot(plan, install, appPrivateRoot)
                ?: return failed("Install destination is not supported by this target")
            val destination = File(install.destinationPath)
            val directory = destination.parentFile
                ?: return failed("Install destination has no parent directory")
            val canonicalDirectory = try {
                files.canonical(directory)
            } catch (_: Exception) {
                return failed("Invalid install destination directory")
            }
            val canonicalDestination = File(canonicalDirectory, destination.name)
            if (!isWithin(expectedRoot, canonicalDestination)) {
                return failed("Install destination escapes its approved directory")
            }
            if (!isSafeLeaf(destination.name)) return failed("Unsafe install destination file name")
            if (files.exists(canonicalDestination)) {
                return failed("Destination already exists; existing files are never overwritten")
            }
            prepared += Prepared(staged, canonicalDestination, canonicalDirectory, expectedRoot)
        }

        for (additional in additionalFiles) {
            val staged = try { files.canonical(additional.stagedFile) } catch (_: Exception) {
                return failed("Invalid additional staged file")
            }
            if (!isWithin(stagingRoot, staged) || !files.isRegularFile(staged) || !files.canRead(staged)) {
                return failed("Additional staged file is missing, unreadable, or outside staging")
            }
            if (additional.expectedSizeBytes <= 0L || staged.length() != additional.expectedSizeBytes ||
                !verifyDigest(staged, additional.expectedSha256, "SHA-256")
            ) return failed("Additional staged file verification failed")
            val expectedRoot = try { files.canonical(additional.approvedRoot) } catch (_: Exception) {
                return failed("Invalid additional destination root")
            }
            val destination = additional.destination
            val directory = destination.parentFile ?: return failed("Additional destination has no parent")
            val canonicalDirectory = try { files.canonical(directory) } catch (_: Exception) {
                return failed("Invalid additional destination directory")
            }
            val canonicalDestination = File(canonicalDirectory, destination.name)
            if (!isSafeLeaf(destination.name) || !isWithin(expectedRoot, canonicalDestination)) {
                return failed("Additional destination escapes its approved directory")
            }
            if (files.exists(canonicalDestination)) return failed("Additional destination already exists")
            prepared += Prepared(staged, canonicalDestination, canonicalDirectory, expectedRoot)
        }

        if (prepared.map { it.destination.path.lowercase(Locale.ROOT) }.distinct().size != prepared.size) {
            return failed("Install plan contains duplicate destination paths")
        }

        val created = mutableListOf<File>()
        val temporaryFiles = mutableListOf<File>()
        try {
            prepared.forEachIndexed { index, item ->
                if (!item.destinationDirectory.exists() &&
                    !files.mkdirs(item.destinationDirectory)
                ) {
                    throw IllegalStateException("Could not create destination directory")
                }
                // Re-check after directory creation to close the common preflight/commit race.
                val recheckedDirectory = files.canonical(item.destinationDirectory)
                if (!isWithinOrEqual(item.expectedRoot, recheckedDirectory)) {
                    throw IllegalStateException("Destination directory changed during installation")
                }
                if (files.exists(item.destination)) {
                    throw IllegalStateException("Destination appeared during installation")
                }

                val temporary = File.createTempFile(".flame-install-$index-", ".part", recheckedDirectory)
                temporaryFiles += temporary
                files.copyAndSync(item.staged, temporary)
                if (files.exists(item.destination)) {
                    throw IllegalStateException("Destination appeared during installation")
                }
                files.moveNoReplace(temporary, item.destination)
                temporaryFiles.remove(temporary)
                created += item.destination
            }
            return MinecraftContentInstallTransactionResult.Installed(created.map { it.path })
        } catch (error: Exception) {
            val rollbackFailures = mutableListOf<String>()
            temporaryFiles.forEach { temp ->
                try {
                    if (files.exists(temp) && !files.delete(temp)) rollbackFailures += temp.path
                } catch (_: Exception) {
                    rollbackFailures += temp.path
                }
            }
            created.asReversed().forEach { installed ->
                try {
                    if (files.exists(installed) && !files.delete(installed)) rollbackFailures += installed.path
                } catch (_: Exception) {
                    rollbackFailures += installed.path
                }
            }
            return MinecraftContentInstallTransactionResult.Failed(
                reason = error.message?.take(240) ?: "Install transaction failed",
                rolledBackPaths = created.map { it.path },
                rollbackFailures = rollbackFailures
            )
        }
    }

    /**
     * Compensates a completed install when ownership persistence fails. A file is deleted
     * only when its canonical path and SHA-256 still match the transaction's staged bytes.
     */
    fun rollbackInstalled(
        installedPaths: List<String>,
        expectedSha256ByPath: Map<String, String>
    ): List<String> {
        if (installedPaths.distinct().size != installedPaths.size ||
            installedPaths.toSet() != expectedSha256ByPath.keys
        ) return listOf("Installed path set did not match rollback verification data")

        val failures = mutableListOf<String>()
        installedPaths.asReversed().forEach { path ->
            val file = File(path)
            try {
                val canonical = files.canonical(file)
                if (canonical.path != path || !files.isRegularFile(canonical) || !files.canRead(canonical) ||
                    !verifyDigest(canonical, expectedSha256ByPath[path], "SHA-256")
                ) {
                    failures += path
                } else if (!files.delete(canonical)) {
                    failures += path
                }
            } catch (_: Exception) {
                failures += path
            }
        }
        return failures
    }

    private fun expectedDestinationRoot(
        plan: MinecraftContentInstallPlan,
        install: MinecraftPlannedInstall,
        appPrivateRoot: File?
    ): File? {
        val raw = when (install.action) {
            MinecraftInstallAction.STAGE_MODPACK_ARCHIVE -> appPrivateRoot
            MinecraftInstallAction.INSTALL_FILE -> when (install.contentType) {
                MinecraftContentType.MOD -> File(plan.target.contentDirectory, "mods")
                MinecraftContentType.RESOURCE_PACK -> File(plan.target.contentDirectory, "resourcepacks")
                MinecraftContentType.SHADER -> File(plan.target.contentDirectory, "shaderpacks")
                MinecraftContentType.DATAPACK -> plan.target.selectedWorldDirectory?.let {
                    File(it, "datapacks")
                }
                MinecraftContentType.MODPACK -> null
            }
        } ?: return null
        return try {
            files.canonical(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun verifyDigest(file: File, expected: String?, algorithm: String): Boolean {
        if (expected == null) return true
        return try {
            val digest = MessageDigest.getInstance(algorithm)
            file.inputStream().use { input ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            digest.digest().toHex().equals(expected, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun isWithinOrEqual(root: File, candidate: File): Boolean {
        val rootPath = root.toPath().normalize()
        val candidatePath = candidate.toPath().normalize()
        return candidatePath == rootPath || candidatePath.startsWith(rootPath)
    }

    private fun isWithin(root: File, candidate: File): Boolean {
        val rootPath = root.toPath().normalize()
        val candidatePath = candidate.toPath().normalize()
        return candidatePath != rootPath && candidatePath.startsWith(rootPath)
    }

    private fun isSafeLeaf(value: String): Boolean =
        value.isNotBlank() && value == value.trim() && value != "." && value != ".." &&
            value.none { it == '/' || it == '\\' || it == ':' || it.code < 32 || it.code == 127 }

    private fun failed(reason: String) = MinecraftContentInstallTransactionResult.Failed(
        reason = reason,
        rolledBackPaths = emptyList()
    )

    private companion object {
        const val MAX_FILE_SIZE_BYTES = 1L shl 30
    }
}
