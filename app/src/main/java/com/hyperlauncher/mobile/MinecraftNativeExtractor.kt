package com.hyperlauncher.mobile

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/** Safely extracts verified native ZIPs into a version-private directory. */
object MinecraftNativeExtractor {
    private const val MAX_ENTRY_BYTES = 256L * 1024L * 1024L
    const val MAX_TOTAL_EXTRACTED_BYTES = 512L * 1024L * 1024L

    fun extract(archives: List<File>, destination: File, shouldCancel: (() -> Boolean)? = null): List<File> {
        val root = destination.canonicalFile
        if (!root.exists() && !root.mkdirs()) error("Could not create native directory")
        var total = 0L
        val extracted = ArrayList<File>()
        for (archive in archives) {
            ZipInputStream(FileInputStream(archive)).use { zip ->
                while (true) {
                    if (shouldCancel?.invoke() == true) throw java.util.concurrent.CancellationException("Native extraction cancelled")
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (name.isBlank() || name.startsWith("/") || name.startsWith("\\") ||
                        name.contains('\\') || name.split('/').any { it == ".." || it == "." }) {
                        throw IllegalArgumentException("Native archive contains an unsafe path")
                    }
                    if (entry.isDirectory || name.startsWith("META-INF/")) {
                        zip.closeEntry()
                        continue
                    }
                    val target = File(root, name).canonicalFile
                    require(target.toPath().startsWith(root.toPath())) { "Native archive path escapes destination" }
                    val parent = target.parentFile ?: error("Native archive entry has no parent")
                    if (!parent.exists() && !parent.mkdirs()) error("Could not create native directory")
                    var entryBytes = 0L
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            if (shouldCancel?.invoke() == true) throw java.util.concurrent.CancellationException("Native extraction cancelled")
                            val count = zip.read(buffer)
                            if (count < 0) break
                            entryBytes = Math.addExact(entryBytes, count.toLong())
                            total = Math.addExact(total, count.toLong())
                            require(entryBytes <= MAX_ENTRY_BYTES && total <= MAX_TOTAL_EXTRACTED_BYTES) {
                                "Native archive exceeds extraction limits"
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                    extracted += target
                    zip.closeEntry()
                }
            }
        }
        return extracted
    }
}
