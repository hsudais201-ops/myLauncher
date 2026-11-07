package com.flame.launcher.runtime

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

class MojoRuntimeTarXzExtractorTest {
    @Test
    fun extractsRuntimeFilesAndPreservesExecutableBit() {
        val temporary = Files.createTempDirectory("mojo-tar-test").toFile()
        val archive = File(temporary, "runtime.tar.xz")
        val destination = File(temporary, "stage").apply { mkdir() }
        try {
            createArchive(archive) {
                addDirectory("./")
                addFile("./jre/release", "JAVA_VERSION=\"21.0.1\"\n".toByteArray())
                addFile("jre/bin/java", byteArrayOf(1, 2, 3), mode = 0b111101101)
                addFile("jre/lib/server/libjvm.so", byteArrayOf(4, 5, 6))
            }
            val result = MojoRuntimeTarXzExtractor().extract(
                archive, destination, "universal.tar.xz"
            )
            assertEquals(3, result.entriesExtracted)
            assertEquals("JAVA_VERSION=\"21.0.1\"\n",
                File(destination, "jre/release").readText())
            assertTrue(File(destination, "jre/bin/java").canExecute())
            assertEquals(File(destination, "jre/release").length() + 6L, result.expandedBytes)
        } finally {
            temporary.deleteRecursively()
        }
    }

    @Test
    fun rejectsPathTraversalEntry() {
        val temporary = Files.createTempDirectory("mojo-tar-test").toFile()
        val archive = File(temporary, "traversal.tar.xz")
        val destination = File(temporary, "stage").apply { mkdir() }
        val outside = File(temporary, "escape.txt")
        try {
            createArchive(archive) {
                addFile("../escape.txt", "nope".toByteArray())
            }
            assertThrows(IllegalArgumentException::class.java) {
                MojoRuntimeTarXzExtractor().extract(archive, destination, "universal.tar.xz")
            }
            assertFalse(outside.exists())
        } finally {
            temporary.deleteRecursively()
        }
    }

    @Test
    fun rejectsSymbolicLinkThatEscapesStagingDirectory() {
        val temporary = Files.createTempDirectory("mojo-tar-test").toFile()
        val archive = File(temporary, "symlink.tar.xz")
        val destination = File(temporary, "stage").apply { mkdir() }
        try {
            createArchive(archive) {
                addSymlink("jre/escape", "../../outside")
            }
            assertThrows(IllegalArgumentException::class.java) {
                MojoRuntimeTarXzExtractor().extract(archive, destination, "universal.tar.xz")
            }
            assertFalse(File(temporary, "outside").exists())
        } finally {
            temporary.deleteRecursively()
        }
    }

    @Test
    fun allowsSafeRelativeSymlinkInsideStagingDirectory() {
        val temporary = Files.createTempDirectory("mojo-tar-test").toFile()
        val archive = File(temporary, "links.tar.xz")
        val destination = File(temporary, "stage").apply { mkdir() }
        try {
            createArchive(archive) {
                addFile("jre/lib/server/libjvm.so", byteArrayOf(9, 8, 7))
                addSymlink("jre/lib/libjvm.so", "server/libjvm.so")
            }
            MojoRuntimeTarXzExtractor().extract(archive, destination, "universal.tar.xz")
            val link = File(destination, "jre/lib/libjvm.so")
            assertTrue(Files.isSymbolicLink(link.toPath()))
            assertEquals(listOf<Byte>(9, 8, 7), link.readBytes().toList())
        } finally {
            temporary.deleteRecursively()
        }
    }

    private fun createArchive(file: File, populate: ArchiveBuilder.() -> Unit) {
        XZOutputStream(FileOutputStream(file), LZMA2Options()).use { xz ->
            TarArchiveOutputStream(xz).use { tar ->
                ArchiveBuilder(tar).populate()
            }
        }
    }

    private class ArchiveBuilder(private val tar: TarArchiveOutputStream) {
        fun addDirectory(path: String) {
            val entry = TarArchiveEntry(path).apply { mode = 0b111101101 }
            tar.putArchiveEntry(entry)
            tar.closeArchiveEntry()
        }

        fun addFile(path: String, bytes: ByteArray, mode: Int = 0b110100100) {
            val entry = TarArchiveEntry(path).apply {
                size = bytes.size.toLong()
                this.mode = mode
            }
            tar.putArchiveEntry(entry)
            tar.write(bytes)
            tar.closeArchiveEntry()
        }

        fun addSymlink(path: String, target: String) {
            val entry = TarArchiveEntry(path, TarConstants.LF_SYMLINK).apply {
                linkName = target
            }
            tar.putArchiveEntry(entry)
            tar.closeArchiveEntry()
        }
    }
}
