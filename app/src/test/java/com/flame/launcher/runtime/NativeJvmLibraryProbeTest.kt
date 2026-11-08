package com.flame.launcher.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeJvmLibraryProbeTest {
    @Test
    fun inspectPassesCanonicalContainedLibraryToNativeInspector() {
        val root = Files.createTempDirectory("flame-jvm-probe").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val library = File(home, "lib/server/libjvm.so").apply {
                parentFile.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }
            var inspectedPath: String? = null
            val probe = NativeJvmLibraryProbe { path ->
                inspectedPath = path
                "OK: symbols found"
            }

            val result = probe.inspect(runtime(home, library))

            assertTrue(result.available)
            assertEquals("OK: symbols found", result.diagnostic)
            assertEquals(library.canonicalPath, inspectedPath)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun inspectReturnsUnavailableWhenNativeLinkerRejectsLibrary() {
        val root = Files.createTempDirectory("flame-jvm-probe").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val library = File(home, "libjvm.so").apply { writeBytes(byteArrayOf(1)) }
            val probe = NativeJvmLibraryProbe { "ERROR: missing dependency" }

            val result = probe.inspect(runtime(home, library))

            assertFalse(result.available)
            assertEquals("ERROR: missing dependency", result.diagnostic)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun inspectRejectsJvmLibraryOutsideRuntimeHomeBeforeNativeCall() {
        val root = Files.createTempDirectory("flame-jvm-probe").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val outside = File(root, "libjvm.so").apply { writeBytes(byteArrayOf(1)) }
            var called = false
            val probe = NativeJvmLibraryProbe {
                called = true
                "OK"
            }

            assertThrows(IllegalArgumentException::class.java) {
                probe.inspect(runtime(home, outside))
            }
            assertFalse(called)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun inspectRejectsProcessRuntime() {
        val root = Files.createTempDirectory("flame-jvm-probe").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val java = File(home, "bin/java").apply {
                parentFile.mkdirs()
                writeBytes(byteArrayOf(1))
            }
            var called = false
            val probe = NativeJvmLibraryProbe {
                called = true
                "OK"
            }

            assertThrows(IllegalArgumentException::class.java) {
                probe.inspect(
                    JavaRuntimeInstallation(
                        id = "runtime",
                        version = "17.0.1",
                        majorVersion = 17,
                        architecture = "arm64",
                        homeDirectory = home,
                        javaExecutable = java,
                        jvmLibrary = null
                    )
                )
            }
            assertFalse(called)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun runtime(home: File, library: File) = JavaRuntimeInstallation(
        id = "runtime",
        version = "17.0.1",
        majorVersion = 17,
        architecture = "arm64",
        homeDirectory = home,
        javaExecutable = null,
        jvmLibrary = library
    )
}
