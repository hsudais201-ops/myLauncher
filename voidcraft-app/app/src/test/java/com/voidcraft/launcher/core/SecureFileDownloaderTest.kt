package com.voidcraft.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.ArrayDeque

class SecureFileDownloaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun downloadsVerifiesAndAtomicallyReplacesDestination() {
        val bytes = "verified game artifact".toByteArray()
        val destination = File(temporaryFolder.root, "client.jar").apply { writeText("old data") }
        val downloader = SecureFileDownloader(
            DownloadConnectionFactory { url -> FakeConnection(url, 200, bytes) }
        )

        val receipt = downloader.download(
            FileDownloadRequest(
                url = "https://piston-data.mojang.com/client.jar",
                destination = destination,
                expectedSha1 = digest("SHA-1", bytes),
                expectedSha256 = digest("SHA-256", bytes),
                expectedBytes = bytes.size.toLong(),
                maxRetries = 0
            )
        )

        assertEquals("verified game artifact", destination.readText())
        assertEquals(bytes.size.toLong(), receipt.bytesDownloaded)
        assertEquals(digest("SHA-1", bytes), receipt.sha1)
        assertEquals(digest("SHA-256", bytes), receipt.sha256)
        assertEquals(1, receipt.attempts)
    }

    @Test
    fun checksumFailurePreservesExistingDestinationAndCleansTemporaryFile() {
        val destination = File(temporaryFolder.root, "artifact.bin").apply { writeText("keep this") }
        val downloader = SecureFileDownloader(
            DownloadConnectionFactory { url -> FakeConnection(url, 200, "bad payload".toByteArray()) }
        )

        assertThrows(DownloadIntegrityException::class.java) {
            downloader.download(
                FileDownloadRequest(
                    url = "https://piston-data.mojang.com/artifact.bin",
                    destination = destination,
                    expectedSha256 = "0".repeat(64),
                    maxRetries = 0
                )
            )
        }
        assertEquals("keep this", destination.readText())
        assertNoPartFiles()
    }

    @Test
    fun transientServerFailureRetriesThenSucceeds() {
        val bytes = "second attempt".toByteArray()
        val queue = ArrayDeque<FakeConnection>()
        queue.add(FakeConnection(URL("https://piston-data.mojang.com/artifact.bin"), 503, byteArrayOf()))
        queue.add(FakeConnection(URL("https://piston-data.mojang.com/artifact.bin"), 200, bytes))
        val downloader = SecureFileDownloader(DownloadConnectionFactory { queue.removeFirst() })
        val destination = File(temporaryFolder.root, "retry.bin")

        val result = downloader.download(
            FileDownloadRequest(
                "https://piston-data.mojang.com/artifact.bin",
                destination,
                expectedSha1 = digest("SHA-1", bytes),
                maxRetries = 1
            )
        )

        assertEquals("second attempt", destination.readText())
        assertEquals(2, result.attempts)
        assertTrue(queue.isEmpty())
    }

    @Test
    fun cancellationDoesNotReplaceExistingFile() {
        val destination = File(temporaryFolder.root, "cancel.bin").apply { writeText("preserve me") }
        val bytes = ByteArray(100_000) { 7 }
        val downloader = SecureFileDownloader(
            DownloadConnectionFactory { url -> FakeConnection(url, 200, bytes) }
        )
        var cancelled = false
        var progressCalls = 0

        assertThrows(DownloadCancelledException::class.java) {
            downloader.download(
                FileDownloadRequest(
                    "https://piston-data.mojang.com/cancel.bin",
                    destination,
                    maxRetries = 0
                ),
                isCancelled = { cancelled },
                onProgress = {
                    progressCalls += 1
                    cancelled = true
                }
            )
        }
        assertTrue(progressCalls > 0)
        assertEquals("preserve me", destination.readText())
        assertNoPartFiles()
    }

    @Test
    fun unsafeHttpUrlIsRejectedBeforeOpeningConnection() {
        var opened = false
        val downloader = SecureFileDownloader(
            DownloadConnectionFactory {
                opened = true
                FakeConnection(it, 200, byteArrayOf())
            }
        )
        assertThrows(IllegalArgumentException::class.java) {
            downloader.download(
                FileDownloadRequest("http://example.invalid/file", File(temporaryFolder.root, "x"))
            )
        }
        assertFalse(opened)
    }

    @Test
    fun insecureRedirectIsRejectedAndPreviousFileSurvives() {
        val destination = File(temporaryFolder.root, "redirect.bin").apply { writeText("untouched") }
        val downloader = SecureFileDownloader(
            DownloadConnectionFactory { url ->
                FakeConnection(
                    url,
                    302,
                    byteArrayOf(),
                    headerName = "Location",
                    headerValue = "http://evil.invalid/payload"
                )
            }
        )
        assertThrows(IOException::class.java) {
            downloader.download(
                FileDownloadRequest(
                    "https://piston-data.mojang.com/redirect.bin",
                    destination,
                    maxRetries = 0
                )
            )
        }
        assertEquals("untouched", destination.readText())
    }

    @Test
    fun oversizedResponseIsRejectedWithoutReplacingDestination() {
        val destination = File(temporaryFolder.root, "large.bin").apply { writeText("small existing file") }
        val downloader = SecureFileDownloader(
            DownloadConnectionFactory { url ->
                FakeConnection(url, 200, ByteArray(128), declaredLength = 128)
            }
        )
        assertThrows(DownloadIntegrityException::class.java) {
            downloader.download(
                FileDownloadRequest(
                    "https://piston-data.mojang.com/large.bin",
                    destination,
                    maxBytes = 32L,
                    maxRetries = 0
                )
            )
        }
        assertEquals("small existing file", destination.readText())
    }

    private fun assertNoPartFiles() {
        val files = temporaryFolder.root.listFiles()?.toList().orEmpty()
        assertTrue(files.none { it.name.endsWith(".part") || it.name.startsWith(".voidcraft-") })
    }

    private fun digest(algorithm: String, input: ByteArray): String =
        MessageDigest.getInstance(algorithm).digest(input).joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }

    private class FakeConnection(
        url: URL,
        private val status: Int,
        private val bytes: ByteArray,
        private val headerName: String? = null,
        private val headerValue: String? = null,
        private val declaredLength: Long = bytes.size.toLong()
    ) : HttpURLConnection(url) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getInputStream() = ByteArrayInputStream(bytes)
        override fun getContentLengthLong(): Long = declaredLength
        override fun getHeaderField(name: String): String? =
            if (headerName.equals(name, ignoreCase = true)) headerValue else null
    }
}
