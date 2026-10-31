package com.hyperlauncher.mobile

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.CancellationException
import java.util.zip.ZipInputStream

/** Safely extracts verified native ZIPs into a version-private directory. */
object MinecraftNativeExtractor {
    private const val MAX_ENTRY_BYTES = 256L * 1024L * 1024L
    const val MAX_TOTAL_EXTRACTED_BYTES = 512L * 1024L * 1024L

    fun extract(
        archives: List<File>,
        destination: File,
        shouldCancel: (() -> Boolean)? = null
    ): List<File> {
        val root = destination.canonicalFile
        var total = 0L
        val extracted = ArrayList<File>()
        // Only remove directories created by this invocation; never touch existing user data.
        val createdDirectories = LinkedHashSet<File>()

        fun createDirectoryTree(directory: File) {
            require(directory == root || directory.toPath().startsWith(root.toPath())) {
                "Native archive directory escapes destination"
            }
            if (directory.exists()) {
                require(directory.isDirectory) { "Native archive parent is not a directory" }
                return
            }
            val missing = ArrayList<File>()
            var cursor: File? = directory
            while (cursor != null && !cursor.exists()) {
                missing += cursor
                cursor = cursor.parentFile
            }
            require(cursor != null && cursor.isDirectory) {
                "Native archive parent is not a directory"
            }
            if (!directory.mkdirs() && !directory.isDirectory) {
                error("Could not create native directory")
            }
            createdDirectories.addAll(missing.asReversed())
        }

        try {
            createDirectoryTree(root)
            for (archive in archives) {
                ZipInputStream(FileInputStream(archive)).use { zip ->
                    while (true) {
                        if (shouldCancel?.invoke() == true) {
                            throw CancellationException("Native extraction cancelled")
                        }
                        val entry = zip.nextEntry ?: break
                        val name = entry.name
                        val pathName = if (entry.isDirectory) name.removeSuffix("/") else name
                        val segments = pathName.split('/')
                        if (pathName.isBlank() || pathName.length > 1024 ||
                            pathName.startsWith("/") ||
                            pathName.startsWith(92.toChar().toString()) ||
                            pathName.indexOf(92.toChar()) >= 0 ||
                            pathName.any { it.code < 0x20 || it.code == 0x7f } ||
                            segments.any { it.isBlank() || it == ".." || it == "." }
                        ) {
                            throw IllegalArgumentException("Native archive contains an unsafe path")
                        }
                        if (entry.isDirectory) {
                            val directory = File(root, pathName).canonicalFile
                            require(directory.toPath().startsWith(root.toPath())) {
                                "Native archive path escapes destination"
                            }
                            createDirectoryTree(directory)
                            zip.closeEntry()
                            continue
                        }

                        val target = File(root, pathName).canonicalFile
                        require(target.toPath().startsWith(root.toPath())) {
                            "Native archive path escapes destination"
                        }
                        // Do not allow a later archive to overwrite an earlier output.
                        require(!target.exists()) {
                            "Native archives contain a duplicate output path"
                        }
                        val parent = target.parentFile ?: error("Native archive entry has no parent")
                        createDirectoryTree(parent)
                        // Track before writing so cancellation or an I/O error removes partial files.
                        extracted += target
                        var entryBytes = 0L
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                if (shouldCancel?.invoke() == true) {
                                    throw CancellationException("Native extraction cancelled")
                                }
                                val count = zip.read(buffer)
                                if (count < 0) break
                                entryBytes = Math.addExact(entryBytes, count.toLong())
                                total = Math.addExact(total, count.toLong())
                                require(entryBytes <= MAX_ENTRY_BYTES &&
                                    total <= MAX_TOTAL_EXTRACTED_BYTES
                                ) {
                                    "Native archive exceeds extraction limits"
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
            return extracted
        } catch (error: Exception) {
            // Extraction is all-or-nothing for files and directories created here.
            extracted.asReversed().forEach { it.delete() }
            createdDirectories.toList().asReversed().forEach { directory ->
                if (directory.isDirectory && directory.list()?.isEmpty() == true) {
                    directory.delete()
                }
            }
            throw error
        }
    }
}
