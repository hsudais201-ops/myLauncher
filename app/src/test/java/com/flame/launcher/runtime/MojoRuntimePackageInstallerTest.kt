package com.flame.launcher.runtime

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest

class MojoRuntimePackageInstallerTest {
    @Test
    fun extractsBothComponentsValidatesAndPromotesRuntime() {
        val root = Files.createTempDirectory("mojo-runtime-install").toFile()
        val packageDir = File(root.parentFile, "mojo-package-${System.nanoTime()}").apply { mkdir() }
        try {
            val universal = File(packageDir, "universal.tar.xz")
            val platform = File(packageDir, "bin-arm64.tar.xz")
            createArchive(universal) {
                addFile("jre/release", "JAVA_VERSION=\"21.0.1\"\nOS_ARCH=\"aarch64\"\n".toByteArray())
                addFile("jre/bin/java", byteArrayOf(1, 2, 3), 0b111101101)
            }
            createArchive(platform) {
                addFile("jre/lib/server/libjvm.so", byteArrayOf(4, 5, 6, 7))
            }
            val installation = installer(root).install(packageOf(universal, platform))
            assertEquals("mojo-java-21-arm64", installation.id)
            assertEquals(21, installation.majorVersion)
            assertEquals("arm64", installation.architecture)
            assertTrue(installation.jvmLibrary?.isFile == true)
            assertTrue(installation.javaExecutable?.canExecute() == true)
            assertEquals(listOf("mojo-java-21-arm64"),
                JavaRuntimeManager(root, "aarch64").discover().map { it.id })
        } finally {
            root.deleteRecursively()
            packageDir.deleteRecursively()
        }
    }

    @Test
    fun rejectsComponentChangedAfterAuthentication() {
        val root = Files.createTempDirectory("mojo-runtime-tamper").toFile()
        val packageDir = File(root.parentFile, "mojo-package-${System.nanoTime()}").apply { mkdir() }
        try {
            val universal = File(packageDir, "universal.tar.xz")
            val platform = File(packageDir, "bin-arm64.tar.xz")
            createArchive(universal) {
                addFile("jre/release", "JAVA_VERSION=\"21.0.1\"\nOS_ARCH=\"aarch64\"\n".toByteArray())
            }
            createArchive(platform) {
                addFile("jre/lib/server/libjvm.so", byteArrayOf(4, 5, 6))
            }
            val packageData = packageOf(universal, platform)
            platform.appendBytes(byteArrayOf(99))
            assertThrows(IllegalArgumentException::class.java) {
                installer(root).install(packageData)
            }
            assertTrue(JavaRuntimeManager(root, "aarch64").discover().isEmpty())
        } finally {
            root.deleteRecursively()
            packageDir.deleteRecursively()
        }
    }

    @Test
    fun rejectsWrongJavaVersionAndLeavesNoPublishedRuntime() {
        val root = Files.createTempDirectory("mojo-runtime-version").toFile()
        val packageDir = File(root.parentFile, "mojo-package-${System.nanoTime()}").apply { mkdir() }
        try {
            val universal = File(packageDir, "universal.tar.xz")
            val platform = File(packageDir, "bin-arm64.tar.xz")
            createArchive(universal) {
                addFile("jre/release", "JAVA_VERSION=\"17.0.12\"\nOS_ARCH=\"aarch64\"\n".toByteArray())
            }
            createArchive(platform) {
                addFile("jre/lib/server/libjvm.so", byteArrayOf(4, 5, 6))
            }
            assertThrows(IllegalArgumentException::class.java) {
                installer(root).install(packageOf(universal, platform))
            }
            assertTrue(JavaRuntimeManager(root, "aarch64").discover().isEmpty())
            assertFalse(File(root, "mojo-java-21-arm64").exists())
        } finally {
            root.deleteRecursively()
            packageDir.deleteRecursively()
        }
    }

    @Test
    fun completionCallbackFailureDoesNotDeletePromotedRuntimeOrPreviousBackup() {
        val root = Files.createTempDirectory("mojo-runtime-callback").toFile()
        val packageDir = File(root.parentFile, "mojo-package-${System.nanoTime()}").apply { mkdir() }
        try {
            val universal = File(packageDir, "universal.tar.xz")
            val platform = File(packageDir, "bin-arm64.tar.xz")
            createArchive(universal) {
                addFile("jre/release", "JAVA_VERSION=\"21.0.1\"\nOS_ARCH=\"aarch64\"\n".toByteArray())
            }
            createArchive(platform) {
                addFile("jre/lib/server/libjvm.so", byteArrayOf(4, 5, 6))
            }
            installer(root).install(packageOf(universal, platform))

            createArchive(platform) {
                addFile("jre/lib/server/libjvm.so", byteArrayOf(8, 9, 10))
            }
            val replacement = installer(root).install(
                packageOf(universal, platform),
                onProgress = { progress ->
                    if (progress is MojoRuntimeInstallProgress.Completed) {
                        throw IllegalStateException("UI observer failed after completion.")
                    }
                }
            )

            assertEquals("mojo-java-21-arm64", replacement.id)
            assertArrayEquals(
                byteArrayOf(8, 9, 10),
                File(root, "mojo-java-21-arm64/lib/server/libjvm.so").readBytes()
            )
            assertEquals(
                listOf("mojo-java-21-arm64"),
                JavaRuntimeManager(root, "aarch64").discover().map { it.id }
            )
            assertTrue(root.listFiles().orEmpty().none { it.name.startsWith(".mojo-backup-") })
        } finally {
            root.deleteRecursively()
            packageDir.deleteRecursively()
        }
    }

    private fun installer(root: File) =
        MojoRuntimePackageInstaller(root, "arm64-v8a")

    private fun packageOf(universal: File, platform: File) = DownloadedMojoRuntimePackage(
        javaMajorVersion = 21,
        architecture = "arm64",
        directory = universal.parentFile,
        universalComponent = verified(universal),
        platformComponent = verified(platform)
    )

    private fun verified(file: File): VerifiedMojoRuntimeComponent =
        VerifiedMojoRuntimeComponent(file, file.length(), sha256(file))

    private fun createArchive(file: File, populate: ArchiveBuilder.() -> Unit) {
        XZOutputStream(FileOutputStream(file), LZMA2Options()).use { xz ->
            TarArchiveOutputStream(xz).use { tar ->
                ArchiveBuilder(tar).populate()
            }
        }
    }

    private class ArchiveBuilder(private val tar: TarArchiveOutputStream) {
        fun addFile(path: String, bytes: ByteArray, mode: Int = 0b110100100) {
            val entry = TarArchiveEntry(path).apply {
                size = bytes.size.toLong()
                this.mode = mode
            }
            tar.putArchiveEntry(entry)
            tar.write(bytes)
            tar.closeArchiveEntry()
        }
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
}
