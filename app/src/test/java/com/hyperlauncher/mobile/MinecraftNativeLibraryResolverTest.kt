package com.hyperlauncher.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MinecraftNativeLibraryResolverTest {
    @Test
    fun resolvesDeclaredLinuxNativeClassifierForX64() {
        val json = """
            {"libraries":[{
              "name":"org.example:native:1.0",
              "natives":{"linux":"natives-linux-64"},
              "downloads":{"classifiers":{"natives-linux-64":{
                "path":"org/example/native/1.0/native.jar",
                "url":"https://libraries.minecraft.net/native.jar",
                "sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":42
              }}}
            }]}
        """.trimIndent()
        val result = MinecraftNativeLibraryResolver.parse("1.21.4", json, osArch = "x86_64", isAndroid = false)
        assertEquals(1, result.size)
        assertEquals("native-library", result.single().kind)
        assertEquals(42L, result.single().descriptor.size)
    }

    @Test
    fun skipsWhenNoMatchingNativeClassifierExists() {
        val json = """{"libraries":[{"name":"org.example:native:1","natives":{"linux":"natives-linux-64"},"downloads":{"classifiers":{}}}]}"""
        assertEquals(0, MinecraftNativeLibraryResolver.parse("1.20.1", json, osArch = "x86_64", isAndroid = false).size)
    }

    @Test
    fun rejectsArchiveTraversalEntries() {
        val archive = File.createTempFile("hyper-native", ".zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../escape.so"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
        val destination = File(archive.parentFile, "hyper-native-out-test")
        try {
            assertThrows(IllegalArgumentException::class.java) {
                MinecraftNativeExtractor.extract(listOf(archive), destination)
            }
        } finally {
            archive.delete()
            destination.deleteRecursively()
        }
    }


    @Test
    fun neverSelectsDesktopLinuxNativesForAndroidEvenOnX64() {
        val json = """
            {"libraries":[{
              "name":"org.example:native:1.0",
              "natives":{"linux":"natives-linux-64"},
              "downloads":{"classifiers":{"natives-linux-64":{
                "path":"org/example/native/1.0/native.jar",
                "url":"https://libraries.minecraft.net/native.jar",
                "sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":42
              }}}
            }]}
        """.trimIndent()
        assertEquals(
            0,
            MinecraftNativeLibraryResolver.parse(
                "1.21.4", json, osArch = "x86_64", isAndroid = true
            ).size
        )
    }

    @Test
    fun doesNotSelectX64NativeClassifierForArm64Device() {
        val json = """
            {"libraries":[{
              "name":"org.example:native:1.0",
              "natives":{"linux":"natives-linux-64"},
              "downloads":{"classifiers":{"natives-linux-64":{
                "path":"org/example/native/1.0/native.jar",
                "url":"https://libraries.minecraft.net/native.jar",
                "sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":42
              }}}
            }]}
        """.trimIndent()
        assertEquals(0, MinecraftNativeLibraryResolver.parse("1.21.4", json, osArch = "aarch64").size)
    }

    @Test
    fun rejectsDuplicateOutputAcrossNativeArchives() {
        val first = File.createTempFile("hyper-native-first", ".zip")
        val second = File.createTempFile("hyper-native-second", ".zip")
        val destination = File(first.parentFile, "hyper-native-duplicate-test")
        fun writeArchive(file: File, byte: Byte) {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("lib/example.so"))
                zip.write(byteArrayOf(byte))
                zip.closeEntry()
            }
        }
        writeArchive(first, 1)
        writeArchive(second, 2)
        try {
            assertThrows(IllegalArgumentException::class.java) {
                MinecraftNativeExtractor.extract(listOf(first, second), destination)
            }
            assertEquals(false, File(destination, "lib/example.so").exists())
        } finally {
            first.delete()
            second.delete()
            destination.deleteRecursively()
        }
    }

    @Test
    fun rejectsAmbiguousEmptyPathSegments() {
        val archive = File.createTempFile("hyper-native-path", ".zip")
        val destination = File(archive.parentFile, "hyper-native-empty-segment-test")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("lib//example.so"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        try {
            assertThrows(IllegalArgumentException::class.java) {
                MinecraftNativeExtractor.extract(listOf(archive), destination)
            }
        } finally {
            archive.delete()
            destination.deleteRecursively()
        }
    }

    @Test
    fun removesPartialFilesWhenExtractionIsCancelled() {
        val archive = File.createTempFile("hyper-native-cancel", ".zip")
        val destination = File(archive.parentFile, "hyper-native-cancel-test")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("lib/example.so"))
            zip.write(ByteArray(128) { 7 })
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("lib/second.so"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        var checks = 0
        try {
            assertThrows(java.util.concurrent.CancellationException::class.java) {
                MinecraftNativeExtractor.extract(listOf(archive), destination) {
                    checks++ >= 3
                }
            }
            assertEquals(false, File(destination, "lib/example.so").exists())
        } finally {
            archive.delete()
            destination.deleteRecursively()
        }
    }

    @Test
    fun acceptsSafeDirectoryEntries() {
        val archive = File.createTempFile("hyper-native-dir", ".zip")
        val destination = File(archive.parentFile, "hyper-native-dir-test")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("lib/"))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("lib/example.so"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        try {
            val files = MinecraftNativeExtractor.extract(listOf(archive), destination)
            assertEquals(1, files.size)
            assertEquals(true, File(destination, "lib/example.so").exists())
        } finally {
            archive.delete()
            destination.deleteRecursively()
        }
    }
}\n