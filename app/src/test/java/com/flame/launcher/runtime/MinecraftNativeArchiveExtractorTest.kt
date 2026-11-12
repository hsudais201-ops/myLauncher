package com.flame.launcher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MinecraftNativeArchiveExtractorTest {
    private fun archive(directory: File, entries: List<Pair<String, ByteArray>>): File {
        val file = File(directory, "native-${System.nanoTime()}.jar")
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun extractsFilesAndHonorsMojangExclusions() {
        val root = Files.createTempDirectory("flame-native-extract").toFile()
        try {
            val zip = archive(root, listOf(
                "META-INF/MANIFEST.MF" to byteArrayOf(1),
                "org/lwjgl/liblwjgl.so" to byteArrayOf(2, 3, 4)
            ))
            val destination = File(root, "natives/1.21")
            val files = MinecraftNativeArchiveExtractor().extract(
                listOf(MinecraftNativeArchiveExtractor.MinecraftNativeArchive(zip, listOf("META-INF/"))),
                destination
            )
            assertEquals(1, files.size)
            assertEquals(byteArrayOf(2, 3, 4).toList(), File(destination, "org/lwjgl/liblwjgl.so").readBytes().toList())
            assertFalse(File(destination, "META-INF").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsTraversalAndPreservesPreviousNativeDirectory() {
        val root = Files.createTempDirectory("flame-native-traversal").toFile()
        try {
            val destination = File(root, "natives/current").apply { mkdirs() }
            File(destination, "old.so").writeBytes(byteArrayOf(9))
            val zip = archive(root, listOf("../escape.so" to byteArrayOf(1)))
            assertThrows(IllegalArgumentException::class.java) {
                MinecraftNativeArchiveExtractor().extract(
                    listOf(MinecraftNativeArchiveExtractor.MinecraftNativeArchive(zip)),
                    destination
                )
            }
            assertTrue(File(destination, "old.so").exists())
            assertFalse(File(root, "escape.so").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun enforcesPerEntryAndAggregateLimits() {
        val root = Files.createTempDirectory("flame-native-limits").toFile()
        try {
            val zip = archive(root, listOf("native.so" to ByteArray(32) { 7 }))
            val destination = File(root, "natives")
            assertThrows(IllegalArgumentException::class.java) {
                MinecraftNativeArchiveExtractor(maxEntryBytes = 16, maxTotalBytes = 64)
                    .extract(listOf(MinecraftNativeArchiveExtractor.MinecraftNativeArchive(zip)), destination)
            }
            assertFalse(destination.exists())
            assertThrows(IllegalArgumentException::class.java) {
                MinecraftNativeArchiveExtractor(maxEntryBytes = 64, maxTotalBytes = 16)
                    .extract(listOf(MinecraftNativeArchiveExtractor.MinecraftNativeArchive(zip)), destination)
            }
            assertFalse(destination.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsDuplicateFilesAcrossNativeArchives() {
        val root = Files.createTempDirectory("flame-native-duplicates").toFile()
        try {
            val first = archive(root, listOf("same.so" to byteArrayOf(1)))
            val second = archive(root, listOf("same.so" to byteArrayOf(2)))
            val destination = File(root, "natives")
            assertThrows(IllegalArgumentException::class.java) {
                MinecraftNativeArchiveExtractor().extract(
                    listOf(
                        MinecraftNativeArchiveExtractor.MinecraftNativeArchive(first),
                        MinecraftNativeArchiveExtractor.MinecraftNativeArchive(second)
                    ),
                    destination
                )
            }
            assertFalse(destination.exists())
        } finally {
            root.deleteRecursively()
        }
    }
 
    @Test
    fun validationFailurePreservesPreviouslyInstalledNativeDirectory() {
        val root = Files.createTempDirectory("flame-native-validation-rollback").toFile()
        try {
            val destination = File(root, "natives/current").apply { mkdirs() }
            File(destination, "working.so").writeBytes(byteArrayOf(9, 8, 7))
            val zip = archive(root, listOf("new.so" to byteArrayOf(1, 2, 3)))

            assertThrows(IllegalStateException::class.java) {
                MinecraftNativeArchiveExtractor().extract(
                    listOf(MinecraftNativeArchiveExtractor.MinecraftNativeArchive(zip)),
                    destination
                ) { _, _ ->
                    throw IllegalStateException("Staged native validation failed.")
                }
            }

            assertEquals(
                byteArrayOf(9, 8, 7).toList(),
                File(destination, "working.so").readBytes().toList()
            )
            assertFalse(File(destination, "new.so").exists())
            assertTrue(root.listFiles().orEmpty().none { it.name.contains(".stage-") })
        } finally {
            root.deleteRecursively()
        }
    }

}
