package com.flame.launcher.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JvmNativeDependencyResolverTest {
    @Test
    fun resolvesRecursiveRuntimeDependenciesAndApprovedAndroidLibraries() {
        val root = Files.createTempDirectory("flame-elf-deps").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val lib = File(home, "lib").apply { mkdirs() }
            val vm = File(lib, "server").apply { mkdirs() }
            val jvm = File(vm, "libjvm.so").apply { writeBytes(byteArrayOf(1)) }
            val verify = File(lib, "libverify.so").apply { writeBytes(byteArrayOf(2)) }
            val zip = File(lib, "libzip.so").apply { writeBytes(byteArrayOf(3)) }
            val metadata = mapOf(
                jvm.canonicalFile to listOf("libverify.so", "libc.so"),
                verify.canonicalFile to listOf("libzip.so"),
                zip.canonicalFile to emptyList()
            )
            val resolver = JvmNativeDependencyResolver(
                JvmElfDynamicMetadataReader { file ->
                    JvmElfDynamicMetadata(metadata[file.canonicalFile].orEmpty())
                }
            )

            val result = resolver.resolve(jvm, home, listOf(lib, vm))

            assertTrue(result is JvmNativeDependencyResolution.Resolved)
            val resolved = result as JvmNativeDependencyResolution.Resolved
            assertEquals(verify.canonicalFile, resolved.runtimeLibraries["libverify.so"])
            assertEquals(zip.canonicalFile, resolved.runtimeLibraries["libzip.so"])
            assertEquals(setOf("libc.so"), resolved.approvedSystemLibraries)
            assertEquals(listOf(zip.canonicalFile, verify.canonicalFile), resolved.runtimeLoadOrder)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun ordersSharedDependenciesOnceAndBeforeTheirDependents() {
        val root = Files.createTempDirectory("flame-elf-dependency-order").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val lib = File(home, "lib").apply { mkdirs() }
            val vm = File(lib, "server").apply { mkdirs() }
            val jvm = File(vm, "libjvm.so").apply { writeBytes(byteArrayOf(1)) }
            val first = File(lib, "libfirst.so").apply { writeBytes(byteArrayOf(2)) }
            val second = File(lib, "libsecond.so").apply { writeBytes(byteArrayOf(3)) }
            val shared = File(lib, "libshared.so").apply { writeBytes(byteArrayOf(4)) }
            val metadata = mapOf(
                jvm.canonicalFile to listOf("libfirst.so", "libsecond.so"),
                first.canonicalFile to listOf("libshared.so"),
                second.canonicalFile to listOf("libshared.so"),
                shared.canonicalFile to emptyList()
            )
            val resolver = JvmNativeDependencyResolver(
                JvmElfDynamicMetadataReader { file ->
                    JvmElfDynamicMetadata(metadata[file.canonicalFile].orEmpty())
                }
            )

            val result = resolver.resolve(jvm, home, listOf(lib, vm))

            assertTrue(result is JvmNativeDependencyResolution.Resolved)
            val order = (result as JvmNativeDependencyResolution.Resolved).runtimeLoadOrder
            assertEquals(3, order.size)
            assertEquals(shared.canonicalFile, order.first())
            assertTrue(order.indexOf(first.canonicalFile) > order.indexOf(shared.canonicalFile))
            assertTrue(order.indexOf(second.canonicalFile) > order.indexOf(shared.canonicalFile))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun reportsUnresolvedDependencyWithoutAttemptingToLoadAnything() {
        val root = Files.createTempDirectory("flame-elf-missing").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val vm = File(home, "lib/server").apply { mkdirs() }
            val jvm = File(vm, "libjvm.so").apply { writeBytes(byteArrayOf(1)) }
            val resolver = JvmNativeDependencyResolver(
                JvmElfDynamicMetadataReader {
                    JvmElfDynamicMetadata(listOf("libnot-present.so"))
                }
            )

            val result = resolver.resolve(jvm, home, listOf(vm))

            assertTrue(result is JvmNativeDependencyResolution.Unresolved)
            assertEquals(
                "libnot-present.so",
                (result as JvmNativeDependencyResolution.Unresolved).library
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsPathTraversalAndAbsoluteDependencyNames() {
        val root = Files.createTempDirectory("flame-elf-invalid-name").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val vm = File(home, "lib/server").apply { mkdirs() }
            val jvm = File(vm, "libjvm.so").apply { writeBytes(byteArrayOf(1)) }

            listOf("../libc.so", "/tmp/evil.so", "nested/libevil.so").forEach { name ->
                val resolver = JvmNativeDependencyResolver(
                    JvmElfDynamicMetadataReader { JvmElfDynamicMetadata(listOf(name)) }
                )
                val result = resolver.resolve(jvm, home, listOf(vm))
                assertTrue(
                    "Expected rejection for $name",
                    result is JvmNativeDependencyResolution.Rejected
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsDuplicateRuntimeDependencyCandidates() {
        val root = Files.createTempDirectory("flame-elf-ambiguous").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val first = File(home, "lib").apply { mkdirs() }
            val second = File(home, "lib/server").apply { mkdirs() }
            val jvm = File(second, "libjvm.so").apply { writeBytes(byteArrayOf(1)) }
            File(first, "libverify.so").writeBytes(byteArrayOf(2))
            File(second, "libverify.so").writeBytes(byteArrayOf(3))
            val resolver = JvmNativeDependencyResolver(
                JvmElfDynamicMetadataReader { JvmElfDynamicMetadata(listOf("libverify.so")) }
            )

            val result = resolver.resolve(jvm, home, listOf(first, second))

            assertTrue(result is JvmNativeDependencyResolution.Rejected)
            assertTrue(
                (result as JvmNativeDependencyResolution.Rejected)
                    .diagnostic.contains("multiple runtime files")
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun doesNotTrustRpathOrRunpathAsSearchDirectories() {
        val root = Files.createTempDirectory("flame-elf-rpath-untrusted").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val vm = File(home, "lib/server").apply { mkdirs() }
            val external = File(root, "external").apply { mkdirs() }
            val jvm = File(vm, "libjvm.so").apply { writeBytes(byteArrayOf(1)) }
            File(external, "liboutside.so").writeBytes(byteArrayOf(2))
            val resolver = JvmNativeDependencyResolver(
                JvmElfDynamicMetadataReader {
                    JvmElfDynamicMetadata(
                        neededLibraries = listOf("liboutside.so"),
                        rpathEntries = listOf(external.absolutePath),
                        runpathEntries = listOf("\$ORIGIN/../../external")
                    )
                }
            )

            val result = resolver.resolve(jvm, home, listOf(vm))

            assertTrue(result is JvmNativeDependencyResolution.Unresolved)
            assertEquals("liboutside.so", (result as JvmNativeDependencyResolution.Unresolved).library)
        } finally {
            root.deleteRecursively()
        }
    }
    @Test
    fun rejectsSearchDirectoriesOutsideRuntimeHome() {
        val root = Files.createTempDirectory("flame-elf-outside").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val outside = File(root, "outside").apply { mkdirs() }
            val vm = File(home, "lib/server").apply { mkdirs() }
            val jvm = File(vm, "libjvm.so").apply { writeBytes(byteArrayOf(1)) }
            val resolver = JvmNativeDependencyResolver(
                JvmElfDynamicMetadataReader { JvmElfDynamicMetadata(emptyList()) }
            )

            val result = resolver.resolve(jvm, home, listOf(outside))

            assertTrue(result is JvmNativeDependencyResolution.Rejected)
        } finally {
            root.deleteRecursively()
        }
    }
}
