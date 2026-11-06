package com.flame.launcher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class MinecraftMetadataDownloaderTest {
    private class FakeConnection(
        url: URL,
        private val body: ByteArray,
        private val status: Int = HTTP_OK,
        private val declaredLength: Long = body.size.toLong()
    ) : HttpURLConnection(url) {
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getContentLengthLong(): Long = declaredLength
        override fun getInputStream(): InputStream = ByteArrayInputStream(body)
    }

    private fun sha1(value: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(value).joinToString("") { "%02x".format(it) }

    @Test
    fun downloadsManifestOverHttpsAndDisconnects() {
        val bytes = """{"latest":{}}""".toByteArray()
        lateinit var connection: FakeConnection
        val downloader = MinecraftMetadataDownloader(
            connectionFactory = { url -> FakeConnection(url, bytes).also { connection = it } }
        )
        assertEquals(String(bytes), downloader.downloadManifest())
        assertTrue(connection.disconnected)
        assertEquals(15_000, connection.connectTimeout)
        assertEquals(15_000, connection.readTimeout)
        assertFalse(connection.instanceFollowRedirects)
        assertEquals("GET", connection.requestMethod)
    }

    @Test
    fun verifiesVersionJsonAgainstManifestSha1() {
        val bytes = """{"id":"1.21"}""".toByteArray()
        val entry = MinecraftVersionEntry(
            id = "1.21",
            type = "release",
            metadataUrl = "https://piston-meta.mojang.com/v1/packages/test/1.21.json",
            sha1 = sha1(bytes),
            releaseTime = "2024-06-13T00:00:00+00:00",
            complianceLevel = 1
        )
        val downloader = MinecraftMetadataDownloader(
            connectionFactory = { url -> FakeConnection(url, bytes) }
        )
        assertEquals(String(bytes), downloader.downloadVersionDetails(entry))
    }

    @Test
    fun rejectsVersionJsonWithWrongSha1AndStillDisconnects() {
        val bytes = """{"id":"1.21"}""".toByteArray()
        lateinit var connection: FakeConnection
        val entry = MinecraftVersionEntry(
            "1.21", "release",
            "https://piston-meta.mojang.com/v1/packages/test/1.21.json",
            "0".repeat(40), "2024-06-13T00:00:00+00:00", 1
        )
        val downloader = MinecraftMetadataDownloader(
            connectionFactory = { url -> FakeConnection(url, bytes).also { connection = it } }
        )
        assertThrows(IllegalArgumentException::class.java) {
            downloader.downloadVersionDetails(entry)
        }
        assertTrue(connection.disconnected)
    }

    @Test
    fun rejectsOversizedBodyEvenWithoutContentLength() {
        val downloader = MinecraftMetadataDownloader(
            connectionFactory = { url -> FakeConnection(url, ByteArray(20), declaredLength = -1) },
            manifestMaxBytes = 10
        )
        assertThrows(IllegalArgumentException::class.java) { downloader.downloadManifest() }
    }

    @Test
    fun rejectsRedirectAndUntrustedMetadataHost() {
        val redirect = MinecraftMetadataDownloader(
            connectionFactory = { url -> FakeConnection(url, byteArrayOf(1), status = 302) }
        )
        assertThrows(IllegalArgumentException::class.java) { redirect.downloadManifest() }

        var opened = false
        val untrusted = MinecraftMetadataDownloader(
            connectionFactory = { url -> opened = true; FakeConnection(url, byteArrayOf(1)) }
        )
        val entry = MinecraftVersionEntry(
            "1.21", "release", "https://attacker.example/version.json",
            "0".repeat(40), "2024-06-13T00:00:00+00:00", 1
        )
        assertThrows(IllegalArgumentException::class.java) { untrusted.downloadVersionDetails(entry) }
        assertFalse(opened)
    }

    @Test
    fun rejectsInvalidTimeoutAndSizeConfiguration() {
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftMetadataDownloader(connectTimeoutMs = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftMetadataDownloader(manifestMaxBytes = 0)
        }
    }
}
