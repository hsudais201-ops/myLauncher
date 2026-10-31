package com.hyperlauncher.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class MinecraftLaunchArgumentResolverTest {
    @Test
    fun resolvesSimpleAndStructuredGameArgumentsWithoutJoiningTokens() {
        val json = """{
          "arguments":{"game":[
            "--username","Player One",
            {"rules":[{"action":"allow","os":{"name":"linux"}}],"value":["--demo","${assets_root}"]},
            {"rules":[{"action":"allow","os":{"name":"windows"}}],"value":"--windows-only"}
          ]}
        }"""
        val args = MinecraftLaunchArgumentResolver.resolve(
            json, "game", "linux", "aarch64", "Android", placeholders = mapOf("assets_root" to "/game assets")
        )
        assertEquals(listOf("--username", "Player One", "--demo", "/game assets"), args)
    }

    @Test
    fun resolvesJvmArgumentsAndPreservesUnresolvedRuntimePlaceholders() {
        val json = """{"arguments":{"jvm":[
          "-Xmx2G",
          {"rules":[{"action":"allow","os":{"name":"linux"}}],"value":["-Djava.library.path=${natives_directory}"]}
        ]}}"""
        val args = MinecraftLaunchArgumentResolver.resolve(
            json, "jvm", "linux", "aarch64", "Android",
            placeholders = mapOf("natives_directory" to "/data/natives")
        )
        assertEquals(listOf("-Xmx2G", "-Djava.library.path=/data/natives"), args)
    }

    @Test
    fun honorsFeatureRulesAndLegacyArguments() {
        val json = """
          {"arguments":{"game":[
            {"rules":[{"action":"allow","features":{"is_demo_user":true}}],"value":"--demo"},
            {"rules":[{"action":"allow","features":{"has_custom_resolution":true}}],"value":"--width"}
          ]}}
        """
        val args = MinecraftLaunchArgumentResolver.resolve(
            json, "game", "linux", "aarch64", "Android",
            features = mapOf("is_demo_user" to true, "has_custom_resolution" to false)
        )
        assertEquals(listOf("--demo"), args)
        val legacy = MinecraftLaunchArgumentResolver.resolve(
            """{"minecraftArguments":"--username Player --gameDir .minecraft"}""",
            "game", "linux", "x86_64", "Linux"
        )
        assertEquals(listOf("--username", "Player", "--gameDir", ".minecraft"), legacy)
    }
}
