package com.flame.launcher.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JavaRuntimeManagerTest {
    @Test
    fun discoversEmbeddedJvmAndResolvesRequiredMajorForDeviceAbi() {
        val root = Files.createTempDirectory("flame-runtimes").toFile()
        try {
            createEmbeddedRuntime(root, "temurin-17", "17.0.12+7", "aarch64")
            createEmbeddedRuntime(root, "temurin-21", "21.0.4+8", "aarch64")

            val manager = JavaRuntimeManager(root, "arm64-v8a")
            val discovered = manager.discover()
            assertEquals(listOf(17, 21), discovered.map { it.majorVersion }.sorted())

            val result = manager.resolve(17)
            assertTrue(result is JavaRuntimeResolution.Available)
            val selected = (result as JavaRuntimeResolution.Available).installation
            assertEquals("temurin-17", selected.id)
            assertEquals(JavaRuntimeExecutionKind.EMBEDDED_JVM, selected.executionKind)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun selectsNewestCompatibleRuntimeOfTheRequiredMajor() {
        val root = Files.createTempDirectory("flame-runtime-version").toFile()
        try {
            createEmbeddedRuntime(root, "jdk-17-old", "17.0.9+9", "aarch64")
            createEmbeddedRuntime(root, "jdk-17-new", "17.0.12+7", "aarch64")

            val result = JavaRuntimeManager(root, "aarch64").resolve(17)
            assertTrue(result is JavaRuntimeResolution.Available)
            assertEquals(
                "17.0.12+7",
                (result as JavaRuntimeResolution.Available).installation.version
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsRuntimeForDifferentArchitectureAndReportsAvailableMajors() {
        val root = Files.createTempDirectory("flame-runtime-abi").toFile()
        try {
            createEmbeddedRuntime(root, "x64-jdk-17", "17.0.12", "amd64")

            val result = JavaRuntimeManager(root, "arm64-v8a").resolve(17)
            assertTrue(result is JavaRuntimeResolution.Unavailable)
            val unavailable = result as JavaRuntimeResolution.Unavailable
            assertEquals("arm64", unavailable.deviceArchitecture)
            assertTrue(unavailable.availableMajors.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun supportsLegacyJavaEightVersionAndExecutableRuntime() {
        val root = Files.createTempDirectory("flame-runtime-java8").toFile()
        try {
            val home = File(root, "legacy-java-8").apply { mkdirs() }
            File(home, "release").writeText(
                "JAVA_VERSION=\"1.8.0_402\"\nOS_ARCH=\"arm64\"\n"
            )
            val java = File(home, "bin/java")
            java.parentFile?.mkdirs()
            java.writeText("runtime entry point placeholder")
            java.setExecutable(true)

            val result = JavaRuntimeManager(root, "aarch64").resolve(8)
            assertTrue(result is JavaRuntimeResolution.Available)
            val runtime = (result as JavaRuntimeResolution.Available).installation
            assertEquals(8, runtime.majorVersion)
            assertEquals(JavaRuntimeExecutionKind.PROCESS, runtime.executionKind)
            assertTrue(runtime.javaExecutable?.canExecute() == true)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun skipsMalformedRuntimeMetadataAndMissingJvmEntrypoints() {
        val root = Files.createTempDirectory("flame-runtime-invalid").toFile()
        try {
            File(root, "missing-entrypoint").apply {
                mkdirs()
                File(this, "release").writeText("JAVA_VERSION=\"17.0.12\"\nOS_ARCH=\"aarch64\"\n")
            }
            File(root, "bad-release").apply {
                mkdirs()
                File(this, "release").writeText("JAVA_VERSION=\"unknown\"\nOS_ARCH=\"aarch64\"\n")
                File(this, "lib/server/libjvm.so").apply {
                    parentFile?.mkdirs()
                    writeBytes(byteArrayOf(1))
                }
            }

            val discovered = JavaRuntimeManager(root, "aarch64").discover()
            assertTrue(discovered.isEmpty())
            assertFalse(JavaRuntimeManager(root, "aarch64").resolve(17) is JavaRuntimeResolution.Available)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsInvalidRequiredJavaMajor() {
        val root = Files.createTempDirectory("flame-runtime-required").toFile()
        try {
            val manager = JavaRuntimeManager(root, "arm64-v8a")
            assertTrue(runCatching { manager.resolve(0) }.isFailure)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun createEmbeddedRuntime(
        root: File,
        id: String,
        version: String,
        architecture: String
    ) {
        val home = File(root, id).apply { mkdirs() }
        File(home, "release").writeText(
            "JAVA_VERSION=\"$version\"\nOS_ARCH=\"$architecture\"\n"
        )
        File(home, "lib/server/libjvm.so").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
    }
}
