package com.flame.launcher.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeJvmRuntimePreparerTest {
    @Test
    fun preparationPreflightsBeforeRetainingAndReturnsResolvedLayout() {
        val root = Files.createTempDirectory("flame-jvm-prepare").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val library = File(home, "lib/server/libjvm.so").apply {
                parentFile.mkdirs()
                writeBytes(byteArrayOf(1))
            }
            val natives = File(root, "game/natives").apply { mkdirs() }
            val events = mutableListOf<String>()
            val preparer = NativeJvmRuntimePreparer(
                probe = NativeJvmLibraryProbe {
                    events += "probe"
                    "OK: linker accepted library"
                },
                loader = NativeJvmLibraryLoader(nativeRetainer = { _ ->
                    events += "retain"
                    "OK: handle retained"
                })
            )

            val result = preparer.prepare(runtime(home, library), natives)

            assertTrue(result is NativeJvmRuntimePreparationResult.Ready)
            val ready = result as NativeJvmRuntimePreparationResult.Ready
            assertEquals(listOf("probe", "retain"), events)
            assertEquals(home.canonicalFile, ready.runtimeHome)
            assertEquals(library.canonicalFile, ready.jvmLibrary)
            assertTrue(ready.libraryDirectories.contains(File(home, "lib").canonicalFile))
            assertTrue(ready.libraryDirectories.contains(natives.canonicalFile))
            assertEquals("OK: handle retained", ready.diagnostic)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun failedPreflightNeverAttemptsToRetainLibrary() {
        val root = Files.createTempDirectory("flame-jvm-prepare-failed").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val library = File(home, "lib/server/libjvm.so").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1)) }
            var retained = false
            val preparer = NativeJvmRuntimePreparer(
                probe = NativeJvmLibraryProbe { "ERROR: missing dependency" },
                loader = NativeJvmLibraryLoader(nativeRetainer = { _ ->
                    retained = true
                    "OK"
                })
            )

            val result = preparer.prepare(runtime(home, library))

            assertTrue(result is NativeJvmRuntimePreparationResult.Failed)
            assertFalse(retained)
            assertTrue((result as NativeJvmRuntimePreparationResult.Failed)
                .diagnostic.contains("missing dependency"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun failedRetentionIsReportedAfterSuccessfulPreflight() {
        val root = Files.createTempDirectory("flame-jvm-prepare-retain-failed").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val library = File(home, "lib/server/libjvm.so").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1)) }
            val events = mutableListOf<String>()
            val preparer = NativeJvmRuntimePreparer(
                probe = NativeJvmLibraryProbe {
                    events += "probe"
                    "OK"
                },
                loader = NativeJvmLibraryLoader(nativeRetainer = { _ ->
                    events += "retain"
                    "ERROR: another JVM is retained"
                })
            )

            val result = preparer.prepare(runtime(home, library))

            assertEquals(listOf("probe", "retain"), events)
            assertTrue(result is NativeJvmRuntimePreparationResult.Failed)
            assertTrue((result as NativeJvmRuntimePreparationResult.Failed)
                .diagnostic.contains("another JVM"))
        } finally {
            root.deleteRecursively()
        }
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
