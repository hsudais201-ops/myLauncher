package com.voidcraft.launcher.core

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

data class NativeExtractionResult(
    val directory: File,
    val filesWritten: Int,
    val bytesWritten: Long,
    val entriesSkippedByRule: Int
)

/**
 * Safely extracts downloaded native-library archives to a new, dedicated
 * directory. It never merges into or overwrites an existing directory.
 *
 * Archive paths are validated before writes, metadata-declared exclusions are
 * honoured, and per-entry/total expansion limits protect against ZIP bombs.
 * Callers should use a fresh version/launch-specific directory and must not
 * treat successful extraction as proof that the native library is Android-
 * compatible.
 */
class MinecraftNativeArchiveExtractor(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxEntryBytes: Long = DEFAULT_MAX_ENTRY_BYTES,
    private val maxExpandedBytes: Long = DEFAULT_MAX_EXPANDED_BYTES,
    private val maxCompressionRatio: Int = DEFAULT_MAX_COMPRESSION_RATIO
) {
    init {
        require(maxEntries in 1..MAX_ALLOWED_ENTRIES) { "Entry limit is outside the supported range" }
        require(maxEntryBytes in 1L..MAX_ALLOWED_EXPANDED_BYTES) { "Per-entry limit is outside the supported range" }
        require(maxExpandedBytes in 1L..MAX_ALLOWED_EXPANDED_BYTES) { "Expanded-size limit is outside the supported range" }
        require(maxEntryBytes <= maxExpandedBytes) { "Per-entry limit cannot exceed total expanded-size limit" }
        require(maxCompressionRatio in 1..MAX_ALLOWED_COMPRESSION_RATIO) {
            "Compression-ratio limit is outside the supported range"
        }
    }

    @Synchronized
    @Throws(IOException::class)
    fun extract(
        archive: File,
        destination: File,
        excludes: List<String> = emptyList(),
        isCancelled: () -> Boolean = { false }
    ): NativeExtractionResult {
        val source = archive.canonicalFile
        if (!source.isFile) throw IOException("Native archive is not a file")
        val target = destination.absoluteFile.canonicalFile
        if (target.exists()) throw IOException("Native extraction destination already exists; refusing to overwrite it")
        val parent = target.parentFile ?: throw IOException("Native extraction destination has no parent directory")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create native extraction parent directory")
        if (!parent.isDirectory) throw IOException("Native extraction parent is not a directory")

        val canonicalParent = parent.canonicalFile
        val prefix = canonicalParent.path.trimEnd(File.separatorChar) + File.separator
        if (!target.path.startsWith(prefix)) throw IOException("Native extraction destination escapes its parent directory")

        val normalizedExcludes = excludes.mapNotNull { normalizeExclude(it) }.toSet()
        val stage = try {
            Files.createTempDirectory(canonicalParent.toPath(), ".voidcraft-native-stage-").toFile()
        } catch (error: Exception) {
            throw IOException("Could not create native extraction staging directory", error)
        }
        val content = File(stage, "content")
        if (!content.mkdirs()) {
            stage.deleteRecursively()
            throw IOException("Could not create native extraction content directory")
        }

        var filesWritten = 0
        var bytesWritten = 0L
        var skipped = 0
        val seenPaths = HashSet<String>()
        try {
            ZipFile(source).use { zip ->
                val entries = zip.entries()
                var entryCount = 0
                while (entries.hasMoreElements()) {
                    checkCancelled(isCancelled)
                    val entry = entries.nextElement()
                    entryCount += 1
                    if (entryCount > maxEntries) throw IOException("Native archive exceeds the entry-count limit")

                    val relative = normalizeArchivePath(entry.name)
                    if (isExcluded(relative, normalizedExcludes)) {
                        skipped += 1
                        continue
                    }
                    if (!seenPaths.add(relative)) throw IOException("Native archive contains a duplicate path: " + relative)

                    val output = resolveInside(content, relative)
                    if (entry.isDirectory || entry.name.endsWith("/") || entry.name.endsWith('\\')) {
                        if (!output.exists() && !output.mkdirs()) throw IOException("Could not create extracted directory: " + relative)
                        if (!output.isDirectory) throw IOException("Native archive path conflicts with a file: " + relative)
                        continue
                    }

                    if (entry.size > maxEntryBytes) throw IOException("Native archive entry exceeds the per-file size limit: " + relative)
                    val compressed = entry.compressedSize
                    if (entry.size > 0L && compressed == 0L) {
                        throw IOException("Native archive entry has an invalid compression ratio: " + relative)
                    }
                    if (entry.size > 0L && compressed > 0L && entry.size > compressed * maxCompressionRatio.toLong()) {
                        throw IOException("Native archive entry exceeds the compression-ratio limit: " + relative)
                    }
                    val outputParent = output.parentFile ?: throw IOException("Extracted file has no parent directory")
                    if (!outputParent.exists() && !outputParent.mkdirs()) {
                        throw IOException("Could not create extracted file directory: " + relative)
                    }
                    if (output.exists()) throw IOException("Native archive file would overwrite another entry: " + relative)

                    zip.getInputStream(entry).use { input ->
                        output.outputStream().buffered().use { stream ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var entryBytes = 0L
                            while (true) {
                                checkCancelled(isCancelled)
                                val count = input.read(buffer)
                                if (count < 0) break
                                entryBytes += count.toLong()
                                if (entryBytes > maxEntryBytes) {
                                    throw IOException("Expanded native entry exceeds the per-file limit: " + relative)
                                }
                                if (Long.MAX_VALUE - bytesWritten < count.toLong() ||
                                    bytesWritten + count.toLong() > maxExpandedBytes) {
                                    throw IOException("Native archive exceeds the total expanded-size limit")
                                }
                                bytesWritten += count.toLong()
                                stream.write(buffer, 0, count)
                            }
                            if (entry.size >= 0L && entryBytes != entry.size) {
                                throw IOException("Expanded native entry size does not match archive metadata: " + relative)
                            }
                        }
                    }
                    filesWritten += 1
                }
            }

            checkCancelled(isCancelled)
            if (target.exists()) throw IOException("Native extraction destination appeared during extraction")
            try {
                Files.move(content.toPath(), target.toPath())
            }
            return NativeExtractionResult(target, filesWritten, bytesWritten, skipped)
        } finally {
            stage.deleteRecursively()
        }
    }

    private fun checkCancelled(isCancelled: () -> Boolean) {
        if (isCancelled()) throw DownloadCancelledException()
    }

    private fun normalizeExclude(value: String): String? {
        if (value.isBlank()) return null
        val withoutTrailingSlash = value.replace('\\', '/').trimEnd('/')
        if (withoutTrailingSlash.isBlank()) return null
        return try {
            normalizeArchivePath(withoutTrailingSlash)
        } catch (error: IOException) {
            throw IllegalArgumentException("Invalid native extraction exclusion: " + value, error)
        }
    }

    private fun isExcluded(path: String, excludes: Set<String>): Boolean =
        excludes.any { path == it || path.startsWith(it.trimEnd('/') + "/") }

    private fun normalizeArchivePath(value: String): String {
        if (value.isBlank() || value.startsWith('/') || value.startsWith('\\') ||
            DRIVE_PREFIX.containsMatchIn(value) || value.any { it == '\u0000' || it.code < 0x20 }) {
            throw IOException("Native archive contains an invalid path")
        }
        val path = value.replace('\\', '/').trimEnd('/')
        if (path.isBlank()) throw IOException("Native archive contains an empty path")
        val segments = path.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." || it.contains(':') }) {
            throw IOException("Native archive path is unsafe: " + value)
        }
        return segments.joinToString("/")
    }

    private fun resolveInside(root: File, relativePath: String): File {
        val checked = normalizeArchivePath(relativePath)
        val canonicalRoot = root.canonicalFile
        val target = File(canonicalRoot, checked).canonicalFile
        val rootPrefix = canonicalRoot.path.trimEnd(File.separatorChar) + File.separator
        if (!target.path.startsWith(rootPrefix)) throw IOException("Native archive path escapes extraction root")
        return target
    }

    companion object {
        private const val BUFFER_SIZE = 32 * 1024
        private const val DEFAULT_MAX_ENTRIES = 20_000
        private const val MAX_ALLOWED_ENTRIES = 100_000
        private const val DEFAULT_MAX_ENTRY_BYTES = 128L * 1024 * 1024
        private const val DEFAULT_MAX_EXPANDED_BYTES = 512L * 1024 * 1024
        private const val MAX_ALLOWED_EXPANDED_BYTES = 2L * 1024 * 1024 * 1024
        private const val DEFAULT_MAX_COMPRESSION_RATIO = 200
        private const val MAX_ALLOWED_COMPRESSION_RATIO = 2_000
        private val DRIVE_PREFIX = Regex("^[A-Za-z]:")
    }
}
