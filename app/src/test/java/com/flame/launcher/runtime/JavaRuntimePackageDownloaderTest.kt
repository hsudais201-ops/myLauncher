package com.flame.launcher.runtime

import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JavaRuntimePackageDownloaderTest {
    @Test
    fun downloadsAndVerifiesArchiveBeforePromotingIt() {
        val directory = Files.createTempDirectory("flame-runtime-download").toFile()
        try {
            val payload = byteArrayOf(1, 2, 3, 4, 5)
            val connection = FakeConnection(payload)
            val target = File(directory, "java.zip")
            val result = downloader(connection).download(URL_STRING, sha256(payload), target)
            assertEquals(target, result)
            assertArrayEquals(payload, target.readBytes())
            assertTrue(connection.disconnected)
            assertFalse(directory.listFiles().orEmpty().any { it.name.startsWith(".java.zip.download-") })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun reportsIncrementalDownloadProgressAndKnownTotal() {
        val directory = Files.createTempDirectory("flame-runtime-progress").toFile()
        try {
            val payload = ByteArray(40_000) { it.toByte() }
            val connection = FakeConnection(payload)
            val updates = mutableListOf<Pair<Long, Long?>>()
            downloader(connection, maxArchiveBytes = 64 * 1024).download(URL_STRING, sha256(payload), File(directory, "java.zip")) { received, total ->
                updates += received to total
            }
            assertTrue(updates.isNotEmpty())
            assertEquals(payload.size.toLong(), updates.last().first)
            assertTrue(updates.last().second == payload.size.toLong())
            assertTrue(updates.zipWithNext().all { (a, b) -> b.first > a.first })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun throttlesProgressCallbacksForLargeArchivesAndReportsFinalByteCount() {
        val directory = Files.createTempDirectory("flame-runtime-progress-throttle").toFile()
        try {
            val payload = ByteArray(1024 * 1024) { it.toByte() }
            val connection = FakeConnection(payload)
            val updates = mutableListOf<Pair<Long, Long?>>()
            downloader(connection, maxArchiveBytes = 2 * 1024 * 1024).download(
                URL_STRING, sha256(payload), File(directory, "java.zip")
            ) { received, total -> updates += received to total }
            assertTrue(updates.size <= 5)
            assertEquals(payload.size.toLong(), updates.last().first)
            assertEquals(payload.size.toLong(), updates.last().second)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun reportsUnknownTotalWhenServerOmitsContentLength() {
        val directory = Files.createTempDirectory("flame-runtime-progress-unknown").toFile()
        try {
            val payload = byteArrayOf(4, 5, 6, 7)
            val connection = FakeConnection(payload, declaredLength = -1)
            val updates = mutableListOf<Pair<Long, Long?>>()
            val downloader = JavaRuntimePackageDownloader(
                trustedHosts = setOf("runtime.example"),
                connectionFactory = { connection },
                maxArchiveBytes = 1024
            )
            downloader.download(URL_STRING, sha256(payload), File(directory, "java.zip")) { received, total ->
                updates += received to total
            }
            assertEquals(4L, updates.last().first)
            assertEquals(null, updates.last().second)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsUntrustedHostsBeforeOpeningConnection() {
        val directory = Files.createTempDirectory("flame-runtime-untrusted").toFile()
        try {
            var opened = false
            val downloader = JavaRuntimePackageDownloader(
                trustedHosts = setOf("runtime.example"),
                connectionFactory = { opened = true; FakeConnection(byteArrayOf(1)) }
            )
            assertThrows(IllegalArgumentException::class.java) {
                downloader.download("https://attacker.example/java.zip", "0".repeat(64), File(directory, "java.zip"))
            }
            assertFalse(opened)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsRedirectResponsesWithoutFollowingThem() {
        val directory = Files.createTempDirectory("flame-runtime-redirect").toFile()
        try {
            val connection = FakeConnection(byteArrayOf(1), status = HttpURLConnection.HTTP_MOVED_TEMP)
            assertThrows(IllegalArgumentException::class.java) {
                downloader(connection).download(URL_STRING, "0".repeat(64), File(directory, "java.zip"))
            }
            assertFalse(File(directory, "java.zip").exists())
            assertFalse(connection.redirectsEnabled)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsChecksumMismatchAndRemovesPartialDownload() {
        val directory = Files.createTempDirectory("flame-runtime-bad-hash").toFile()
        try {
            val connection = FakeConnection(byteArrayOf(9, 8, 7))
            assertThrows(IllegalArgumentException::class.java) {
                downloader(connection).download(URL_STRING, "0".repeat(64), File(directory, "java.zip"))
            }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
            assertTrue(connection.disconnected)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun enforcesStreamingSizeLimitEvenWhenServerOmitsLength() {
        val directory = Files.createTempDirectory("flame-runtime-size-cap").toFile()
        try {
            val connection = FakeConnection(ByteArray(32), declaredLength = -1)
            val downloader = JavaRuntimePackageDownloader(
                trustedHosts = setOf("runtime.example"),
                connectionFactory = { connection },
                maxArchiveBytes = 16
            )
            assertThrows(IllegalArgumentException::class.java) {
                downloader.download(URL_STRING, sha256(ByteArray(32)), File(directory, "java.zip"))
            }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun replacesExistingArchiveOnlyAfterNewHashIsVerified() {
        val directory = Files.createTempDirectory("flame-runtime-replace-download").toFile()
        try {
            val target = File(directory, "java.zip")
            val previous = byteArrayOf(1, 1, 1)
            target.writeBytes(previous)
            val connection = FakeConnection(byteArrayOf(2, 2, 2))
            assertThrows(IllegalArgumentException::class.java) {
                downloader(connection).download(URL_STRING, "0".repeat(64), target)
            }
            assertArrayEquals(previous, target.readBytes())
            assertEquals(listOf("java.zip"), directory.listFiles().orEmpty().map { it.name })
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun downloader(
        connection: FakeConnection,
        maxArchiveBytes: Long = 1024
    ) = JavaRuntimePackageDownloader(
        trustedHosts = setOf("runtime.example"),
        connectionFactory = { connection },
        maxArchiveBytes = maxArchiveBytes
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private class FakeConnection(
        private val body: ByteArray,
        private val status: Int = HttpURLConnection.HTTP_OK,
        private val declaredLength: Long = body.size.toLong()
    ) : HttpURLConnection(URL(URL_STRING)) {
        var disconnected = false
        var redirectsEnabled = true

        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getContentLengthLong(): Long = declaredLength
        override fun getInputStream() = ByteArrayInputStream(body)
        override fun setInstanceFollowRedirects(follow: Boolean) {
            redirectsEnabled = follow
            super.setInstanceFollowRedirects(follow)
        }
    }

    companion object {
        private const val URL_STRING = "https://runtime.example/java.zip"
    }
}
