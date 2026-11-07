package com.flame.launcher.runtime

import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftAssetInstallCoordinatorTest {
    private fun hash(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun plan(bytes: ByteArray): MinecraftPlannedDownload {
        val digest = hash(bytes)
        val path = "assets/objects/${digest.take(2)}/$digest"
        return MinecraftPlannedDownload(
            MinecraftPlannedDownloadKind.ASSET,
            path,
            MinecraftDownloadDescriptor(
                "https://resources.download.minecraft.net/${digest.take(2)}/$digest",
                digest,
                bytes.size.toLong(),
                path
            )
        )
    }

    private class FakeConnection(private val body: ByteArray) :
        HttpURLConnection(URL("https://resources.download.minecraft.net/test")) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = HTTP_OK
        override fun getContentLengthLong(): Long = body.size.toLong()
        override fun getInputStream() = ByteArrayInputStream(body)
    }

    @Test
    fun installsAssetsSequentiallyAndReportsAggregateProgress() {
        val directory = Files.createTempDirectory("flame-assets-coordinator").toFile()
        try {
            val first = byteArrayOf(1, 2, 3)
            val second = byteArrayOf(4, 5)
            val byHash = mapOf(hash(first) to first, hash(second) to second)
            val downloader = MinecraftAssetDownloader(
                connectionFactory = { url ->
                    val payload = byHash[url.path.substringAfterLast('/')]
                        ?: error("Unexpected asset URL: $url")
                    FakeConnection(payload)
                }
            )
            val progress = mutableListOf<MinecraftAssetInstallProgress>()
            val result = MinecraftAssetInstallCoordinator(downloader).install(
                listOf(plan(first), plan(second)), directory
            ) { progress += it }

            assertEquals(2, result.size)
            assertTrue(result.all(File::exists))
            assertEquals(5L, progress.last().completedBytes)
            assertEquals(5L, progress.last().totalBytes)
            assertTrue(progress.zipWithNext().all { (a, b) -> b.completedBytes >= a.completedBytes })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsTotalSizeLimitBeforeOpeningAnyConnection() {
        val directory = Files.createTempDirectory("flame-assets-total-cap").toFile()
        try {
            var opened = false
            val downloader = MinecraftAssetDownloader(
                connectionFactory = { opened = true; FakeConnection(byteArrayOf(1, 2, 3)) }
            )
            assertThrows(IllegalArgumentException::class.java) {
                MinecraftAssetInstallCoordinator(downloader, maxTotalBytes = 2)
                    .install(listOf(plan(byteArrayOf(1, 2, 3))), directory)
            }
            assertTrue(!opened)
            assertTrue(directory.walkTopDown().none { it.isFile })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsNonAssetEntriesAndDuplicateDestinations() {
        val directory = Files.createTempDirectory("flame-assets-invalid-plan").toFile()
        try {
            val valid = plan(byteArrayOf(1))
            val coordinator = MinecraftAssetInstallCoordinator(
                MinecraftAssetDownloader(connectionFactory = { error("Network must not start") })
            )
            assertThrows(IllegalArgumentException::class.java) {
                coordinator.install(listOf(valid.copy(kind = MinecraftPlannedDownloadKind.CLIENT)), directory)
            }
            assertThrows(IllegalArgumentException::class.java) {
                coordinator.install(listOf(valid, valid), directory)
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
