package com.flame.launcher.runtime

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JavaRuntimePackageInstallCoordinatorTest {
    @Test
    fun downloadsVerifiesAndInstallsCatalogPackageInOrder() {
        val root = Files.createTempDirectory("flame-runtime-coordinator").toFile()
        try {
            val archive = runtimeArchive()
            val connection = FakeConnection(archive)
            val descriptor = descriptor(archive)
            val installer = JavaRuntimePackageInstaller(
                runtimeRoot = File(root, "runtimes"),
                deviceArchitecture = "arm64-v8a",
                maxArchiveBytes = 1024 * 1024,
                maxExpandedBytes = 1024 * 1024,
                maxEntries = 100
            )
            val coordinator = JavaRuntimePackageInstallCoordinator(
                downloader = JavaRuntimePackageDownloader(
                    trustedHosts = setOf("runtime.example"),
                    connectionFactory = { connection },
                    maxArchiveBytes = 1024 * 1024
                ),
                installer = installer,
                downloadDirectory = File(root, "downloads"),
                deviceArchitecture = "arm64-v8a"
            )
            val progress = mutableListOf<JavaRuntimeInstallProgress>()
            val installed = coordinator.install(descriptor, progress::add)

            assertEquals(17, installed.majorVersion)
            assertEquals("17.0.12", installed.version)
            assertTrue(connection.disconnected)
            assertTrue(progress.first() is JavaRuntimeInstallProgress.Downloading)
            assertTrue(progress.any { it is JavaRuntimeInstallProgress.DownloadProgress })
            assertTrue(progress.any { it is JavaRuntimeInstallProgress.Installing })
            assertTrue(progress.last() is JavaRuntimeInstallProgress.Completed)
            val downloadProgress = progress.filterIsInstance<JavaRuntimeInstallProgress.DownloadProgress>()
            assertEquals(archive.size.toLong(), downloadProgress.last().receivedBytes)
            assertEquals(archive.size.toLong(), downloadProgress.last().totalBytes)
            assertTrue(File(root, "runtimes/java-17").isDirectory)
            assertTrue(File(root, "downloads").listFiles().orEmpty().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsWrongDeviceArchitectureBeforeNetworkAccess() {
        val root = Files.createTempDirectory("flame-runtime-coordinator-abi").toFile()
        try {
            val archive = runtimeArchive()
            var opened = false
            val coordinator = JavaRuntimePackageInstallCoordinator(
                downloader = JavaRuntimePackageDownloader(
                    trustedHosts = setOf("runtime.example"),
                    connectionFactory = { opened = true; FakeConnection(archive) }
                ),
                installer = JavaRuntimePackageInstaller(File(root, "runtimes"), "arm64-v8a"),
                downloadDirectory = File(root, "downloads"),
                deviceArchitecture = "arm64-v8a"
            )
            assertThrows(IllegalArgumentException::class.java) {
                coordinator.install(descriptor(archive).copy(architecture = "x86_64"))
            }
            assertFalse(opened)
            assertTrue(File(root, "downloads").listFiles().orEmpty().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsCatalogSizeMismatchAndCleansTemporaryArchive() {
        val root = Files.createTempDirectory("flame-runtime-coordinator-size").toFile()
        try {
            val archive = runtimeArchive()
            val coordinator = JavaRuntimePackageInstallCoordinator(
                downloader = JavaRuntimePackageDownloader(
                    trustedHosts = setOf("runtime.example"),
                    connectionFactory = { FakeConnection(archive) },
                    maxArchiveBytes = 1024 * 1024
                ),
                installer = JavaRuntimePackageInstaller(File(root, "runtimes"), "arm64-v8a"),
                downloadDirectory = File(root, "downloads"),
                deviceArchitecture = "arm64-v8a"
            )
            assertThrows(IllegalArgumentException::class.java) {
                coordinator.install(descriptor(archive).copy(sizeBytes = archive.size.toLong() + 1))
            }
            assertTrue(File(root, "downloads").listFiles().orEmpty().isEmpty())
            assertTrue(JavaRuntimeManager(File(root, "runtimes"), "arm64-v8a").discover().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun descriptor(bytes: ByteArray) = JavaRuntimePackageDescriptor(
        id = "java-17",
        version = "17.0.12",
        javaMajorVersion = 17,
        architecture = "aarch64",
        downloadUrl = "https://runtime.example/java.zip",
        sha256 = sha256(bytes),
        sizeBytes = bytes.size.toLong(),
        archiveType = "zip"
    )

    private fun runtimeArchive(): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val entries = mapOf(
                "release" to "JAVA_VERSION=\"17.0.12\"\nOS_ARCH=\"aarch64\"\n".toByteArray(),
                "lib/server/libjvm.so" to byteArrayOf(1, 2, 3, 4)
            )
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private class FakeConnection(private val body: ByteArray) : HttpURLConnection(URL(URL_STRING)) {
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = HTTP_OK
        override fun getContentLengthLong(): Long = body.size.toLong()
        override fun getInputStream() = ByteArrayInputStream(body)
    }

    companion object {
        private const val URL_STRING = "https://runtime.example/java.zip"
    }
}
