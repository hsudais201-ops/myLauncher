package com.voidcraft.launcher.core

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest

data class NativeArchiveSource(
    val file: File,
    val excludes: List<String> = emptyList()
)

data class NativeLibraryAssemblyResult(
    val directory: File,
    val archivesProcessed: Int,
    val filesWritten: Int,
    val identicalDuplicatesReused: Int,
    val bytesWritten: Long
)

/**
 * Builds one native search directory from all selected native-library archives.
 * Identical duplicate paths are reused only when their SHA-256 digests match;
 * conflicting contents abort the operation rather than silently winning.
 */
class MinecraftNativeLibraryAssembler(
    private val extractor: MinecraftNativeArchiveExtractor = MinecraftNativeArchiveExtractor(),
    private val maxArchiveCount: Int = DEFAULT_MAX_ARCHIVES,
    private val maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES
) {
    init {
        require(maxArchiveCount in 1..MAX_ALLOWED_ARCHIVES) { "Native archive-count limit is outside the supported range" }
        require(maxTotalBytes in 1L..MAX_ALLOWED_TOTAL_BYTES) { "Native assembly size limit is outside the supported range" }
    }

    @Synchronized
    @Throws(IOException::class)
    fun assemble(
        archives: List<NativeArchiveSource>,
        destination: File,
        isCancelled: () -> Boolean = { false }
    ): NativeLibraryAssemblyResult {
        require(archives.isNotEmpty()) { "At least one native archive is required" }
        if (archives.size > maxArchiveCount) throw IOException("Too many native archives")
        archives.forEach { if (!it.file.isFile) throw IOException("Native archive is not a file: " + it.file.name) }

        val target = destination.absoluteFile.canonicalFile
        if (target.exists()) throw IOException("Native assembly destination already exists; refusing to overwrite it")
        val parent = target.parentFile ?: throw IOException("Native assembly destination has no parent directory")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create native assembly parent directory")
        if (!parent.isDirectory) throw IOException("Native assembly parent is not a directory")
        val canonicalParent = parent.canonicalFile
        val parentPrefix = canonicalParent.path.trimEnd(File.separatorChar) + File.separator
        if (!target.path.startsWith(parentPrefix)) throw IOException("Native assembly destination escapes its parent")

        val stage = try {
            Files.createTempDirectory(canonicalParent.toPath(), ".voidcraft-native-assembly-").toFile()
        } catch (error: Exception) {
            throw IOException("Could not create native assembly staging directory", error)
        }
        val merged = File(stage, "merged")
        if (!merged.mkdirs()) {
            stage.deleteRecursively()
            throw IOException("Could not create native assembly output directory")
        }

        var filesWritten = 0
        var duplicatesReused = 0
        var bytesWritten = 0L
        try {
            archives.forEachIndexed { archiveIndex, source ->
                checkCancelled(isCancelled)
                val extracted = File(stage, "archive-" + archiveIndex)
                val report = extractor.extract(source.file, extracted, source.excludes, isCancelled)
                report.directory.walkTopDown()
                    .filter { it.isFile }
                    .forEach { extractedFile ->
                        checkCancelled(isCancelled)
                        val relative = extractedFile.relativeTo(report.directory).invariantSeparatorsPath
                        val output = resolveInside(merged, relative)
                        if (output.exists()) {
                            if (!output.isFile || !sha256(extractedFile).equals(sha256(output), ignoreCase = true)) {
                                throw IOException("Native archives contain conflicting files at " + relative)
                            }
                            duplicatesReused += 1
                        } else {
                            val outputParent = output.parentFile
                                ?: throw IOException("Native output file has no parent directory")
                            if (!outputParent.exists() && !outputParent.mkdirs()) {
                                throw IOException("Could not create native output directory for " + relative)
                            }
                            val size = extractedFile.length()
                            if (size < 0L || Long.MAX_VALUE - bytesWritten < size ||
                                bytesWritten + size > maxTotalBytes) {
                                throw IOException("Combined native files exceed the configured size limit")
                            }
                            Files.copy(extractedFile.toPath(), output.toPath())
                            bytesWritten += size
                            filesWritten += 1
                        }
                    }
                report.directory.deleteRecursively()
            }

            checkCancelled(isCancelled)
            if (filesWritten == 0) throw IOException("Native archives did not contain any extractable files")
            if (target.exists()) throw IOException("Native assembly destination appeared during assembly")
            Files.move(merged.toPath(), target.toPath())
            return NativeLibraryAssemblyResult(
                directory = target,
                archivesProcessed = archives.size,
                filesWritten = filesWritten,
                identicalDuplicatesReused = duplicatesReused,
                bytesWritten = bytesWritten
            )
        } finally {
            stage.deleteRecursively()
        }
    }

    private fun checkCancelled(isCancelled: () -> Boolean) {
        if (isCancelled()) throw DownloadCancelledException()
    }

    private fun resolveInside(root: File, relativePath: String): File {
        require(relativePath.isNotBlank() && !relativePath.startsWith('/') &&
            !relativePath.contains('\\') && !relativePath.contains(':')) {
            "Native archive produced an invalid relative path"
        }
        require(relativePath.split('/').all { it.isNotBlank() && it != "." && it != ".." }) {
            "Native archive produced an unsafe relative path"
        }
        val canonicalRoot = root.canonicalFile
        val output = File(canonicalRoot, relativePath).canonicalFile
        val rootPrefix = canonicalRoot.path.trimEnd(File.separatorChar) + File.separator
        if (!output.path.startsWith(rootPrefix)) throw IOException("Native assembly path escapes output directory")
        return output
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    companion object {
        private const val BUFFER_SIZE = 32 * 1024
        private const val DEFAULT_MAX_ARCHIVES = 128
        private const val MAX_ALLOWED_ARCHIVES = 1024
        private const val DEFAULT_MAX_TOTAL_BYTES = 1024L * 1024 * 1024
        private const val MAX_ALLOWED_TOTAL_BYTES = 4L * 1024 * 1024 * 1024
    }
}
