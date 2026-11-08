package com.flame.launcher.runtime

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Safely extracts Mojang native-library ZIP/JAR archives into a version-scoped
 * native directory. Extraction is staged and promoted only after every archive
 * has passed path and size checks.
 *
 * ZIP entries are treated as untrusted. Extraction is sequential and bounded;
 * symbolic links and special files are never created.
 */
class MinecraftNativeArchiveExtractor(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxEntryBytes: Long = DEFAULT_MAX_ENTRY_BYTES,
    private val maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES
) {
    init {
        require(maxEntries in 1..ABSOLUTE_MAX_ENTRIES)
        require(maxEntryBytes in 1..ABSOLUTE_MAX_ENTRY_BYTES)
        require(maxTotalBytes in 1..ABSOLUTE_MAX_TOTAL_BYTES)
    }

    fun extract(archives: List<MinecraftNativeArchive>, destination: File): List<File> {
        require(archives.isNotEmpty()) { "No Minecraft native archives were supplied." }
        require(archives.size <= MAX_ARCHIVES) { "Too many Minecraft native archives." }
        archives.forEach {
            require(it.file.isFile && !java.nio.file.Files.isSymbolicLink(it.file.toPath())) {
                "Minecraft native archive is missing or is not a regular file."
            }
        }
        val parent = destination.absoluteFile.parentFile
            ?: throw IllegalArgumentException("Minecraft native destination has no parent.")
        if (!parent.exists()) require(parent.mkdirs()) { "Unable to create native parent directory." }
        require(parent.isDirectory && parent.canonicalFile == parent.absoluteFile) {
            "Minecraft native destination parent is invalid."
        }
        require(!java.nio.file.Files.isSymbolicLink(destination.toPath())) {
            "Minecraft native destination cannot be a symbolic link."
        }
        val stage = File(parent, ".${destination.name}.stage-${UUID.randomUUID()}")
        require(stage.mkdir()) { "Unable to create native extraction staging directory." }
        var backup: File? = null
        try {
            val extracted = LinkedHashMap<String, File>()
            var entriesSeen = 0
            var totalBytes = 0L
            archives.forEach { archive ->
                val excludes = archive.excludes.map(::normalizeExclude)
                ZipInputStream(FileInputStream(archive.file).buffered()).use { zip ->
                    while (true) {
                        if (Thread.currentThread().isInterrupted) {
                            throw InterruptedException("Minecraft native extraction was cancelled.")
                        }
                        val entry = zip.nextEntry ?: break
                        entriesSeen++
                        require(entriesSeen <= maxEntries) { "Minecraft native archive contains too many entries." }
                        val isDirectory = entry.isDirectory
                        val relative = normalizeEntry(entry.name, isDirectory)
                        if (relative.isEmpty() || isExcluded(relative, excludes)) {
                            zip.closeEntry()
                            continue
                        }
                        val output = safeResolve(stage, relative)
                        if (isDirectory) {
                            require(!output.exists() || output.isDirectory) {
                                "Native archive directory conflicts with an existing file."
                            }
                            if (!output.exists()) require(output.mkdirs()) { "Unable to create native directory." }
                        } else {
                            require(!output.exists()) { "Native archives contain a duplicate native file: $relative" }
                            output.parentFile?.let { directory ->
                                if (!directory.exists()) require(directory.mkdirs()) {
                                    "Unable to create native extraction directory."
                                }
                            }
                            var entryBytes = 0L
                            FileOutputStream(output).use { file ->
                                BufferedOutputStream(file).use { out ->
                                    val buffer = ByteArray(BUFFER_SIZE)
                                    while (true) {
                                        if (Thread.currentThread().isInterrupted) {
                                            throw InterruptedException("Minecraft native extraction was cancelled.")
                                        }
                                        val count = zip.read(buffer)
                                        if (count == -1) break
                                        require(count > 0) { "Native archive returned an invalid read length." }
                                        require(entryBytes <= maxEntryBytes - count &&
                                            totalBytes <= maxTotalBytes - count
                                        ) { "Minecraft native archive exceeds extraction size limits." }
                                        out.write(buffer, 0, count)
                                        entryBytes += count
                                        totalBytes += count
                                    }
                                }
                            }
                            require(entryBytes > 0L || entry.size == 0L) {
                                "Minecraft native archive entry size is invalid."
                            }
                            if (entry.size >= 0L) require(entry.size == entryBytes) {
                                "Minecraft native archive entry size does not match its data."
                            }
                            extracted[relative] = output
                        }
                        zip.closeEntry()
                    }
                }
            }
            require(extracted.isNotEmpty()) { "Minecraft native archives contained no extractable files." }
            if (destination.exists()) {
                require(destination.isDirectory) { "Minecraft native destination is not a directory." }
                backup = File(parent, ".${destination.name}.backup-${UUID.randomUUID()}")
                check(destination.renameTo(backup)) { "Unable to stage previous native directory." }
            }
            try {
                check(stage.renameTo(destination)) { "Unable to promote extracted native libraries." }
                backup?.deleteRecursively()
            } catch (failure: Exception) {
                if (backup != null && backup.exists() && !backup.renameTo(destination)) {
                    failure.addSuppressed(IllegalStateException("Previous native directory could not be restored."))
                }
                throw failure
            }
            return extracted.keys.map { File(destination, it) }
        } finally {
            stage.deleteRecursively()
            if (backup != null && backup.exists() && !destination.exists()) backup.renameTo(destination)
        }
    }

    private fun safeResolve(root: File, relative: String): File {
        val output = File(root, relative).absoluteFile
        require(output.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())) {
            "Native archive entry escapes the extraction directory."
        }
        return output
    }

    private fun normalizeEntry(raw: String, directory: Boolean): String {
        require(raw.isNotBlank() && raw.length <= MAX_PATH_LENGTH &&
            !raw.startsWith('/') && !raw.startsWith('\\') &&
            '\\' !in raw && ':' !in raw && raw.none { it.code < 0x20 }
        ) { "Native archive contains an unsafe entry path." }
        val value = if (directory) raw.removeSuffix("/") else raw
        require(value.isNotBlank() && value.split('/').all {
            it.isNotBlank() && it != "." && it != ".."
        }) { "Native archive contains an unsafe entry path." }
        return value
    }

    private fun normalizeExclude(raw: String): String {
        require(raw.isNotBlank() && raw.length <= MAX_PATH_LENGTH &&
            !raw.startsWith('/') && '\\' !in raw && ':' !in raw &&
            raw.split('/').all { it.isNotBlank() && it != "." && it != ".." }
        ) { "Native extraction exclusion is unsafe." }
        return raw
    }

    private fun isExcluded(path: String, excludes: List<String>): Boolean =
        excludes.any { path == it || path.startsWith("$it/") }

    data class MinecraftNativeArchive(val file: File, val excludes: List<String> = emptyList())

    companion object {
        private const val BUFFER_SIZE = 16 * 1024
        private const val MAX_PATH_LENGTH = 2048
        private const val MAX_ARCHIVES = 256
        private const val DEFAULT_MAX_ENTRIES = 20_000
        private const val ABSOLUTE_MAX_ENTRIES = 100_000
        private const val DEFAULT_MAX_ENTRY_BYTES = 256L * 1024 * 1024
        private const val ABSOLUTE_MAX_ENTRY_BYTES = 1024L * 1024 * 1024
        private const val DEFAULT_MAX_TOTAL_BYTES = 512L * 1024 * 1024
        private const val ABSOLUTE_MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024
    }
}
