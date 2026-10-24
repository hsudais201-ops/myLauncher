package com.voidcraft.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MinecraftNativeLibraryAssemblerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun assemblesMultipleArchivesAndReusesOnlyIdenticalDuplicateFiles() {
        val first = zip(
            "first.jar",
            mapOf(
                "lib/a.so" to "a",
                "common.dat" to "same",
                "META-INF/MANIFEST.MF" to "ignored"
            )
        )
        val second = zip(
            "second.jar",
            mapOf(
                "lib/b.so" to "bbb",
                "common.dat" to "same"
            )
        )
        val destination = File(temporaryFolder.root, "natives")

        val result = MinecraftNativeLibraryAssembler().assemble(
            listOf(
                NativeArchiveSource(first, listOf("META-INF/")),
                NativeArchiveSource(second)
            ),
            destination
        )

        assertEquals(2, result.archivesProcessed)
        assertEquals(3, result.filesWritten)
        assertEquals(1, result.identicalDuplicatesReused)
        assertEquals(8L, result.bytesWritten)
        assertEquals("a", File(destination, "lib/a.so").readText())
        assertEquals("bbb", File(destination, "lib/b.so").readText())
        assertEquals("same", File(destination, "common.dat").readText())
        assertFalse(File(destination, "META-INF").exists())
        assertTrue(temporaryFolder.root.listFiles().orEmpty().none {
            it.name.startsWith(".voidcraft-native-assembly-") || it.name.startsWith(".voidcraft-native-stage-")
        })
    }

    @Test
    fun conflictingDuplicatePathAbortsWithoutPublishingOutput() {
        val first = zip("first.jar", mapOf("lib/native.so" to "first"))
        val second = zip("second.jar", mapOf("lib/native.so" to "different"))
        val destination = File(temporaryFolder.root, "natives")

        assertThrows(IOException::class.java) {
            MinecraftNativeLibraryAssembler().assemble(
                listOf(NativeArchiveSource(first), NativeArchiveSource(second)),
                destination
            )
        }

        assertFalse(destination.exists())
        assertTrue(temporaryFolder.root.listFiles().orEmpty().none {
            it.name.startsWith(".voidcraft-native-assembly-") || it.name.startsWith(".voidcraft-native-stage-")
        })
    }

    @Test
    fun totalAssemblySizeIsBounded() {
        val archive = zip("large.jar", mapOf("large.dat" to "12345"))
        val destination = File(temporaryFolder.root, "natives")

        assertThrows(IOException::class.java) {
            MinecraftNativeLibraryAssembler(maxTotalBytes = 4L).assemble(
                listOf(NativeArchiveSource(archive)),
                destination
            )
        }

        assertFalse(destination.exists())
    }

    @Test
    fun cancellationAndExistingDestinationPreserveData() {
        val archive = zip("native.jar", mapOf("lib/native.so" to "native"))
        val cancelledDestination = File(temporaryFolder.root, "cancelled-natives")
        assertThrows(DownloadCancelledException::class.java) {
            MinecraftNativeLibraryAssembler().assemble(
                listOf(NativeArchiveSource(archive)),
                cancelledDestination,
                isCancelled = { true }
            )
        }
        assertFalse(cancelledDestination.exists())

        val existing = File(temporaryFolder.root, "existing-natives")
        assertTrue(existing.mkdirs())
        val sentinel = File(existing, "keep.txt")
        sentinel.writeText("keep")
        assertThrows(IOException::class.java) {
            MinecraftNativeLibraryAssembler().assemble(listOf(NativeArchiveSource(archive)), existing)
        }
        assertEquals("keep", sentinel.readText())
    }

    @Test
    fun rejectsAnEmptyArchiveList() {
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftNativeLibraryAssembler().assemble(
                emptyList(),
                File(temporaryFolder.root, "natives")
            )
        }
    }

    private fun zip(name: String, entries: Map<String, String>): File {
        val file = File(temporaryFolder.root, name)
        ZipOutputStream(FileOutputStream(file)).use { output ->
            entries.forEach { (path, content) ->
                output.putNextEntry(ZipEntry(path))
                output.write(content.toByteArray())
                output.closeEntry()
            }
        }
        return file
    }
}
