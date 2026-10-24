package com.voidcraft.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class MinecraftInstallExecutorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val metadataBytes = "metadata content".toByteArray()
    private val clientBytes = "client jar content".toByteArray()

    @Test
    fun downloadsAllArtifactsThenPromotesVerifiedFiles() {
        val root = temporaryFolder.newFolder("game-root")
        val executor = MinecraftInstallExecutor(
            SecureFileDownloader(DownloadConnectionFactory { url -> fakeFor(url) })
        )
        var lastProgressIndex = 0
        val result = executor.execute(
            plan(),
            root,
            onProgress = { lastProgressIndex = it.artifactIndex }
        )

        assertEquals("metadata content", File(root, "versions/1.21.4/1.21.4.json").readText())
        assertEquals("client jar content", File(root, "versions/1.21.4/1.21.4.jar").readText())
        assertEquals(2, result.receipts.size)
        assertEquals(2, lastProgressIndex)
        assertEquals(metadataBytes.size + clientBytes.size, result.totalBytesDownloaded.toInt())
        assertNoStagingDirectories(root)
    }

    @Test
    fun cancelledDownloadPreservesExistingFilesAndRemovesStaging() {
        val root = temporaryFolder.newFolder("game-root")
        val metadata = File(root, "versions/1.21.4/1.21.4.json")
        assertTrue(metadata.parentFile.mkdirs())
        metadata.writeText("existing metadata")
        var cancelled = false
        val executor = MinecraftInstallExecutor(
            SecureFileDownloader(DownloadConnectionFactory { url -> fakeFor(url) })
        )

        assertThrows(DownloadCancelledException::class.java) {
            executor.execute(plan(), root, isCancelled = { cancelled }, onProgress = { cancelled = true })
        }

        assertEquals("existing metadata", metadata.readText())
        assertFalse(File(root, "versions/1.21.4/1.21.4.jar").exists())
        assertNoStagingDirectories(root)
    }

    @Test
    fun verifiedFilesAreReusedWithoutNetworkAndCountAsZeroDownloadedBytes() {
        val root = temporaryFolder.newFolder("game-root")
        var initialRequests = 0
        val firstExecutor = MinecraftInstallExecutor(
            SecureFileDownloader(DownloadConnectionFactory { url ->
                initialRequests += 1
                fakeFor(url)
            })
        )
        val first = firstExecutor.execute(plan(), root)
        assertEquals(2, initialRequests)
        assertEquals(metadataBytes.size + clientBytes.size, first.totalBytesDownloaded.toInt())
        assertTrue(first.receipts.none { it.wasCached })

        val secondExecutor = MinecraftInstallExecutor(
            SecureFileDownloader(DownloadConnectionFactory {
                throw AssertionError("Verified cached artifacts should not trigger network calls")
            })
        )
        val second = secondExecutor.execute(plan(), root)
        assertTrue(second.receipts.all { it.wasCached })
        assertEquals(0L, second.totalBytesDownloaded)
        assertEquals(metadataBytes.size + clientBytes.size,
            second.receipts.sumOf { it.bytesDownloaded }.toInt())
    }

    @Test
    fun checksumMismatchIsRedownloadedWhileVerifiedFilesRemainCached() {
        val root = temporaryFolder.newFolder("game-root")
        val metadata = File(root, "versions/1.21.4/1.21.4.json")
        assertTrue(metadata.parentFile.mkdirs())
        metadata.writeText("bad metadata")
        val client = File(root, "versions/1.21.4/1.21.4.jar")
        client.writeBytes(clientBytes)

        var requests = 0
        val executor = MinecraftInstallExecutor(
            SecureFileDownloader(DownloadConnectionFactory { url ->
                requests += 1
                fakeFor(url)
            })
        )
        val result = executor.execute(plan(), root)

        assertEquals(1, requests)
        assertEquals("metadata content", metadata.readText())
        assertEquals("client jar content", client.readText())
        assertFalse(result.receipts.first().wasCached)
        assertTrue(result.receipts.last().wasCached)
        assertEquals(metadataBytes.size, result.totalBytesDownloaded.toInt())
    }

    @Test
    fun incompletePlanIsRejectedBeforeCreatingInstallFiles() {
        val root = temporaryFolder.newFolder("game-root")
        val incomplete = plan().copy(warnings = listOf("parent metadata is unresolved"))

        assertThrows(IncompleteInstallPlanException::class.java) {
            MinecraftInstallExecutor().execute(incomplete, root)
        }
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun traversalPathIsRejectedBeforeNetworkOrWrites() {
        val root = temporaryFolder.newFolder("game-root")
        var opened = false
        val executor = MinecraftInstallExecutor(
            SecureFileDownloader(DownloadConnectionFactory {
                opened = true
                fakeFor(it)
            })
        )
        val unsafe = plan().copy(downloads = listOf(
            planned("client", "../outside.jar", "https://piston-data.mojang.com/client.jar", clientBytes)
        ))

        assertThrows(IllegalArgumentException::class.java) {
            executor.execute(unsafe, root)
        }
        assertFalse(opened)
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun clientTasksCannotWriteOutsideSelectedVersionDirectory() {
        val root = temporaryFolder.newFolder("game-root")
        var opened = false
        val executor = MinecraftInstallExecutor(
            SecureFileDownloader(DownloadConnectionFactory {
                opened = true
                fakeFor(it)
            })
        )
        val unsafe = plan().copy(downloads = listOf(
            planned(
                MinecraftInstallPlanner.KIND_CLIENT,
                "saves/world.dat",
                "https://piston-data.mojang.com/client.jar",
                clientBytes
            )
        ))

        assertThrows(IllegalArgumentException::class.java) {
            executor.execute(unsafe, root)
        }
        assertFalse(opened)
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun unknownArtifactKindIsRejectedBeforeNetwork() {
        val root = temporaryFolder.newFolder("game-root")
        var opened = false
        val executor = MinecraftInstallExecutor(
            SecureFileDownloader(DownloadConnectionFactory {
                opened = true
                fakeFor(it)
            })
        )
        val unsafe = plan().copy(downloads = listOf(
            planned("arbitrary", "versions/1.21.4/arbitrary.bin",
                "https://piston-data.mojang.com/arbitrary.bin", clientBytes)
        ))

        assertThrows(IllegalArgumentException::class.java) {
            executor.execute(unsafe, root)
        }
        assertFalse(opened)
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun failedCommitRestoresPreviousFileAndPreservesDirectoryTarget() {
        val root = temporaryFolder.newFolder("game-root")
        val oldMetadata = File(root, "versions/1.21.4/1.21.4.json")
        assertTrue(oldMetadata.parentFile.mkdirs())
        oldMetadata.writeText("old metadata")

        val clientTarget = File(root, "versions/1.21.4/1.21.4.jar")
        assertTrue(clientTarget.mkdirs())
        val sentinel = File(clientTarget, "keep.txt")
        sentinel.writeText("do not delete")

        val executor = MinecraftInstallExecutor(
            SecureFileDownloader(DownloadConnectionFactory { url -> fakeFor(url) })
        )
        assertThrows(IOException::class.java) { executor.execute(plan(), root) }

        assertEquals("old metadata", oldMetadata.readText())
        assertEquals("do not delete", sentinel.readText())
        assertNoStagingDirectories(root)
    }

    private fun plan(): MinecraftInstallPlan = MinecraftInstallPlan(
        versionId = "1.21.4",
        javaMajorVersion = 21,
        mainClass = "net.minecraft.client.main.Main",
        downloads = listOf(
            planned(
                "version_metadata",
                "versions/1.21.4/1.21.4.json",
                "https://piston-meta.mojang.com/versions/1.21.4.json",
                metadataBytes
            ),
            planned(
                "client",
                "versions/1.21.4/1.21.4.jar",
                "https://piston-data.mojang.com/versions/1.21.4.jar",
                clientBytes
            )
        ),
        warnings = emptyList()
    )

    private fun planned(kind: String, path: String, url: String, bytes: ByteArray) =
        MinecraftPlannedDownload(
            kind = kind,
            destinationRelativePath = path,
            url = url,
            expectedSha1 = digest("SHA-1", bytes),
            expectedBytes = bytes.size.toLong(),
            logicalName = kind
        )

    private fun fakeFor(url: URL): HttpURLConnection {
        val bytes = when {
            url.path.endsWith(".json") -> metadataBytes
            url.path.endsWith(".jar") -> clientBytes
            else -> throw AssertionError("Unexpected fake URL: " + url)
        }
        return FakeConnection(url, bytes)
    }

    private fun digest(algorithm: String, bytes: ByteArray): String =
        MessageDigest.getInstance(algorithm).digest(bytes).joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }

    private fun assertNoStagingDirectories(root: File) {
        assertTrue(root.listFiles().orEmpty().none {
            it.name.startsWith(".voidcraft-install-") || it.name.startsWith(".voidcraft-txn-backups")
        })
    }

    private class FakeConnection(url: URL, private val bytes: ByteArray) : HttpURLConnection(url) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = 200
        override fun getInputStream() = ByteArrayInputStream(bytes)
        override fun getContentLengthLong(): Long = bytes.size.toLong()
    }
}
