package com.flame.launcher.runtime

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest

class MinecraftPlannedArtifactDownloaderTest {
    private fun sha1(bytes: ByteArray) = MessageDigest.getInstance("SHA-1")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun plan(
        bytes: ByteArray,
        kind: MinecraftPlannedDownloadKind = MinecraftPlannedDownloadKind.CLIENT,
        path: String = "versions/1.21/1.21.jar",
        url: String = "https://piston-data.mojang.com/v1/client.jar"
    ) = MinecraftPlannedDownload(
        kind = kind,
        relativePath = path,
        descriptor = MinecraftDownloadDescriptor(url, sha1(bytes), bytes.size.toLong(),
            if (kind == MinecraftPlannedDownloadKind.LIBRARY || kind == MinecraftPlannedDownloadKind.NATIVE) {
                path.removePrefix("libraries/")
            } else null)
    )

    private class FakeConnection(
        private val body: ByteArray,
        private val status: Int = HTTP_OK,
        private val declaredLength: Long = body.size.toLong()
    ) : HttpURLConnection(URL("https://piston-data.mojang.com/test")) {
        var disconnected = false
        var redirectsEnabled = true
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy(): Boolean = false
        override fun getResponseCode() = status
        override fun getContentLengthLong() = declaredLength
        override fun getInputStream() = ByteArrayInputStream(body)
        override fun setInstanceFollowRedirects(follow: Boolean) {
            redirectsEnabled = follow
            super.setInstanceFollowRedirects(follow)
        }
    }

    @Test
    fun downloadsAndVerifiesClientJarBeforePromotion() {
        val directory = Files.createTempDirectory("flame-client-artifact").toFile()
        try {
            val bytes = byteArrayOf(1, 3, 5, 7)
            val connection = FakeConnection(bytes)
            val updates = mutableListOf<Pair<Long, Long?>>()
            val result = MinecraftPlannedArtifactDownloader(connectionFactory = { connection })
                .download(plan(bytes), directory) { received, total -> updates += received to total }
            assertArrayEquals(bytes, result.readBytes())
            assertEqualsPair(bytes.size.toLong() to bytes.size.toLong(), updates.last())
            assertFalse(connection.redirectsEnabled)
            assertTrue(connection.disconnected)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun downloadsLibraryAndNativeArtifactsIntoMetadataPaths() {
        val directory = Files.createTempDirectory("flame-library-artifact").toFile()
        try {
            val bytes = byteArrayOf(2, 4, 6)
            val downloader = MinecraftPlannedArtifactDownloader(connectionFactory = { FakeConnection(bytes) })
            val library = plan(bytes,
                kind = MinecraftPlannedDownloadKind.LIBRARY,
                path = "libraries/org/example/lib/1.0/lib-1.0.jar",
                url = "https://libraries.minecraft.net/org/example/lib/1.0/lib-1.0.jar")
            assertArrayEquals(bytes, downloader.download(library, directory).readBytes())
            val native = plan(bytes,
                kind = MinecraftPlannedDownloadKind.NATIVE,
                path = "libraries/org/example/lib/1.0/lib-1.0-natives-linux.jar",
                url = "https://libraries.minecraft.net/org/example/lib/1.0/lib-1.0-natives-linux.jar")
            assertArrayEquals(bytes, downloader.download(native, directory).readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun reusesExistingArtifactOnlyWhenSizeAndSha1Match() {
        val directory = Files.createTempDirectory("flame-client-cached").toFile()
        try {
            val bytes = byteArrayOf(5, 4, 3, 2, 1)
            val planned = plan(bytes)
            val target = File(directory, planned.relativePath)
            target.parentFile!!.mkdirs()
            target.writeBytes(bytes)
            val result = MinecraftPlannedArtifactDownloader(connectionFactory = {
                throw AssertionError("Verified cached artifact should not open a network connection.")
            }).download(planned, directory)
            assertArrayEquals(bytes, result.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsHashMismatchAndPreservesPreviousFile() {
        val directory = Files.createTempDirectory("flame-client-bad-hash").toFile()
        try {
            val expected = byteArrayOf(1, 2, 3)
            val planned = plan(expected)
            val previous = byteArrayOf(9, 9, 9)
            val target = File(directory, planned.relativePath)
            target.parentFile!!.mkdirs()
            target.writeBytes(previous)
            assertThrows(IllegalArgumentException::class.java) {
                MinecraftPlannedArtifactDownloader(connectionFactory = { FakeConnection(byteArrayOf(4, 5, 6)) })
                    .download(planned, directory)
            }
            assertArrayEquals(previous, target.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsRedirectUntrustedHostAndUnsafePathBeforeConnecting() {
        val directory = Files.createTempDirectory("flame-client-invalid").toFile()
        try {
            val bytes = byteArrayOf(1, 2, 3)
            var opened = false
            val downloader = MinecraftPlannedArtifactDownloader(connectionFactory = {
                opened = true
                FakeConnection(bytes, status = 302)
            })
            assertThrows(IllegalArgumentException::class.java) {
                downloader.download(plan(bytes), directory)
            }
            assertTrue(opened)
            opened = false
            assertThrows(IllegalArgumentException::class.java) {
                MinecraftPlannedArtifactDownloader(connectionFactory = { opened = true; FakeConnection(bytes) })
                    .download(plan(bytes, url = "https://attacker.example/file"), directory)
            }
            assertFalse(opened)
            assertThrows(IllegalArgumentException::class.java) {
                MinecraftPlannedArtifactDownloader(connectionFactory = { opened = true; FakeConnection(bytes) })
                    .download(plan(bytes, path = "versions/../../escape.jar"), directory)
            }
            assertFalse(opened)
            assertTrue(directory.walkTopDown().none { it.isFile })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsSymlinkedParentDirectoryBeforeConnecting() {
        val parent = Files.createTempDirectory("flame-artifact-symlink").toFile()
        val directory = File(parent, "game").apply { mkdirs() }
        val outside = File(parent, "outside").apply { mkdirs() }
        try {
            Files.createSymbolicLink(File(directory, "versions").toPath(), outside.toPath())
            var opened = false
            assertThrows(IllegalArgumentException::class.java) {
                MinecraftPlannedArtifactDownloader(connectionFactory = {
                    opened = true
                    FakeConnection(byteArrayOf(1))
                }).download(plan(byteArrayOf(1)), directory)
            }
            assertFalse(opened)
            assertTrue(outside.walkTopDown().none { it.isFile })
        } finally {
            parent.deleteRecursively()
        }
    }

    private fun assertEqualsPair(expected: Pair<Long, Long?>, actual: Pair<Long, Long?>) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
