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

class MinecraftAssetDownloaderTest {
    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun plan(bytes: ByteArray, size: Long = bytes.size.toLong()): MinecraftPlannedDownload {
        val hash = sha1(bytes)
        val path = "assets/objects/${hash.take(2)}/$hash"
        return MinecraftPlannedDownload(
            kind = MinecraftPlannedDownloadKind.ASSET,
            relativePath = path,
            descriptor = MinecraftDownloadDescriptor(
                "https://resources.download.minecraft.net/${hash.take(2)}/$hash",
                hash,
                size,
                path
            )
        )
    }

    private class FakeConnection(
        private val body: ByteArray,
        private val status: Int = HTTP_OK,
        private val declaredLength: Long = body.size.toLong()
    ) : HttpURLConnection(URL("https://resources.download.minecraft.net/test")) {
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

    private fun downloader(connection: FakeConnection, maxBytes: Long = 1024) =
        MinecraftAssetDownloader(
            connectionFactory = { connection },
            maxAssetBytes = maxBytes
        )

    @Test
    fun downloadsAndVerifiesAssetBeforePromotingIt() {
        val directory = Files.createTempDirectory("flame-mc-asset").toFile()
        try {
            val bytes = byteArrayOf(1, 2, 3, 4, 5)
            val connection = FakeConnection(bytes)
            val updates = mutableListOf<Pair<Long, Long?>>()
            val result = downloader(connection).download(plan(bytes), directory) { received, total ->
                updates += received to total
            }
            assertArrayEquals(bytes, result.readBytes())
            assertEquals(bytes.size.toLong() to bytes.size.toLong(), updates.last())
            assertFalse(connection.redirectsEnabled)
            assertTrue(connection.disconnected)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun acceptsZeroByteAssetWhenHashAndSizeMatch() {
        val directory = Files.createTempDirectory("flame-mc-empty-asset").toFile()
        try {
            val bytes = byteArrayOf()
            val connection = FakeConnection(bytes)
            val result = downloader(connection).download(plan(bytes), directory)
            assertTrue(result.exists())
            assertEquals(0L, result.length())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsHashMismatchAndPreservesExistingAsset() {
        val directory = Files.createTempDirectory("flame-mc-bad-hash").toFile()
        try {
            val expected = byteArrayOf(1, 2, 3)
            val path = plan(expected)
            val target = File(directory, path.relativePath)
            target.parentFile!!.mkdirs()
            val previous = byteArrayOf(8, 8, 8)
            target.writeBytes(previous)
            val connection = FakeConnection(byteArrayOf(4, 5, 6))
            assertThrows(IllegalArgumentException::class.java) {
                downloader(connection).download(path, directory)
            }
            assertArrayEquals(previous, target.readBytes())
            assertEquals(listOf(target), directory.walkTopDown().filter { it.isFile }.toList())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsSizeMismatchAndRedirect() {
        val directory = Files.createTempDirectory("flame-mc-invalid-asset").toFile()
        try {
            val bytes = byteArrayOf(1, 2, 3)
            assertThrows(IllegalArgumentException::class.java) {
                downloader(FakeConnection(bytes, declaredLength = 4)).download(plan(bytes), directory)
            }
            assertThrows(IllegalArgumentException::class.java) {
                downloader(FakeConnection(bytes, status = HttpURLConnection.HTTP_MOVED_TEMP))
                    .download(plan(bytes), directory)
            }
            assertTrue(directory.walkTopDown().none { it.isFile })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsAlteredDescriptorAndNonAssetPlan() {
        val directory = Files.createTempDirectory("flame-mc-forged-asset").toFile()
        try {
            val bytes = byteArrayOf(1, 2, 3)
            val valid = plan(bytes)
            val altered = valid.copy(descriptor = valid.descriptor.copy(url = "https://attacker.example/file"))
            assertThrows(IllegalArgumentException::class.java) {
                downloader(FakeConnection(bytes)).download(altered, directory)
            }
            assertThrows(IllegalArgumentException::class.java) {
                downloader(FakeConnection(bytes)).download(
                    valid.copy(kind = MinecraftPlannedDownloadKind.CLIENT), directory
                )
            }
            assertTrue(directory.walkTopDown().none { it.isFile })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsSymlinkedAssetDirectoryBeforeOpeningConnection() {
        val parent = Files.createTempDirectory("flame-mc-symlink-parent").toFile()
        val directory = File(parent, "game").also { it.mkdirs() }
        val outside = File(parent, "outside").also { it.mkdirs() }
        try {
            Files.createSymbolicLink(File(directory, "assets").toPath(), outside.toPath())
            var opened = false
            val bytes = byteArrayOf(1, 2, 3)
            val downloader = MinecraftAssetDownloader(
                connectionFactory = { opened = true; FakeConnection(bytes) }
            )
            assertThrows(IllegalArgumentException::class.java) {
                downloader.download(plan(bytes), directory)
            }
            assertFalse(opened)
            assertTrue(outside.walkTopDown().none { it.isFile })
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun rejectsOversizedAssetBeforeOpeningConnection() {
        val directory = Files.createTempDirectory("flame-mc-oversized-asset").toFile()
        try {
            var opened = false
            val downloader = MinecraftAssetDownloader(
                connectionFactory = { opened = true; FakeConnection(byteArrayOf(1)) },
                maxAssetBytes = 2
            )
            val bytes = byteArrayOf(1, 2, 3)
            assertThrows(IllegalArgumentException::class.java) {
                downloader.download(plan(bytes), directory)
            }
            assertFalse(opened)
        } finally {
            directory.deleteRecursively()
        }
    }
}
