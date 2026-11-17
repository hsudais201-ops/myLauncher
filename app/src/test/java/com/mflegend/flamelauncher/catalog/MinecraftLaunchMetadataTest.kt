package com.mflegend.flamelauncher.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftLaunchMetadataTest {
    @Test
    fun parsesModernLaunchArgumentsAndJavaRequirement() {
        val json = """
            {
              "id": "1.21.4",
              "mainClass": "net.minecraft.client.main.Main",
              "javaVersion": {"component": "java-runtime-gamma", "majorVersion": 21},
              "arguments": {
                "game": ["--username", {"rules": [{"action": "allow"}], "value": ["demo", "player"]}],
                "jvm": ["-Xmx2G"]
              }
            }
        """.trimIndent()

        val result = MinecraftLaunchMetadataParser.parse(json.toByteArray(), "1.21.4")

        assertEquals("net.minecraft.client.main.Main", result.mainClass)
        assertEquals(21, result.requiredJavaMajorVersion)
        assertEquals("java-runtime-gamma", result.javaRuntimeComponent)
        assertTrue(result.hasModernGameArguments)
        assertFalse(result.hasLegacyGameArguments)
    }

    @Test
    fun parsesLegacyArgumentsWithoutInventingJavaRequirement() {
        val json = """
            {
              "id": "1.12.2",
              "mainClass": "net.minecraft.client.main.Main",
              "minecraftArguments": "--username Offline --version 1.12.2"
            }
        """.trimIndent()

        val result = MinecraftLaunchMetadataParser.parse(json.toByteArray(), "1.12.2")

        assertEquals(null, result.requiredJavaMajorVersion)
        assertEquals(null, result.javaRuntimeComponent)
        assertFalse(result.hasModernGameArguments)
        assertTrue(result.hasLegacyGameArguments)
    }

    @Test
    fun rejectsMismatchedVersionId() {
        val json = """{"id":"1.20.1","mainClass":"net.minecraft.client.main.Main","minecraftArguments":"--demo"}"""

        val error = runCatching {
            MinecraftLaunchMetadataParser.parse(json.toByteArray(), "1.20.2")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun rejectsInvalidJavaMajorVersion() {
        val json = """
            {
              "id": "test",
              "mainClass": "net.minecraft.client.main.Main",
              "javaVersion": {"component": "java-runtime", "majorVersion": 0},
              "minecraftArguments": "--demo"
            }
        """.trimIndent()

        val error = runCatching {
            MinecraftLaunchMetadataParser.parse(json.toByteArray(), "test")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun rejectsMalformedConditionalArgumentValues() {
        val json = """
            {
              "id": "test",
              "mainClass": "net.minecraft.client.main.Main",
              "arguments": {"game": [{"value": 42}]}
            }
        """.trimIndent()

        val error = runCatching {
            MinecraftLaunchMetadataParser.parse(json.toByteArray(), "test")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun rejectsMissingMainClass() {
        val json = """{"id":"test","minecraftArguments":"--demo"}"""

        val error = runCatching {
            MinecraftLaunchMetadataParser.parse(json.toByteArray(), "test")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
