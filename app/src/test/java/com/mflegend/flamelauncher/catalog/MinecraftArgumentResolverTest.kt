package com.mflegend.flamelauncher.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftArgumentResolverTest {
    private val android = MinecraftLaunchRuleContext(
        platform = MinecraftLaunchPlatform.ANDROID,
        osVersion = "14",
        osArchitecture = "aarch64"
    )

    @Test
    fun keepsEachPlaceholderExpandedValueAsOneArgument() {
        val metadata = modernMetadata(
            """{"id":"test","mainClass":"net.minecraft.client.main.Main","arguments":{"game":["--username","${'$'}{auth_player_name}","--gameDir","${'$'}{game_directory}"],"jvm":[]}}"""
        )

        val result = MinecraftArgumentResolver.resolveGameArguments(
            metadata,
            "test",
            android,
            mapOf(
                "auth_player_name" to "Player One",
                "game_directory" to "/storage/emulated/0/My Game"
            )
        )

        assertEquals(listOf("--username", "Player One", "--gameDir", "/storage/emulated/0/My Game"), result)
    }

    @Test
    fun doesNotTreatAndroidAsDesktopLinux() {
        val metadata = modernMetadata(
            """{"id":"test","mainClass":"net.minecraft.client.main.Main","arguments":{"game":[{"rules":[{"action":"allow","os":{"name":"linux"}}],"value":["--linux-only"]},{"rules":[{"action":"allow","os":{"name":"windows"}}],"value":["--windows-only"]}]}}"""
        )

        val result = MinecraftArgumentResolver.resolveGameArguments(metadata, "test", android, emptyMap())

        assertTrue(result.isEmpty())
    }

    @Test
    fun evaluatesFeatureRulesAndLastMatchingRuleWins() {
        val metadata = modernMetadata(
            """{"id":"test","mainClass":"net.minecraft.client.main.Main","arguments":{"game":[{"rules":[{"action":"allow","os":{"name":"windows"}},{"action":"disallow","features":{"is_demo_user":true}}],"value":["--licensed-only"]}]}}"""
        )
        val windows = MinecraftLaunchRuleContext(
            platform = MinecraftLaunchPlatform.WINDOWS,
            features = mapOf("is_demo_user" to true)
        )

        val result = MinecraftArgumentResolver.resolveGameArguments(metadata, "test", windows, emptyMap())

        assertTrue(result.isEmpty())
    }

    @Test
    fun defaultsMissingFeaturesToFalse() {
        val metadata = modernMetadata(
            """{"id":"test","mainClass":"net.minecraft.client.main.Main","arguments":{"game":[{"rules":[{"action":"allow","features":{"has_custom_resolution":true}}],"value":["--width"]}]}}"""
        )

        val result = MinecraftArgumentResolver.resolveGameArguments(metadata, "test", android, emptyMap())

        assertTrue(result.isEmpty())
    }

    @Test
    fun acceptsArchitectureAndVersionRegexes() {
        val metadata = modernMetadata(
            """{"id":"test","mainClass":"net.minecraft.client.main.Main","arguments":{"game":[{"rules":[{"action":"allow","os":{"name":"osx","arch":"aarch64|arm64","version":"14\\..*"}}],"value":["--apple-arm"]}]}}"""
        )
        val mac = MinecraftLaunchRuleContext(
            platform = MinecraftLaunchPlatform.MAC_OS,
            osVersion = "14.5",
            osArchitecture = "aarch64"
        )

        val result = MinecraftArgumentResolver.resolveGameArguments(metadata, "test", mac, emptyMap())

        assertEquals(listOf("--apple-arm"), result)
    }

    @Test
    fun rejectsUnknownPlaceholderInsteadOfPassingItThrough() {
        val metadata = modernMetadata(
            """{"id":"test","mainClass":"net.minecraft.client.main.Main","arguments":{"game":["${'$'}{missing_value}"]}}"""
        )

        val error = runCatching {
            MinecraftArgumentResolver.resolveGameArguments(metadata, "test", android, emptyMap())
        }.exceptionOrNull()

        assertTrue(error != null)
    }

    @Test
    fun emptyRulesArrayAllowsConditionalArgumentValue() {
        val metadata = modernMetadata(
            """{"id":"test","mainClass":"net.minecraft.client.main.Main","arguments":{"game":[{"rules":[],"value":["--always"]}]}}"""
        )

        val result = MinecraftArgumentResolver.resolveGameArguments(metadata, "test", android, emptyMap())

        assertEquals(listOf("--always"), result)
    }

    @Test
    fun rejectsMalformedRuleRegex() {
        val metadata = modernMetadata(
            """{"id":"test","mainClass":"net.minecraft.client.main.Main","arguments":{"game":[{"rules":[{"action":"allow","os":{"arch":"["}}],"value":["--bad-regex"]}]}}"""
        )

        val error = runCatching {
            MinecraftArgumentResolver.resolveGameArguments(metadata, "test", android, emptyMap())
        }.exceptionOrNull()

        assertTrue(error != null)
    }

    private fun modernMetadata(json: String): ByteArray = json.toByteArray(Charsets.UTF_8)
}
