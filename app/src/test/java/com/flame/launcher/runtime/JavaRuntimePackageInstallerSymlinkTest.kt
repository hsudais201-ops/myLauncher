package com.flame.launcher.runtime

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JavaRuntimePackageInstallerSymlinkTest {
    @Test
    fun refusesToReplaceDestinationSymlinkThatPointsOutsideRuntimeRoot() {
        val parent = Files.createTempDirectory("flame-runtime-symlink").toFile()
        val root = Files.createDirectory(parent.toPath().resolve("runtimes")).toFile()
        val outside = Files.createDirectory(parent.toPath().resolve("outside")).toFile()
        try {
            val marker = Files.write(outside.toPath().resolve("keep.txt"), "preserve".toByteArray()).toFile()
            Files.createSymbolicLink(
                root.toPath().resolve("java-17"),
                outside.toPath()
            )
            val archive = runtimeArchive()
            val installer = JavaRuntimePackageInstaller(
                runtimeRoot = root,
                deviceArchitecture = "aarch64",
                maxArchiveBytes = 1024 * 1024,
                maxExpandedBytes = 1024 * 1024,
                maxEntries = 100
            )

            assertThrows(IllegalArgumentException::class.java) {
                installer.install(
                    id = "java-17",
                    archiveInput = ByteArrayInputStream(archive),
                    expectedSha256 = sha256(archive),
                    expectedMajorVersion = 17,
                    expectedArchitecture = "aarch64"
                )
            }

            assertEquals("preserve", marker.readText())
            assertEquals(outside.canonicalFile, root.resolve("java-17").canonicalFile)
        } finally {
            parent.deleteRecursively()
        }
    }

    private fun runtimeArchive(): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("release"))
            zip.write("JAVA_VERSION=\"17.0.12\"\nOS_ARCH=\"aarch64\"\n".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("lib/server/libjvm.so"))
            zip.write(byteArrayOf(1, 2, 3, 4))
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
