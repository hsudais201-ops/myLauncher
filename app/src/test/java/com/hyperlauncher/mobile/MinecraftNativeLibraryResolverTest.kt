package com.hyperlauncher.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MinecraftNativeLibraryResolverTest {
    @Test
    fun resolvesDeclaredLinuxNativeClassifier() {
        val json = """
            {"libraries":[{
              "name":"org.example:native:1.0",
              "natives":{"linux":"natives-linux-${arch}"},
              "downloads":{"classifiers":{"natives-linux-64":{
                "path":"org/example/native/1.0/native-1.0-natives-linux.jar",
                "url":"https://libraries.minecraft.net/org/example/native/1.0/native.jar",
                "sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":42
              }}}
            }]}
        """.trimIndent()
        val result = MinecraftNativeLibraryResolver.parse("1.21.4", json, osArch = "aarch64")
        assertEquals(1, result.size)
        assertEquals("native-library", result.single().kind)
        assertEquals(42L, result.single().descriptor.size)
    }

    @Test
    fun skipsWhenNoMatchingNativeClassifierExists() {
        val json = """{"libraries":[{"name":"org.example:native:1","natives":{"linux":"natives-linux-${arch}"},"downloads":{"classifiers":{}}}]}"""
        assertEquals(0, MinecraftNativeLibraryResolver.parse("1.20.1", json, osArch = "aarch64").size)
    }

    @Test
    fun rejectsArchiveTraversalEntries() {
        val archive = File.createTempFile("hyper-native", ".zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../escape.so"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
        val destination = File(archive.parentFile, "hyper-native-out-${'$'}{System.nanoTime()}")
        try {
            assertThrows(IllegalArgumentException::class.java) {
                MinecraftNativeExtractor.extract(listOf(archive), destination)
            }
        } finally {
            archive.delete()
            destination.deleteRecursively()
        }
    }
}
