package com.mflegend.flamelauncher.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetLogicalPathPolicyTest {
    @Test
    fun acceptsNormalMinecraftAssetNames() {
        assertEquals(
            listOf("minecraft", "sounds", "ambient", "cave.ogg"),
            AssetLogicalPathPolicy.segments("minecraft/sounds/ambient/cave.ogg")
        )
        assertEquals(
            listOf("icons", "icon_16x16.png"),
            AssetLogicalPathPolicy.segments("icons/icon_16x16.png")
        )
    }

    @Test
    fun rejectsTraversalAndAbsolutePaths() {
        listOf(
            "../outside",
            "minecraft/../../outside",
            "/absolute/path",
            "minecraft/./file",
            "minecraft//file"
        ).forEach(::assertRejected)
    }

    @Test
    fun rejectsSeparatorsAndInvalidCharacters() {
        listOf(
            "minecraft\\sounds\\file.ogg",
            "minecraft/sounds/name with spaces.ogg",
            "minecraft/sounds/file?query",
            "minecraft/sounds/file#fragment",
            "minecraft/" + 0.toChar() + "file"
        ).forEach(::assertRejected)
    }

    @Test
    fun rejectsOverlongAndExcessivelyDeepPaths() {
        assertRejected("a".repeat(1025))
        assertRejected("a".repeat(256))
        assertRejected((1..33).joinToString("/") { "segment" })
    }


    @Test
    fun rejectsDuplicatePathsThatDifferOnlyByCase() {
        val paths = listOf(
            AssetLogicalPathPolicy.segments("minecraft/sounds/cave.ogg"),
            AssetLogicalPathPolicy.segments("Minecraft/Sounds/CAVE.ogg")
        )
        assertTrue(runCatching { AssetLogicalPathPolicy.validateUnique(paths) }
            .exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun rejectsFileAndDirectoryPrefixConflicts() {
        val paths = listOf(
            AssetLogicalPathPolicy.segments("minecraft/sounds"),
            AssetLogicalPathPolicy.segments("minecraft/sounds/cave.ogg")
        )
        assertTrue(runCatching { AssetLogicalPathPolicy.validateUnique(paths) }
            .exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun detectsPrefixConflictsEvenWhenAnotherNameSortsBetweenThem() {
        val paths = listOf(
            AssetLogicalPathPolicy.segments("asset"),
            AssetLogicalPathPolicy.segments("asset-file"),
            AssetLogicalPathPolicy.segments("asset/file")
        )
        assertTrue(runCatching { AssetLogicalPathPolicy.validateUnique(paths) }
            .exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun acceptsDistinctSiblingPaths() {
        AssetLogicalPathPolicy.validateUnique(listOf(
            AssetLogicalPathPolicy.segments("minecraft/sounds/cave.ogg"),
            AssetLogicalPathPolicy.segments("minecraft/sounds/rain.ogg")
        ))
    }

    private fun assertRejected(path: String) {
        assertTrue(
            "Expected unsafe asset path to be rejected",
            runCatching { AssetLogicalPathPolicy.segments(path) }.exceptionOrNull() is IllegalArgumentException
        )
    }
}
