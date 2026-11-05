package com.flame.launcher.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftContentCompatibilityTest {
    private val target = MinecraftInstanceTarget(
        minecraftVersion = "1.20.1",
        loader = MinecraftLoader.FABRIC,
        contentDirectory = "/instances/survival"
    )

    private fun version(
        gameVersions: Set<String> = setOf("1.20.1"),
        loaders: Set<MinecraftLoader> = setOf(MinecraftLoader.FABRIC)
    ) = MinecraftContentVersion(
        provider = ContentProviderId.MODRINTH,
        projectId = "example-project",
        versionId = "example-version",
        versionNumber = "1.0.0",
        displayName = "Example",
        gameVersions = gameVersions,
        loaders = loaders,
        fileName = "example.jar",
        downloadUrl = "https://example.invalid/example.jar"
    )

    @Test
    fun compatibleVersionPasses() {
        assertEquals(
            ContentCompatibility.Compatible,
            MinecraftContentCompatibility.check(
                MinecraftContentType.MOD,
                version(),
                target
            )
        )
    }

    @Test
    fun wrongMinecraftVersionIsRejected() {
        val result = MinecraftContentCompatibility.check(
            MinecraftContentType.MOD,
            version(gameVersions = setOf("1.19.4")),
            target
        )
        assertTrue(result is ContentCompatibility.Incompatible)
        assertTrue((result as ContentCompatibility.Incompatible).reasons
            .any { it.contains("Minecraft 1.20.1") })
    }

    @Test
    fun wrongLoaderIsRejected() {
        val result = MinecraftContentCompatibility.check(
            MinecraftContentType.MOD,
            version(loaders = setOf(MinecraftLoader.FORGE)),
            target
        )
        assertTrue(result is ContentCompatibility.Incompatible)
    }

    @Test
    fun datapackRequiresSelectedWorld() {
        val result = MinecraftContentCompatibility.check(
            MinecraftContentType.DATAPACK,
            version(),
            target
        )
        assertTrue(result is ContentCompatibility.Incompatible)
        assertTrue((result as ContentCompatibility.Incompatible).reasons
            .any { it.contains("Select a world") })
    }

    @Test
    fun datapackPassesWhenWorldIsSelected() {
        val result = MinecraftContentCompatibility.check(
            MinecraftContentType.DATAPACK,
            version(),
            target.copy(selectedWorldDirectory = "/worlds/survival")
        )
        assertEquals(ContentCompatibility.Compatible, result)
    }
}
