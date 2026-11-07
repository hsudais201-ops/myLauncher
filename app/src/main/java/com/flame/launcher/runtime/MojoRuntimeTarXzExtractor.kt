package com.flame.launcher.runtime

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CancellationException

/**
 * Safely expands authenticated Mojo .tar.xz components into one private staging
 * directory. Call universal first, then the ABI-specific component so platform
 * files can intentionally override universal files.
 *
 * This class does not publish a runtime directory; validation and transactional
 * promotion are performed by the next installation stage.
 */
class MojoRuntimeTarXzExtractor(
    private val maxArchiveBytes: Long = DEFAULT_MAX_ARCHIVE_BYTES,
    private val maxExpandedBytes: Long = DEFAULT_MAX_EXPANDED_BYTES,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val xzMemoryLimitKiB: Int = DEFAULT_XZ_MEMORY_LIMIT_KIB
) {
    init {
        require(maxArchiveBytes in 1..ABSOLUTE_MAX_BYTES)
        require(maxExpandedBytes in 1..ABSOLUTE_MAX_BYTES)
        require(maxEntries in 1..ABSOLUTE_MAX_ENTRIES)
        require(xzMemoryLimitKiB in 1..MAX_XZ_MEMORY_LIMIT_KIB)
    }

    fun extract(
        archive: File,
        destinationDirectory: File,
        componentName: String,
        onProgress: (component: String, expandedBytes: Long, totalEntryBytes: Long?) -> Unit =
            { _, _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): MojoTarExtractionResult {
        require(componentName == "universal.tar.xz" ||
            COMPONENT_PATTERN.matches(componentName)
        ) { "Runtime component name is invalid." }
        val source = archive.canonicalFile
        require(source.isFile && source.length() in 1..maxArchiveBytes) {
            "Runtime archive is missing or exceeds the compressed size limit."
        }
        val root = destinationDirectory.canonicalFile
        require(root.isDirectory) { "Runtime extraction destination must already exist." }
        val rootPath = root.toPath().toAbsolutePath().normalize()
        val seen = HashMap<String, String>()
        var entries = 0
        var expanded = 0L
        var componentExpanded = 0L

        FileInputStream(source).use { fileInput ->
            XZInputStream(fileInput, xzMemoryLimitKiB).use { xzInput ->
                TarArchiveInputStream(xzInput).use { tar ->
                    while (true) {
                        checkCancelled(isCancelled)
                        val entry = tar.nextTarEntry ?: break
                        entries += 1
                        require(entries <= maxEntries) {
                            "Runtime archive contains too many entries."
                        }
                        val relative = validatePath(entry.name)
                        val key = relative.toString()
                        val type = entryType(entry)
                        val previousType = seen[key]
                        require(previousType == null || canOverlay(previousType, type)) {
                            "Runtime archive contains an incompatible duplicate path."
                        }

                        val output = rootPath.resolve(relative).normalize()
                        require(output.startsWith(rootPath) && output != rootPath) {
                            "Runtime archive entry escapes its extraction directory."
                        }
                        val target = output.toFile()
                        ensureSafeParents(rootPath, output.parent)
                        val canonicalTarget = target.canonicalFile.toPath()
                        require(canonicalTarget.startsWith(rootPath)) {
                            "Runtime archive entry resolves outside its extraction directory."
                        }

                        when (type) {
                            TYPE_DIRECTORY -> {
                                if (Files.exists(output) && !Files.isDirectory(output)) {
                                    require(Files.isSymbolicLink(output).not()) {
                                        "Runtime archive directory conflicts with a symbolic link."
                                    }
                                    Files.delete(output)
                                }
                                require(Files.exists(output) || target.mkdirs()) {
                                    "Unable to create runtime directory."
                                }
                            }
                            TYPE_FILE -> {
                                if (Files.exists(output) || Files.isSymbolicLink(output)) {
                                    require(!Files.isDirectory(output)) {
                                        "Runtime archive file conflicts with a directory."
                                    }
                                    Files.delete(output)
                                }
                                val declared = entry.size
                                require(declared in 0..maxExpandedBytes) {
                                    "Runtime archive entry has an invalid or excessive size."
                                }
                                FileOutputStream(target).use { outputStream ->
                                    val buffer = ByteArray(BUFFER_SIZE)
                                    var entryBytes = 0L
                                    while (entryBytes < declared) {
                                        checkCancelled(isCancelled)
                                        val wanted = minOf(buffer.size.toLong(), declared - entryBytes).toInt()
                                        val count = tar.read(buffer, 0, wanted)
                                        require(count > 0) {
                                            "Runtime archive file ended before its declared size."
                                        }
                                        val amount = count.toLong()
                                        require(expanded <= maxExpandedBytes - amount) {
                                            "Runtime archive exceeds the expanded size limit."
                                        }
                                        outputStream.write(buffer, 0, count)
                                        entryBytes += amount
                                        expanded += amount
                                        componentExpanded += amount
                                        onProgress(componentName, componentExpanded, declared)
                                    }
                                }
                                if (entry.mode and EXECUTE_BITS != 0) {
                                    require(target.setExecutable(true, false) || target.canExecute()) {
                                        "Unable to preserve runtime executable permissions."
                                    }
                                }
                            }
                            TYPE_SYMLINK -> {
                                require(entry.linkName.isNotBlank()) {
                                    "Runtime archive contains an empty symbolic-link target."
                                }
                                require(!Files.exists(output) || Files.isSymbolicLink(output)) {
                                    "Runtime archive symbolic link conflicts with an existing file."
                                }
                                if (Files.isSymbolicLink(output)) Files.delete(output)
                                val linkTarget = validateLinkTarget(rootPath, output.parent, entry.linkName)
                                require(linkTarget.startsWith(rootPath)) {
                                    "Runtime archive symbolic link escapes its extraction directory."
                                }
                                Files.createSymbolicLink(output, Paths.get(entry.linkName))
                            }
                            else -> throw IllegalArgumentException(
                                "Runtime archive contains an unsupported special file."
                            )
                        }
                        seen[key] = type
                    }
                }
            }
        }
        require(entries > 0 && expanded > 0L) { "Runtime archive contains no usable files." }
        return MojoTarExtractionResult(entries, expanded)
    }

    private fun ensureSafeParents(root: Path, parent: Path?) {
        require(parent != null && parent.normalize().startsWith(root)) {
            "Runtime archive entry has an invalid parent path."
        }
        var cursor = root
        val relative = root.relativize(parent.normalize())
        for (segment in relative) {
            cursor = cursor.resolve(segment)
            if (Files.exists(cursor) || Files.isSymbolicLink(cursor)) {
                val canonical = cursor.toFile().canonicalFile.toPath()
                require(canonical.startsWith(root)) {
                    "Runtime archive parent resolves outside its extraction directory."
                }
                require(Files.isDirectory(cursor)) {
                    "Runtime archive parent is not a directory."
                }
            } else {
                Files.createDirectory(cursor)
            }
        }
    }

    private fun validatePath(raw: String): Path {
        require(raw.isNotBlank() && !raw.startsWith("/") &&
            '\\' !in raw && ':' !in raw &&
            raw.none { it.code < 0x20 }
        ) { "Runtime archive contains an absolute or invalid path." }
        val trimmed = raw.removeSuffix("/")
        require(trimmed.isNotBlank()) { "Runtime archive contains an empty path." }
        val segments = trimmed.split('/')
        require(segments.all { it.isNotBlank() && it != "." && it != ".." }) {
            "Runtime archive contains a traversal path."
        }
        return Paths.get(segments.first(), *segments.drop(1).toTypedArray())
    }

    private fun validateLinkTarget(root: Path, parent: Path, rawTarget: String): Path {
        require(!rawTarget.startsWith("/") && '\\' !in rawTarget &&
            ':' !in rawTarget && rawTarget.none { it.code < 0x20 }
        ) { "Runtime archive symbolic link has an invalid target." }
        val target = parent.resolve(rawTarget).normalize()
        require(target.startsWith(root) && target != root.parent) {
            "Runtime archive symbolic link escapes its extraction directory."
        }
        return target
    }

    private fun entryType(entry: TarArchiveEntry): String = when {
        entry.isDirectory -> TYPE_DIRECTORY
        entry.isSymbolicLink -> TYPE_SYMLINK
        entry.isFile -> TYPE_FILE
        else -> TYPE_SPECIAL
    }

    private fun canOverlay(previous: String, next: String): Boolean =
        previous == TYPE_FILE && next == TYPE_FILE ||
            previous == TYPE_SYMLINK && next == TYPE_FILE ||
            previous == TYPE_FILE && next == TYPE_SYMLINK ||
            previous == TYPE_DIRECTORY && next == TYPE_DIRECTORY

    private fun checkCancelled(isCancelled: () -> Boolean) {
        if (isCancelled()) throw CancellationException("Runtime archive extraction cancelled.")
    }

    companion object {
        private const val DEFAULT_MAX_ARCHIVE_BYTES = 512L * 1024 * 1024
        private const val DEFAULT_MAX_EXPANDED_BYTES = 1024L * 1024 * 1024
        private const val ABSOLUTE_MAX_BYTES = 2L * 1024 * 1024 * 1024
        private const val DEFAULT_MAX_ENTRIES = 50_000
        private const val ABSOLUTE_MAX_ENTRIES = 100_000
        private const val DEFAULT_XZ_MEMORY_LIMIT_KIB = 128 * 1024
        private const val MAX_XZ_MEMORY_LIMIT_KIB = 256 * 1024
        private const val BUFFER_SIZE = 32 * 1024
        private const val EXECUTE_BITS = 0b001001001
        private const val TYPE_DIRECTORY = "directory"
        private const val TYPE_FILE = "file"
        private const val TYPE_SYMLINK = "symlink"
        private const val TYPE_SPECIAL = "special"
        private val COMPONENT_PATTERN = Regex("bin-(arm|arm64|x86|x86_64)\\.tar\\.xz")
    }
}

data class MojoTarExtractionResult(
    val entriesExtracted: Int,
    val expandedBytes: Long
)
