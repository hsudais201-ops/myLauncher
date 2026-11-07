package com.flame.launcher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest

class MinecraftArtifactInstallCoordinatorTest {
    private fun descriptor(bytes: ByteArray, path: String) = MinecraftDownloadDescriptor(
        url = "https://libraries.minecraft.net/$path",
        sha1 = MessageDigest.getInstance("SHA-1").digest(bytes)
            .joinToString("") { "%02x".format(it) },
        size = bytes.size.toLong(),
        path = path
    )

    private class FakeConnection(private val bytes: ByteArray) :
        HttpURLConnection(URL("https://libraries.minecraft.net/test")) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = HTTP_OK
        override fun getContentLengthLong() = bytes.size.toLong()
        override fun getInputStream() = ByteArrayInputStream(bytes)
    }

    private fun plan(): MinecraftLaunchDependencyPlan {
        val client = byteArrayOf(1, 2, 3)
        val library = byteArrayOf(4, 5)
        return MinecraftLaunchDependencyPlan(
            versionId = "1.21",
            mainClass = "net.minecraft.client.main.Main",
            requiredJavaMajorVersion = 21,
            assetIndexId = "17",
            downloads = listOf(
                MinecraftPlannedDownload(
                    MinecraftPlannedDownloadKind.CLIENT,
                    "versions/1.21/1.21.jar",
                    descriptor(client, "client.jar")
                ),
                MinecraftPlannedDownload(
                    MinecraftPlannedDownloadKind.LIBRARY,
                    "libraries/org/example/lib/1.0/lib-1.0.jar",
                    descriptor(library, "org/example/lib/1.0/lib-1.0.jar")
                )
            ),
            classPath = listOf("versions/1.21/1.21.jar", "libraries/org/example/lib/1.0/lib-1.0.jar"),
            nativeLibraries = emptyList()
        )
    }

    @Test
    fun installsPlanSequentiallyAndReportsAggregateProgress() {
        val directory = Files.createTempDirectory("flame-artifact-coordinator").toFile()
        val bodies = ArrayDeque(listOf(byteArrayOf(1, 2, 3), byteArrayOf(4, 5)))
        try {
            val downloader = MinecraftPlannedArtifactDownloader(connectionFactory = {
                FakeConnection(bodies.removeFirst())
            })
            val updates = mutableListOf<MinecraftArtifactInstallProgress>()
            val installed = MinecraftArtifactInstallCoordinator(downloader).install(
                plan(), directory
            ) { updates += it }
            assertEquals(2, installed.size)
            assertEquals(5L, updates.last().completedBytes)
            assertEquals(5L, updates.last().totalBytes)
            assertTrue(installed.all { it.isFile })
            assertTrue(bodies.isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsAggregateSizeLimitBeforeOpeningNetwork() {
        val directory = Files.createTempDirectory("flame-artifact-limit").toFile()
        try {
            var opened = false
            val downloader = MinecraftPlannedArtifactDownloader(connectionFactory = {
                opened = true
                FakeConnection(byteArrayOf(1, 2, 3))
            })
            assertThrows(IllegalArgumentException::class.java) {
                MinecraftArtifactInstallCoordinator(downloader, maxTotalBytes = 4)
                    .install(plan(), directory)
            }
            assertTrue(!opened)
        } finally {
            directory.deleteRecursively()
        }
    }
}
