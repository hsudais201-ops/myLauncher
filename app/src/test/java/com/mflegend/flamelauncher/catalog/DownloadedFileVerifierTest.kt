package com.mflegend.flamelauncher.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException

class DownloadedFileVerifierTest {
    private val bytes = "Flame Launcher verified download".toByteArray()
    private val sha1 = MessageDigest.getInstance("SHA-1")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    @Test fun acceptsMatchingStoredBytes() {
        assertTrue(
            DownloadedFileVerifier.matches(
                ByteArrayInputStream(bytes), bytes.size.toLong(), sha1
            )
        )
    }

    @Test fun rejectsStoredBytesWithWrongSizeOrChecksum() {
        assertFalse(
            DownloadedFileVerifier.matches(
                ByteArrayInputStream(bytes), bytes.size.toLong() + 1, sha1
            )
        )
        assertFalse(
            DownloadedFileVerifier.matches(
                ByteArrayInputStream(bytes), bytes.size.toLong(), "0".repeat(40)
            )
        )
    }

    @Test fun checksCancellationWhileReadingStoredBytes() {
        val control = DownloadControl().apply { cancel() }
        val error = runCatching {
            DownloadedFileVerifier.matches(
                ByteArrayInputStream(bytes), bytes.size.toLong(), sha1, control
            )
        }.exceptionOrNull()
        assertTrue(error is CancellationException)
    }
}
