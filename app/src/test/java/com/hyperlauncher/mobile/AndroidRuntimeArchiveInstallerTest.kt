package com.hyperlauncher.mobile

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.concurrent.CancellationException
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream

class AndroidRuntimeArchiveInstallerTest {
    private fun createArchive(
        entries: List<ArchiveEntry>
    ): File {
        val archive = File.createTempFile("hyper-runtime", ".tar.xz")
        TarArchiveOutputStream(
            XZOutputStream(FileOutputStream(archive), LZMA2Options())
        ).use { tar ->
            entries.forEach { item ->
                val entry = if (item.linkTarget != null) {
                    TarArchiveEntry(item.path, item.linkType)
                } else {
                    TarArchiveEntry(item.path)
                }
                entry.mode = item.mode
                entry.linkName = item.linkTarget ?: ""
                entry.size = item.bytes?.size?.toLong() ?: 0L
                tar.putArchiveEntry(entry)
                item.bytes?.let { tar.write(it) }
                tar.closeArchiveEntry()
            }
        }
        return archive
    }

    private fun validEntries(
        javaMajor: Int = 17,
        architecture: String = "amd64",
        javaBytes: ByteArray = byteArrayOf(1, 2, 3)
    ): List<ArchiveEntry> {
        val libraryRoot = "lib/$architecture"
        return listOf(
            ArchiveEntry(
                "release",
                "JAVA_VERSION=\"$javaMajor.0.1\"\nOS_ARCH=\"$architecture\"\n".toByteArray(),
                0b110100100
            ),
            // Deliberately no explicit bin/ directory header.
            ArchiveEntry("bin/java", javaBytes, 0b111101101)
        ) + listOf(
            "$libraryRoot/server/libjvm.so",
            "$libraryRoot/jli/libjli.so",
            "$libraryRoot/libjava.so",
            "$libraryRoot/libverify.so",
            "$libraryRoot/libnet.so",
            "$libraryRoot/libnio.so"
        ).map { path ->
            ArchiveEntry(path, byteArrayOf(1), 0b110100100)
        }
    }

    @Test
    fun extractsRuntimeAndWritesVerifiedAndroidProvenanceMarker() {
        val archive = createArchive(validEntries())
        val root = Files.createTempDirectory("hyper-runtime-stage").toFile()
        val staging = File(root, "staging")
        val digest = "a".repeat(64)
        try {
            val runtime = AndroidRuntimeArchiveExtractor.extract(
                archive, staging, 17, "x86_64", digest,
                "https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download/download_jre17/jre17-android-x86_64.tar.xz"
            )

            assertEquals(17, runtime.javaMajor)
            assertEquals("amd64", runtime.architecture)
            assertTrue(File(staging, "bin/java").canExecute())
            val marker = File(staging, "hyperlauncher-runtime.properties").readText()
            assertTrue(marker.contains("platform=android"))
            assertTrue(marker.contains("abi=x86_64"))
            assertTrue(marker.contains("javaMajor=17"))
            assertTrue(marker.contains("archiveSha256=$digest"))
            assertEquals(runtime, AndroidRuntimeInventory.inspectDirectory(staging, "x86_64"))
        } finally {
            archive.delete()
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsTraversalAndDeletesStagingDirectory() {
        val entries = validEntries() + ArchiveEntry("../escaped", byteArrayOf(9), 0b110100100)
        val archive = createArchive(entries)
        val root = Files.createTempDirectory("hyper-runtime-traversal").toFile()
        val staging = File(root, "staging")
        try {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                AndroidRuntimeArchiveExtractor.extract(
                    archive, staging, 17, "x86_64", "a".repeat(64), "https://github.com/runtime"
                )
            }
            assertFalse(staging.exists())
            assertFalse(File(root, "escaped").exists())
        } finally {
            archive.delete()
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsSymbolicLinkThatEscapesStagingDirectory() {
        val entries = validEntries() + ArchiveEntry(
            path = "bin/escape",
            bytes = null,
            mode = 0b111111111,
            linkTarget = "../../outside",
            linkType = TarConstants.LF_SYMLINK
        )
        val archive = createArchive(entries)
        val root = Files.createTempDirectory("hyper-runtime-symlink").toFile()
        val staging = File(root, "staging")
        try {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                AndroidRuntimeArchiveExtractor.extract(
                    archive, staging, 17, "x86_64", "b".repeat(64), "https://github.com/runtime"
                )
            }
            assertFalse(staging.exists())
            assertFalse(File(root, "outside").exists())
        } finally {
            archive.delete()
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsJavaMajorOrArchitectureMismatch() {
        val archive = createArchive(validEntries(javaMajor = 21, architecture = "aarch64"))
        val root = Files.createTempDirectory("hyper-runtime-mismatch").toFile()
        val staging = File(root, "staging")
        try {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                AndroidRuntimeArchiveExtractor.extract(
                    archive, staging, 17, "x86_64", "c".repeat(64), "https://github.com/runtime"
                )
            }
            assertFalse(staging.exists())
        } finally {
            archive.delete()
            root.deleteRecursively()
        }
    }

    @Test
    fun cancellationRemovesExtractedRuntimeFilesAndStage() {
        val archive = createArchive(
            validEntries(javaBytes = ByteArray(128 * 1024) { 7 })
        )
        val root = Files.createTempDirectory("hyper-runtime-cancel").toFile()
        val staging = File(root, "staging")
        var checks = 0
        try {
            org.junit.Assert.assertThrows(CancellationException::class.java) {
                AndroidRuntimeArchiveExtractor.extract(
                    archive, staging, 17, "x86_64", "d".repeat(64), "https://github.com/runtime"
                ) {
                    checks++ >= 4
                }
            }
            assertFalse(staging.exists())
        } finally {
            archive.delete()
            root.deleteRecursively()
        }
    }

    private data class ArchiveEntry(
        val path: String,
        val bytes: ByteArray?,
        val mode: Int,
        val linkTarget: String? = null,
        val linkType: Byte = TarConstants.LF_NORMAL
    )

    @Test
    fun rejectsArchiveAttemptToSupplyLauncherProvenanceMarker() {
        val archive = createArchive(
            validEntries() + ArchiveEntry(
                "hyperlauncher-runtime.properties",
                "platform=android\\nabi=x86_64\\n".toByteArray(),
                0b110100100
            )
        )
        val root = Files.createTempDirectory("hyper-runtime-marker").toFile()
        val staging = File(root, "staging")
        try {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                AndroidRuntimeArchiveExtractor.extract(
                    archive, staging, 17, "x86_64", "e".repeat(64), "https://github.com/runtime"
                )
            }
            assertFalse(staging.exists())
        } finally {
            archive.delete()
            root.deleteRecursively()
        }
    }

    @Test
    fun acceptsSafeInternalSymbolicAndHardLinks() {
        val archive = createArchive(
            validEntries() + listOf(
                ArchiveEntry("lib/source.so", byteArrayOf(4, 5, 6), 0b110100100),
                ArchiveEntry(
                    "lib/symbolic.so",
                    null,
                    0b111111111,
                    linkTarget = "../bin/java",
                    linkType = TarConstants.LF_SYMLINK
                ),
                ArchiveEntry(
                    "lib/hard.so",
                    null,
                    0b110100100,
                    linkTarget = "lib/source.so",
                    linkType = TarConstants.LF_LINK
                )
            )
        )
        val root = Files.createTempDirectory("hyper-runtime-links").toFile()
        val staging = File(root, "staging")
        try {
            val result = AndroidRuntimeArchiveExtractor.extract(
                archive, staging, 17, "x86_64", "f".repeat(64), "https://github.com/runtime"
            )
            assertEquals(17, result.javaMajor)
            assertTrue(Files.isSymbolicLink(File(staging, "lib/symbolic.so").toPath()))
            assertTrue(File(staging, "lib/hard.so").isFile)
            assertEquals(
                File(staging, "lib/source.so").readBytes().toList(),
                File(staging, "lib/hard.so").readBytes().toList()
            )
        } finally {
            archive.delete()
            root.deleteRecursively()
        }
    }
}
