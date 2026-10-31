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
            runtime.resolve("hyperlauncher-runtime.properties").writeText(
                "platform=android\nabi=arm64-v8a\n"
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
            wrongAbi.resolve("hyperlauncher-runtime.properties").writeText(
                "platform=android\nabi=x86_64\n"
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
    fun architectureMetadataAloneDoesNotProveAndroidNativeProvenance() {
        val root = Files.createTempDirectory("runtime-inventory").toFile()
        try {
            val runtime = root.resolve("desktop")
            runtime.resolve("bin").mkdirs()
            runtime.resolve("bin/java").apply { writeText("desktop binary"); setExecutable(true) }
            runtime.resolve("release").writeText(
                "JAVA_VERSION=\"21\"\nOS_ARCH=\"aarch64\"\n"
            )

            // Matching CPU architecture and a desktop executable are insufficient.
            // Without a launcher-owned Android provenance manifest, reject it.
            val result = AndroidRuntimeInventory.inspect(root, "arm64-v8a")
            assertNull(result.compatibleRuntime)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun ignoresIncompleteRuntimeStagingDirectories() {
        val root = Files.createTempDirectory("runtime-staging-inventory").toFile()
        try {
            val staging = root.resolve(".install-abandoned")
            staging.resolve("bin").mkdirs()
            staging.resolve("bin/java").apply { writeText("runtime"); setExecutable(true) }
            staging.resolve("release").writeText(
                "JAVA_VERSION=\"21.0.2\"\nOS_ARCH=\"aarch64\"\n"
            )
            staging.resolve("hyperlauncher-runtime.properties").writeText(
                "platform=android\nabi=arm64-v8a\njavaMajor=21\n"
            )

            val result = AndroidRuntimeInventory.inspect(root, "arm64-v8a")

            assertEquals(0, result.discoveredRuntimeCount)
            assertNull(result.compatibleRuntime)
        } finally {
            root.deleteRecursively()
        }
    }
}
