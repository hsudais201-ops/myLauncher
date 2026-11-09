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
                }),
                dependencyResolver = emptyDependencyResolver()
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
    fun preloadsResolvedDependenciesInDependencyFirstOrder() {
        val root = Files.createTempDirectory("flame-jvm-prepare-preload").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val lib = File(home, "lib").apply { mkdirs() }
            val vm = File(lib, "server").apply { mkdirs() }
            val library = File(vm, "libjvm.so").apply { writeBytes(byteArrayOf(1)) }
            val dependency = File(lib, "libverify.so").apply { writeBytes(byteArrayOf(2)) }
            val leaf = File(lib, "libzip.so").apply { writeBytes(byteArrayOf(3)) }
            val metadata = mapOf(
                library.canonicalFile to listOf("libverify.so"),
                dependency.canonicalFile to listOf("libzip.so"),
                leaf.canonicalFile to emptyList()
            )
            val received = mutableListOf<String>()
            val preparer = NativeJvmRuntimePreparer(
                probe = NativeJvmLibraryProbe { "OK: probe" },
                loader = NativeJvmLibraryLoader(
                    nativeRuntimeRetainer = { _, paths ->
                        received += paths.toList()
                        "OK: dependencies preloaded"
                    }
                ),
                dependencyResolver = JvmNativeDependencyResolver(
                    JvmElfDynamicMetadataReader { file ->
                        JvmElfDynamicMetadata(metadata[file.canonicalFile].orEmpty())
                    }
                )
            )

            val result = preparer.prepare(runtime(home, library))

            assertTrue(result is NativeJvmRuntimePreparationResult.Ready)
            assertEquals(
                listOf(leaf.canonicalPath, dependency.canonicalPath),
                received
            )
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
                    "OK: preflight passed"
                }),
                dependencyResolver = emptyDependencyResolver()
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
                }),
                dependencyResolver = emptyDependencyResolver()
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

    @Test
    fun unresolvedElfDependencyStopsBeforeNativeProbeOrLoad() {
        val root = Files.createTempDirectory("flame-jvm-unresolved-dep").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val library = File(home, "lib/server/libjvm.so").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1)) }
            val events = mutableListOf<String>()
            val resolver = JvmNativeDependencyResolver(
                JvmElfDynamicMetadataReader { JvmElfDynamicMetadata(listOf("libmissing-runtime.so")) }
            )
            val preparer = NativeJvmRuntimePreparer(
                probe = NativeJvmLibraryProbe { events += "probe"; "OK: probe" },
                loader = NativeJvmLibraryLoader(nativeRetainer = { events += "retain"; "OK: retained" }),
                dependencyResolver = resolver
            )

            val result = preparer.prepare(runtime(home, library))

            assertTrue(result is NativeJvmRuntimePreparationResult.Failed)
            assertTrue((result as NativeJvmRuntimePreparationResult.Failed).diagnostic.contains("libmissing-runtime.so"))
            assertTrue(events.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
    private fun emptyDependencyResolver() = JvmNativeDependencyResolver(
        JvmElfDynamicMetadataReader { JvmElfDynamicMetadata(emptyList()) }
    )

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
