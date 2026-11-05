package com.flame.launcher.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest

class MinecraftContentStagedDownloaderTest {
    private val payload = "verified minecraft content".toByteArray()

    private fun install(
        sha1: String? = digest("SHA-1"),
        sha512: String? = digest("SHA-512"),
        expectedSize: Long? = payload.size.toLong(),
        url: String = "https://cdn.modrinth.com/data/test/file"
    ) = MinecraftPlannedInstall(
        provider = ContentProviderId.MODRINTH,
        projectId = "test-project",
        versionId = "test-version",
        contentType = MinecraftContentType.MOD,
        action = MinecraftInstallAction.INSTALL_FILE,
        fileName = "test.jar",
        downloadUrl = url,
        destinationPath = "/instance/mods/test.jar",
        expectedSizeBytes = expectedSize,
        sha1 = sha1,
        sha512 = sha512
    )

    private fun digest(algorithm: String): String =
        MessageDigest.getInstance(algorithm).digest(payload).joinToString("") { "%02x".format(it) }

    private class FakeConnection(
        url: URL,
        private val bytes: ByteArray,
        private val status: Int = 200
    ) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getContentLengthLong(): Long = bytes.size.toLong()
        override fun getInputStream() = ByteArrayInputStream(bytes)
    }

    private fun temporaryDirectory(): File = Files.createTempDirectory("flame-download-test").toFile()

    @Test
    fun verifiedDownloadIsMovedIntoStagingAndReportsProgress() {
        val directory = temporaryDirectory()
        try {
            var lastProgress = 0L
            val downloader = MinecraftContentStagedDownloader {
                FakeConnection(it, payload)
            }
            val result = downloader.download(install(), directory) { downloaded, _ ->
                lastProgress = downloaded
            }

            assertTrue("Expected download success, got $result", result is MinecraftStagedDownloadResult.Downloaded)
            val downloaded = result as MinecraftStagedDownloadResult.Downloaded
            assertEquals(payload.size.toLong(), downloaded.bytesWritten)
            assertEquals(payload.size.toLong(), lastProgress)
            assertTrue(downloaded.stagedFile.isFile)
            assertTrue(downloaded.stagedFile.readBytes().contentEquals(payload))
            assertEquals(1, directory.listFiles()?.size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun checksumMismatchDeletesPartialDownload() {
        val directory = temporaryDirectory()
        try {
            val result = MinecraftContentStagedDownloader {
                FakeConnection(it, payload)
            }.download(install(sha1 = "0".repeat(40), sha512 = null), directory)

            assertTrue(result is MinecraftStagedDownloadResult.Failed)
            assertEquals(0, directory.listFiles()?.size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun redirectsAreRejectedAndNoFileIsKept() {
        val directory = temporaryDirectory()
        try {
            val result = MinecraftContentStagedDownloader {
                FakeConnection(it, payload, status = 302)
            }.download(install(), directory)

            assertTrue(result is MinecraftStagedDownloadResult.Failed)
            assertTrue((result as MinecraftStagedDownloadResult.Failed).reason.contains("redirect"))
            assertEquals(0, directory.listFiles()?.size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun incorrectExpectedSizeIsRejected() {
        val directory = temporaryDirectory()
        try {
            val result = MinecraftContentStagedDownloader {
                FakeConnection(it, payload)
            }.download(install(expectedSize = payload.size.toLong() + 1), directory)

            assertTrue(result is MinecraftStagedDownloadResult.Failed)
            assertEquals(0, directory.listFiles()?.size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun untrustedHostIsRejectedBeforeOpeningConnection() {
        val directory = temporaryDirectory()
        try {
            var opened = false
            val result = MinecraftContentStagedDownloader {
                opened = true
                FakeConnection(it, payload)
            }.download(install(url = "https://example.invalid/file"), directory)

            assertFalse(opened)
            assertTrue(result is MinecraftStagedDownloadResult.Failed)
            assertEquals(0, directory.listFiles()?.size)
        } finally {
            directory.deleteRecursively()
        }
    }
}
