package com.voidcraft.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MinecraftLaunchArgumentsTest {
    @Test
    fun parsesModernGameJvmAndLegacyArgumentsAndFiltersRules() {
        val json = """
            {
              "id": "1.20.1",
              "type": "release",
              "minecraftArguments": "--username player --version legacy",
              "arguments": {
                "game": [
                  "--username",
                  {
                    "rules": [
                      { "action": "allow" },
                      { "action": "disallow", "os": { "name": "windows" } }
                    ],
                    "value": ["--platform", "selected"]
                  },
                  {
                    "rules": [{ "action": "allow", "os": { "name": "windows" } }],
                    "value": "--windows-only"
                  }
                ],
                "jvm": [
                  "-Xmx2G",
                  {
                    "rules": [{ "action": "allow", "features": { "is_demo_user": false } }],
                    "value": ["-Dfoo=bar", "-Dbar=baz"]
                  }
                ]
              }
            }
        """.trimIndent()

        val details = MinecraftVersionDetailsParser().parse(json)

        assertEquals("--username player --version legacy", details.legacyMinecraftArguments)
        assertEquals(3, details.arguments?.game?.size)
        assertEquals(2, details.arguments?.jvm?.size)
        assertEquals(listOf("--username", "--platform", "selected"),
            MinecraftLaunchArgumentEvaluator().flatten(
                details.arguments?.game,
                MinecraftPlatformContext("linux", "x86_64", "6.8")
            ))
        assertEquals(listOf("--username", "--windows-only"),
            MinecraftLaunchArgumentEvaluator().flatten(
                details.arguments?.game,
                MinecraftPlatformContext("windows", "amd64", "10.0")
            ))
        assertEquals(listOf("-Xmx2G", "-Dfoo=bar", "-Dbar=baz"),
            MinecraftLaunchArgumentEvaluator().flatten(
                details.arguments?.jvm,
                MinecraftPlatformContext("linux", "x86_64", "6.8")
            ))
    }

    @Test
    fun supportsLegacyOnlyMetadataAndPreservesArgumentTextExactly() {
        val value = "--username \"player one\" --version old"
        val json = """{"id":"1.7.10","type":"release","minecraftArguments":"$value"}"""
        val details = MinecraftVersionDetailsParser().parse(json)

        assertNull(details.arguments)
        assertEquals(value, details.legacyMinecraftArguments)
    }

    @Test
    fun rejectsMalformedModernArgumentEntriesAndRules() {
        val missingValue = """
            {"id":"1.20.1","type":"release","arguments":{"game":[{"rules":[]}]}}
        """.trimIndent()
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftVersionDetailsParser().parse(missingValue)
        }

        val wrongValueType = """
            {"id":"1.20.1","type":"release","arguments":{"jvm":[{"value":[1,2]}]}}
        """.trimIndent()
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftVersionDetailsParser().parse(wrongValueType)
        }

        val wrongRulesType = """
            {"id":"1.20.1","type":"release","arguments":{"game":[{"value":"--flag","rules":"allow"}]}}
        """.trimIndent()
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftVersionDetailsParser().parse(wrongRulesType)
        }
    }

    @Test
    fun rejectsNonArrayGameOrJvmArgumentFields() {
        val json = """{"id":"1.20.1","type":"release","arguments":{"game":"--flag"}}"""
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftVersionDetailsParser().parse(json)
        }
    }
}
