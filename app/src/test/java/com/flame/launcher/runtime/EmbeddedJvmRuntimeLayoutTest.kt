package com.flame.launcher.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class EmbeddedJvmRuntimeLayoutTest {
    @Test
    fun resolvesRuntimeLibraryDirectoriesAndAndroidPreloadsInStableOrder() {
        val root = Files.createTempDirectory("flame-runtime-layout").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val vmDirectory = File(home, "lib/server").apply { mkdirs() }
            val jvm = File(vmDirectory, "libjvm.so").apply { writeBytes(byteArrayOf(1)) }
            val libraryDirectory = File(home, "lib")
            val jliDirectory = File(libraryDirectory, "jli").apply { mkdirs() }
            val awt = File(libraryDirectory, "libawt_headless.so").apply { writeBytes(byteArrayOf(2)) }
            val nio = File(libraryDirectory, "libnio.so").apply { writeBytes(byteArrayOf(3)) }
            val natives = File(root, "game/natives").apply { mkdirs() }

            val layout = EmbeddedJvmRuntimeLayoutResolver.resolve(home, jvm, natives)

            assertEquals(home.canonicalFile, layout.runtimeHome)
            assertEquals(jvm.canonicalFile, layout.jvmLibrary)
            assertEquals(natives.canonicalFile, layout.applicationNativeDirectory)
            assertEquals(
                listOf(
                    libraryDirectory.canonicalFile,
                    natives.canonicalFile,
                    vmDirectory.canonicalFile,
                    jliDirectory.canonicalFile
                ),
                layout.libraryDirectories
            )
            assertEquals(listOf(awt.canonicalFile, nio.canonicalFile), layout.optionalPreloadLibraries)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsJvmLibraryOutsideRuntimeHomeAndMissingNativeDirectory() {
        val root = Files.createTempDirectory("flame-runtime-layout-invalid").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val jvm = File(home, "lib/server/libjvm.so").apply {
                parentFile.mkdirs()
                writeBytes(byteArrayOf(1))
            }
            val outside = File(root, "outside/libjvm.so").apply {
                parentFile.mkdirs()
                writeBytes(byteArrayOf(2))
            }
            assertThrows(IllegalArgumentException::class.java) {
                EmbeddedJvmRuntimeLayoutResolver.resolve(home, outside)
            }
            assertThrows(IllegalArgumentException::class.java) {
                EmbeddedJvmRuntimeLayoutResolver.resolve(home, jvm, File(root, "missing"))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun ignoresOptionalPreloadsThatResolveOutsideRuntimeHome() {
        val root = Files.createTempDirectory("flame-runtime-layout-symlink").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val libraryDirectory = File(home, "lib").apply { mkdirs() }
            val jvm = File(libraryDirectory, "libjvm.so").apply { writeBytes(byteArrayOf(1)) }
            val outside = File(root, "outside.so").apply { writeBytes(byteArrayOf(2)) }
            val preload = File(libraryDirectory, "libnio.so")
            try {
                Files.createSymbolicLink(preload.toPath(), outside.toPath())
            } catch (_: UnsupportedOperationException) {
                return
            } catch (_: SecurityException) {
                return
            }

            val layout = EmbeddedJvmRuntimeLayoutResolver.resolve(home, jvm)
            assertTrue(layout.optionalPreloadLibraries.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
}
