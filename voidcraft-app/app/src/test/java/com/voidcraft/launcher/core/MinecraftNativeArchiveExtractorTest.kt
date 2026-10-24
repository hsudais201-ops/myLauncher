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

class MinecraftNativeArchiveExtractorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun extractsFilesAndHonorsMetadataExclusions() {
        val archive = zip(
            "native.jar",
            mapOf(
                "lib/example.so" to "native-bytes",
                "META-INF/MANIFEST.MF" to "manifest",
                "META-INF/services/example" to "service"
            )
        )
        val destination = File(temporaryFolder.root, "native-out")

        val result = MinecraftNativeArchiveExtractor().extract(
            archive = archive,
            destination = destination,
            excludes = listOf("META-INF/")
        )

        assertEquals(destination.canonicalFile, result.directory)
        assertEquals(1, result.filesWritten)
        assertEquals("native-bytes", File(destination, "lib/example.so").readText())
        assertFalse(File(destination, "META-INF").exists())
        assertEquals(2, result.entriesSkippedByRule)
        assertEquals("native-bytes".toByteArray().size.toLong(), result.bytesWritten)
        assertTrue(temporaryFolder.root.listFiles().orEmpty().none { it.name.startsWith(".voidcraft-native-stage-") })
    }

    @Test
    fun traversalEntryIsRejectedAndDoesNotCreateDestination() {
        val archive = zip("malicious.jar", mapOf("../escape.txt" to "do not write"))
        val destination = File(temporaryFolder.root, "native-out")
        val outside = File(temporaryFolder.root, "escape.txt")

        assertThrows(IOException::class.java) {
            MinecraftNativeArchiveExtractor().extract(archive, destination)
        }

        assertFalse(destination.exists())
        assertFalse(outside.exists())
        assertTrue(temporaryFolder.root.listFiles().orEmpty().none { it.name.startsWith(".voidcraft-native-stage-") })
    }

    @Test
    fun expandedEntryLimitIsEnforcedBeforePublishingOutput() {
        val archive = zip("large.jar", mapOf("lib/large.so" to "123456789"))
        val destination = File(temporaryFolder.root, "native-out")
        val extractor = MinecraftNativeArchiveExtractor(
            maxEntries = 10,
            maxEntryBytes = 4,
            maxExpandedBytes = 4,
            maxCompressionRatio = 100
        )

        assertThrows(IOException::class.java) { extractor.extract(archive, destination) }

        assertFalse(destination.exists())
        assertTrue(temporaryFolder.root.listFiles().orEmpty().none { it.name.startsWith(".voidcraft-native-stage-") })
    }

    @Test
    fun cancellationCleansStageAndNeverPublishesPartialOutput() {
        val archive = zip("cancel.jar", mapOf("lib/native.so" to "native"))
        val destination = File(temporaryFolder.root, "native-out")

        assertThrows(DownloadCancelledException::class.java) {
            MinecraftNativeArchiveExtractor().extract(archive, destination, isCancelled = { true })
        }

        assertFalse(destination.exists())
        assertTrue(temporaryFolder.root.listFiles().orEmpty().none { it.name.startsWith(".voidcraft-native-stage-") })
    }

    @Test
    fun existingDestinationIsNeverOverwritten() {
        val archive = zip("native.jar", mapOf("lib/native.so" to "new"))
        val destination = File(temporaryFolder.root, "native-out")
        assertTrue(destination.mkdirs())
        val sentinel = File(destination, "keep.txt")
        sentinel.writeText("keep")

        assertThrows(IOException::class.java) {
            MinecraftNativeArchiveExtractor().extract(archive, destination)
        }

        assertEquals("keep", sentinel.readText())
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
