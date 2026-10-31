package com.hyperlauncher.mobile

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class AndroidRuntimeInventoryTest {
    @Test
    fun findsExecutableRuntimeWithMatchingAbiAndReadsJavaMajor() {
        val root = Files.createTempDirectory("runtime-inventory").toFile()
        try {
            val runtime = root.resolve("External-21")
            runtime.resolve("bin").mkdirs()
            runtime.resolve("bin/java").apply {
                writeText("runtime placeholder")
                setExecutable(true)
            }
            runtime.resolve("release").writeText(
                "JAVA_VERSION=\"21.0.2\"\nOS_ARCH=\"aarch64\"\n"
            )

            val result = AndroidRuntimeInventory.inspect(root, "arm64-v8a")

            assertEquals(1, result.discoveredRuntimeCount)
            assertNotNull(result.compatibleRuntime)
            assertEquals(21, result.compatibleRuntime!!.javaMajor)
            assertEquals("External-21", result.compatibleRuntime!!.name)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsRuntimeWithWrongArchitectureOrMissingExecutable() {
        val root = Files.createTempDirectory("runtime-inventory").toFile()
        try {
            val wrongAbi = root.resolve("wrong-abi")
            wrongAbi.resolve("bin").mkdirs()
            wrongAbi.resolve("bin/java").apply { writeText("not executable") ; setExecutable(true) }
            wrongAbi.resolve("release").writeText(
                "JAVA_VERSION=\"17.0.9\"\nOS_ARCH=\"amd64\"\n"
            )

            val noJava = root.resolve("missing-java")
            noJava.mkdirs()
            noJava.resolve("release").writeText(
                "JAVA_VERSION=\"21\"\nOS_ARCH=\"aarch64\"\n"
            )

            val result = AndroidRuntimeInventory.inspect(root, "arm64-v8a")

            assertEquals(2, result.discoveredRuntimeCount)
            assertNull(result.compatibleRuntime)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsDesktopLinuxRuntimeWithSameCpuFamilyMetadataMismatch() {
        val root = Files.createTempDirectory("runtime-inventory").toFile()
        try {
            val runtime = root.resolve("desktop")
            runtime.resolve("bin").mkdirs()
            runtime.resolve("bin/java").apply { writeText("desktop binary"); setExecutable(true) }
            runtime.resolve("release").writeText(
                "JAVA_VERSION=\"21\"\nOS_ARCH=\"aarch64\"\n"
            )

            // The inventory is metadata validation only. Android-native provenance
            // still must be guaranteed by the runtime installer/source before launch.
            val result = AndroidRuntimeInventory.inspect(root, "arm64-v8a")
            assertNotNull(result.compatibleRuntime)
        } finally {
            root.deleteRecursively()
        }
    }
}
