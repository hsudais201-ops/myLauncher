package com.mflegend.flamelauncher.catalog

import java.io.InputStream
import java.security.MessageDigest

/** Re-reads a SAF temporary file to verify what the provider actually stored. */
internal object DownloadedFileVerifier {
    fun matches(
        input: InputStream,
        expectedSize: Long,
        expectedSha1: String,
        control: DownloadControl? = null
    ): Boolean = input.use { stream ->
        val digest = MessageDigest.getInstance("SHA-1")
        var total = 0L
        val buffer = ByteArray(64 * 1024)
        while (true) {
            control?.checkpoint()
            val count = stream.read(buffer)
            if (count < 0) break
            total += count
            if (total > expectedSize) return@use false
            digest.update(buffer, 0, count)
        }
        total == expectedSize &&
            digest.digest().joinToString("") { "%02x".format(it) }
                .equals(expectedSha1, ignoreCase = true)
    }
}
