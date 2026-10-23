package com.voidcraft.launcher

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

private const val MAX_MOD_ARCHIVE_BYTES = 100L * 1024L * 1024L

/**
 * Copies an imported mod to a temporary file while enforcing a strict streaming limit.
 * Any failed or empty import removes its partial file so repeated failures cannot fill storage.
 */
internal fun copyModArchiveToTemporaryFile(
    input: InputStream,
    temporaryFile: File,
    maxBytes: Long = MAX_MOD_ARCHIVE_BYTES
): Long {
    require(maxBytes > 0L) { "Mod file size limit must be positive." }
    try {
        val parent = temporaryFile.parentFile ?: error("Temporary mod path has no parent directory.")
        check(parent.isDirectory || parent.mkdirs()) { "Could not create the active mods folder." }
        var total = 0L
        FileOutputStream(temporaryFile).use { output ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count
                require(total <= maxBytes) { "Mod files must be 100 MB or smaller." }
                output.write(buffer, 0, count)
            }
            output.fd.sync()
        }
        require(total > 0L) { "The selected file is empty." }
        return total
    } catch (failure: Throwable) {
        temporaryFile.delete()
        throw failure
    }
}
