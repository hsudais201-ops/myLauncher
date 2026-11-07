package com.flame.launcher.runtime

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Installs a caller-supplied Java runtime ZIP into Flame's private runtime area.
 *
 * The archive hash is verified before extraction. Extraction is streamed, bounded,
 * rejects traversal/absolute paths, and is staged beside the destination so the
 * final directory swap can use same-filesystem renames.
 *
 * Run install() from a worker thread. The caller owns the trust decision for the
 * supplied SHA-256 and package source; this class does not fetch runtime archives.
 */
class JavaRuntimePackageInstaller(
    runtimeRoot: File,
    deviceArchitecture: String,
    private val maxArchiveBytes: Long = DEFAULT_MAX_ARCHIVE_BYTES,
    private val maxExpandedBytes: Long = DEFAULT_MAX_EXPANDED_BYTES,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    private val root = runtimeRoot.canonicalFile
    private val deviceAbi = JavaRuntimeManager.normalizeArchitecture(deviceArchitecture) ?: "unknown"

    init {
        require(maxArchiveBytes in 1..ABSOLUTE_MAX_BYTES) { "Archive size limit is invalid." }
        require(maxExpandedBytes in 1..ABSOLUTE_MAX_BYTES) { "Expanded size limit is invalid." }
        require(maxEntries in 1..ABSOLUTE_MAX_ENTRIES) { "ZIP entry limit is invalid." }
    }

    @Synchronized
    fun install(
        id: String,
        archiveInput: InputStream,
        expectedSha256: String,
        expectedMajorVersion: Int,
        expectedArchitecture: String
    ): JavaRuntimeInstallation {
        require(RUNTIME_ID.matches(id)) { "Runtime package ID is invalid." }
        require(SHA256.matches(expectedSha256)) { "Runtime package SHA-256 is invalid." }
        require(expectedMajorVersion in 1..100) { "Required Java major version is invalid." }
        val expectedAbi = JavaRuntimeManager.normalizeArchitecture(expectedArchitecture)
            ?: throw IllegalArgumentException("Runtime package architecture is unsupported.")
        require(deviceAbi != "unknown" && expectedAbi == deviceAbi) {
            "Runtime package architecture does not match this device."
        }

        if (!root.exists() && !root.mkdirs()) {
            throw IllegalStateException("Unable to create the private runtime directory.")
        }
        require(root.isDirectory) { "Runtime destination is not a directory." }

        val staging = File(root, ".flame-stage-" + id + "-" + UUID.randomUUID())
        check(staging.mkdir()) { "Unable to create a runtime staging directory." }
        val archive = File(staging, "runtime.zip")
        val payload = File(staging, "runtime")
        check(payload.mkdir()) { "Unable to create the runtime extraction directory." }

        try {
            val actualSha256 = copyArchive(archiveInput, archive)
            require(actualSha256.equals(expectedSha256, ignoreCase = true)) {
                "Runtime package SHA-256 verification failed."
            }

            extractArchive(archive, payload)
            val runtimeHome = findRuntimeHome(payload)
            listOf("bin/java", "bin/javaw").forEach { relative ->
                val executable = File(runtimeHome, relative)
                if (executable.isFile && !executable.setExecutable(true, true) && !executable.canExecute()) {
                    throw IllegalStateException("Unable to mark the runtime launcher executable.")
                }
            }

            val stagedRuntime = JavaRuntimeManager(runtimeHome.parentFile, deviceAbi)
                .discover()
                .firstOrNull { it.id == runtimeHome.name }
                ?: throw IllegalArgumentException("Archive does not contain a valid Java runtime root.")
            require(stagedRuntime.majorVersion == expectedMajorVersion) {
                "Runtime Java major version does not match the requested version."
            }
            require(stagedRuntime.architecture == expectedAbi) {
                "Runtime architecture metadata does not match the requested architecture."
            }

            val destination = File(root, id)
            require(destination.canonicalFile.parentFile == root) {
                "Runtime destination resolves outside the private runtime directory."
            }
            var backup: File? = null
            if (destination.exists()) {
                val backupDirectory = File(root, ".flame-backup-" + id + "-" + UUID.randomUUID())
                check(destination.renameTo(backupDirectory)) {
                    "Unable to stage the existing runtime for replacement."
                }
                backup = backupDirectory
            }

            var promoted = false
            try {
                check(runtimeHome.renameTo(destination)) {
                    "Unable to move the verified runtime into its destination."
                }
                promoted = true
                val installed = JavaRuntimeManager(root, deviceAbi)
                    .discover()
                    .firstOrNull { it.id == id }
                    ?: throw IllegalStateException("Installed runtime failed its final validation.")
                require(installed.majorVersion == expectedMajorVersion &&
                    installed.architecture == expectedAbi
                ) { "Installed runtime metadata changed during promotion." }

                backup?.deleteRecursively()
                return installed
            } catch (failure: Exception) {
                if (promoted) destination.deleteRecursively()
                val previous = backup
                if (previous != null && previous.exists() && !previous.renameTo(destination)) {
                    failure.addSuppressed(
                        IllegalStateException("The previous runtime could not be restored from backup.")
                    )
                }
                throw failure
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun copyArchive(input: InputStream, destination: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        input.use { source ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val count = source.read(buffer)
                    if (count == -1) break
                    require(count > 0) { "Runtime archive stream returned an invalid read length." }
                    require(copied <= maxArchiveBytes - count) {
                        "Runtime archive exceeds the configured size limit."
                    }
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                    copied += count
                }
            }
        }
        require(copied > 0L) { "Runtime archive is empty." }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun extractArchive(archive: File, destination: File) {
        val destinationPrefix = destination.canonicalPath + File.separator
        val seenEntries = HashSet<String>()
        var entryCount = 0
        var expandedBytes = 0L

        ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount += 1
                require(entryCount <= maxEntries) { "Runtime archive contains too many entries." }

                val relativePath = validateEntryPath(entry.name, entry.isDirectory)
                require(seenEntries.add(relativePath)) { "Runtime archive contains a duplicate path." }
                val outputFile = File(destination, relativePath).canonicalFile
                require(outputFile.path.startsWith(destinationPrefix)) {
                    "Runtime archive entry escapes its extraction directory."
                }

                if (entry.isDirectory) {
                    require(outputFile.mkdirs() || outputFile.isDirectory) {
                        "Unable to create a runtime directory."
                    }
                } else {
                    val parent = outputFile.parentFile
                        ?: throw IllegalArgumentException("Runtime archive entry has no parent directory.")
                    require(parent.mkdirs() || parent.isDirectory) {
                        "Unable to create a runtime file's parent directory."
                    }
                    if (entry.size >= 0L) {
                        require(entry.size <= maxExpandedBytes) {
                            "Runtime archive entry exceeds the expanded size limit."
                        }
                    }
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var entryBytes = 0L
                        while (true) {
                            val count = zip.read(buffer)
                            if (count == -1) break
                            require(count > 0) { "Runtime archive returned an invalid read length." }
                            val increment = count.toLong()
                            require(entryBytes <= maxExpandedBytes - increment &&
                                expandedBytes <= maxExpandedBytes - increment
                            ) { "Runtime archive exceeds the expanded size limit." }
                            output.write(buffer, 0, count)
                            entryBytes += increment
                            expandedBytes += increment
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        require(expandedBytes > 0L) {
            "Runtime archive is empty."
        }
    }

    /**
     * Runtime archives commonly wrap their JRE in one top-level directory.
     * Accept either a root-level release file or exactly one immediate child
     * containing release metadata; reject ambiguous layouts.
     */
    private fun findRuntimeHome(extractionRoot: File): File {
        if (File(extractionRoot, "release").isFile) return extractionRoot
        val candidates = extractionRoot.listFiles()
            .orEmpty()
            .filter { it.isDirectory && File(it, "release").isFile }
        require(candidates.size == 1) {
            "Runtime archive must contain one unambiguous Java runtime root."
        }
        return candidates.single().canonicalFile.also { candidate ->
            require(candidate.parentFile == extractionRoot.canonicalFile) {
                "Runtime root resolves outside the extraction directory."
            }
        }
    }

    private fun validateEntryPath(rawPath: String, isDirectory: Boolean): String {
        require(rawPath.isNotBlank()) { "Runtime archive contains an empty path." }
        require(!rawPath.startsWith("/") && '\\' !in rawPath && ':' !in rawPath) {
            "Runtime archive contains an absolute or invalid path."
        }
        require(rawPath.none { it.code < 0x20 }) { "Runtime archive path contains control characters." }
        val normalized = if (isDirectory) rawPath.removeSuffix("/") else rawPath
        require(normalized.isNotBlank()) { "Runtime archive contains an empty path." }
        val segments = normalized.split('/')
        require(segments.all { it.isNotBlank() && it != "." && it != ".." }) {
            "Runtime archive contains a traversal path."
        }
        return segments.joinToString("/")
    }

    companion object {
        private const val DEFAULT_MAX_ARCHIVE_BYTES = 512L * 1024L * 1024L
        private const val DEFAULT_MAX_EXPANDED_BYTES = 1024L * 1024L * 1024L
        private const val ABSOLUTE_MAX_BYTES = 2L * 1024L * 1024L * 1024L
        private const val DEFAULT_MAX_ENTRIES = 50_000
        private const val ABSOLUTE_MAX_ENTRIES = 100_000
        private const val BUFFER_SIZE = 32 * 1024
        private val RUNTIME_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
        private val SHA256 = Regex("[a-fA-F0-9]{64}")
    }
}
