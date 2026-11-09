package com.flame.launcher.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class NativeJvmLibraryLoaderTest {
    @Test
    fun retainPassesCanonicalContainedLibraryToNativeLoader() {
        val root = Files.createTempDirectory("flame-jvm-loader").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val library = File(home, "lib/server/libjvm.so").apply {
                parentFile.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }
            var retainedPath: String? = null
            val loader = NativeJvmLibraryLoader { path ->
                retainedPath = path
                "OK: retained"
            }

            val result = loader.retain(runtime(home, library))

            assertTrue(result.retained)
            assertEquals("OK: retained", result.diagnostic)
            assertEquals(library.canonicalPath, retainedPath)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun retainReportsNativeLoaderFailure() {
        val root = Files.createTempDirectory("flame-jvm-loader-failure").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val library = File(home, "libjvm.so").apply { writeBytes(byteArrayOf(1)) }
            val loader = NativeJvmLibraryLoader { "ERROR: a different JVM is already retained" }

            val result = loader.retain(runtime(home, library))

            assertFalse(result.retained)
            assertEquals("ERROR: a different JVM is already retained", result.diagnostic)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun retainRejectsLibraryOutsideRuntimeHomeBeforeNativeCall() {
        val root = Files.createTempDirectory("flame-jvm-loader-outside").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val outside = File(root, "libjvm.so").apply { writeBytes(byteArrayOf(1)) }
            var called = false
            val loader = NativeJvmLibraryLoader {
                called = true
                "OK"
            }

            assertThrows(IllegalArgumentException::class.java) {
                loader.retain(runtime(home, outside))
            }
            assertFalse(called)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun inspectVmStateUsesNativeInspectorWithoutCreatingVm() {
        var inspected = false
        val loader = NativeJvmLibraryLoader(
            nativeRetainer = { "OK: retained" },
            nativeVmInspector = {
                inspected = true
                "OK: retained OpenJDK reports 0 created VM(s)"
            }
        )

        val result = loader.inspectVmState()

        assertTrue(inspected)
        assertTrue(result.contains("0 created VM"))
    }

    @Test
    fun inspectVmStateReportsMissingRetainedLibrary() {
        val loader = NativeJvmLibraryLoader(
            nativeRetainer = { "ERROR: retention failed" },
            nativeVmInspector = { "ERROR: No embedded JVM library is retained." }
        )

        val result = loader.inspectVmState()

        assertTrue(result.startsWith("ERROR:"))
        assertTrue(result.contains("No embedded JVM"))
    }

    private fun runtime(home: File, library: File) = JavaRuntimeInstallation(
        id = "runtime",
        version = "21.0.1",
        majorVersion = 21,
        architecture = "arm64",
        homeDirectory = home,
        javaExecutable = null,
        jvmLibrary = library
    )
}
