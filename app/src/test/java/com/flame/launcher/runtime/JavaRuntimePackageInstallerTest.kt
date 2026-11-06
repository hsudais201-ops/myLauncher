package com.flame.launcher.runtime

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class JavaRuntimePackageInstallerTest {
    @Test
    fun verifiesArchiveAndInstallsRuntimeIntoManagedDirectory() {
        val root = Files.createTempDirectory("flame-runtime-install").toFile()
        try {
            val archive = runtimeArchive("17.0.12+7")
            val installed = installer(root).install(
                id = "java-17-arm64",
                archiveInput = ByteArrayInputStream(archive),
                expectedSha256 = sha256(archive),
                expectedMajorVersion = 17,
                expectedArchitecture = "arm64-v8a"
            )

            assertEquals("java-17-arm64", installed.id)
            assertEquals(17, installed.majorVersion)
            assertTrue(File(installed.homeDirectory, "lib/server/libjvm.so").isFile)
            val resolved = JavaRuntimeManager(root, "aarch64").resolve(17)
            assertTrue(resolved is JavaRuntimeResolution.Available)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsWrongArchiveHashAndLeavesNoRuntimeInstalled() {
        val root = Files.createTempDirectory("flame-runtime-bad-hash").toFile()
        try {
            val archive = runtimeArchive("17.0.12")
            assertThrows(IllegalArgumentException::class.java) {
                installer(root).install(
                    "java-17",
                    ByteArrayInputStream(archive),
                    "0".repeat(64),
                    17,
                    "aarch64"
                )
            }
            assertTrue(JavaRuntimeManager(root, "aarch64").discover().isEmpty())
            assertFalse(File(root, "java-17").exists())
            assertTrue(root.listFiles().orEmpty().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun blocksZipTraversalBeforeWritingOutsideStagingDirectory() {
        val root = Files.createTempDirectory("flame-runtime-zip-slip").toFile()
        try {
            val archive = runtimeArchive(
                version = "17.0.12",
                extraEntries = mapOf("../escape.txt" to "not allowed".toByteArray())
            )
            assertThrows(IllegalArgumentException::class.java) {
                installer(root).install(
                    "java-17",
                    ByteArrayInputStream(archive),
                    sha256(archive),
                    17,
                    "aarch64"
                )
            }
            assertFalse(File(root.parentFile, "escape.txt").exists())
            assertTrue(JavaRuntimeManager(root, "aarch64").discover().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun enforcesExpandedSizeAndEntryCountLimits() {
        val root = Files.createTempDirectory("flame-runtime-limits").toFile()
        try {
            val archive = runtimeArchive(
                version = "17.0.12",
                extraEntries = mapOf("extra.bin" to ByteArray(256) { 7 })
            )
            val sizeLimited = JavaRuntimePackageInstaller(
                root, "aarch64", maxExpandedBytes = 64
            )
            assertThrows(IllegalArgumentException::class.java) {
                sizeLimited.install(
                    "java-17", ByteArrayInputStream(archive), sha256(archive), 17, "aarch64"
                )
            }

            val entryLimited = JavaRuntimePackageInstaller(
                root, "aarch64", maxEntries = 2
            )
            assertThrows(IllegalArgumentException::class.java) {
                entryLimited.install(
                    "java-17", ByteArrayInputStream(archive), sha256(archive), 17, "aarch64"
                )
            }
            assertTrue(JavaRuntimeManager(root, "aarch64").discover().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsWrongJavaMajorAndDeviceArchitecture() {
        val root = Files.createTempDirectory("flame-runtime-compat").toFile()
        try {
            val archive = runtimeArchive("21.0.4")
            assertThrows(IllegalArgumentException::class.java) {
                installer(root).install(
                    "java-17", ByteArrayInputStream(archive), sha256(archive), 17, "aarch64"
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                installer(root).install(
                    "java-17", ByteArrayInputStream(archive), sha256(archive), 21, "x86_64"
                )
            }
            assertTrue(JavaRuntimeManager(root, "aarch64").discover().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun replacementPromotesNewRuntimeAndDoesNotExposeBackupAsInstalled() {
        val root = Files.createTempDirectory("flame-runtime-replace").toFile()
        try {
            val first = runtimeArchive("17.0.9")
            val second = runtimeArchive("17.0.12")
            installer(root).install(
                "java-17", ByteArrayInputStream(first), sha256(first), 17, "aarch64"
            )
            val replaced = installer(root).install(
                "java-17", ByteArrayInputStream(second), sha256(second), 17, "aarch64"
            )

            assertEquals("17.0.12", replaced.version)
            val discovered = JavaRuntimeManager(root, "aarch64").discover()
            assertEquals(listOf("java-17"), discovered.map { it.id })
            assertTrue(root.listFiles().orEmpty().none { it.name.startsWith(".flame-backup-") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsOversizedArchiveBeforeExtractingAnything() {
        val root = Files.createTempDirectory("flame-runtime-archive-cap").toFile()
        try {
            val archive = runtimeArchive("17.0.12")
            val capped = JavaRuntimePackageInstaller(
                root, "aarch64", maxArchiveBytes = 12
            )
            assertThrows(IllegalArgumentException::class.java) {
                capped.install(
                    "java-17", ByteArrayInputStream(archive), sha256(archive), 17, "aarch64"
                )
            }
            assertTrue(JavaRuntimeManager(root, "aarch64").discover().isEmpty())
            assertTrue(root.listFiles().orEmpty().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun installer(root: File) = JavaRuntimePackageInstaller(
        runtimeRoot = root,
        deviceArchitecture = "arm64-v8a",
        maxArchiveBytes = 1024 * 1024,
        maxExpandedBytes = 1024 * 1024,
        maxEntries = 100
    )

    private fun runtimeArchive(
        version: String,
        extraEntries: Map<String, ByteArray> = emptyMap()
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val entries = linkedMapOf(
                "release" to "JAVA_VERSION=\"$version\"\nOS_ARCH=\"aarch64\"\n".toByteArray(),
                "lib/server/libjvm.so" to byteArrayOf(1, 2, 3, 4)
            )
            entries.putAll(extraEntries)
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
