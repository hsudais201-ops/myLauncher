package com.voidcraft.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MinecraftVersionInheritanceArgumentsTest {
    @Test
    fun childGameArgumentsOverrideWhileMissingJvmArgumentsInherit() {
        val parent = details(
            id = "base",
            arguments = MinecraftVersionArguments(
                game = listOf(MinecraftArgumentValue(listOf("--parent-game"))),
                jvm = listOf(MinecraftArgumentValue(listOf("-Xmx2G")))
            ),
            legacy = "--parent-legacy"
        )
        val child = details(
            id = "child",
            inheritsFrom = "base",
            arguments = MinecraftVersionArguments(
                game = listOf(MinecraftArgumentValue(listOf("--child-game"))),
                jvm = null
            )
        )

        val merged = MinecraftVersionInheritanceMerger.merge(child, parent)

        assertEquals(listOf("--child-game"), merged.arguments?.game?.flatMap { it.values })
        assertEquals(listOf("-Xmx2G"), merged.arguments?.jvm?.flatMap { it.values })
        assertEquals("--parent-legacy", merged.legacyMinecraftArguments)
    }

    @Test
    fun explicitEmptyArgumentArrayOverridesParentButAbsentFieldInherits() {
        val parent = details(
            id = "base",
            arguments = MinecraftVersionArguments(
                game = listOf(MinecraftArgumentValue(listOf("--parent-game"))),
                jvm = listOf(MinecraftArgumentValue(listOf("-Xmx2G")))
            )
        )
        val child = details(
            id = "child",
            inheritsFrom = "base",
            arguments = MinecraftVersionArguments(game = emptyList(), jvm = null),
            legacy = "--child-legacy"
        )

        val merged = MinecraftVersionInheritanceMerger.merge(child, parent)

        assertEquals(emptyList<String>(), merged.arguments?.game)
        assertEquals(listOf("-Xmx2G"), merged.arguments?.jvm?.flatMap { it.values })
        assertEquals("--child-legacy", merged.legacyMinecraftArguments)
    }

    private fun details(
        id: String,
        inheritsFrom: String? = null,
        arguments: MinecraftVersionArguments? = null,
        legacy: String? = null
    ) = MinecraftVersionDetails(
        id = id,
        type = "release",
        mainClass = null,
        inheritsFrom = inheritsFrom,
        assets = null,
        assetIndex = null,
        clientDownload = null,
        javaMajorVersion = null,
        arguments = arguments,
        legacyMinecraftArguments = legacy
    )
}
