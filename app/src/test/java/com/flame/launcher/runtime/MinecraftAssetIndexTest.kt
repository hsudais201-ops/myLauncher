package com.flame.launcher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MinecraftAssetIndexTest {
    private val hashA = "0123456789abcdef0123456789abcdef01234567"
    private val hashB = "abcdef0123456789abcdef0123456789abcdef01"

    @Test
    fun parsesIndexAndBuildsDeduplicatedHashAddressedDownloads() {
        val json = """
            {
              "id": "17",
              "totalSize": 300,
              "objects": {
                "minecraft/sounds/a.ogg": {"hash": "$hashA", "size": 100},
                "minecraft/textures/a.png": {"hash": "$hashB", "size": 200},
                "minecraft/sounds/copy.ogg": {"hash": "$hashA", "size": 100}
              }
            }
        """.trimIndent()
        val index = MinecraftAssetIndex.parse(json, "17")
        val plan = MinecraftAssetDownloadPlanBuilder.build(index)

        assertEquals("17", index.id)
        assertEquals(3, index.objects.size)
        assertEquals(300L, index.totalSize)
        assertEquals(2, plan.size)
        assertEquals(
            "assets/objects/01/$hashA",
            plan.first().relativePath
        )
        assertEquals(
            "https://resources.download.minecraft.net/01/$hashA",
            plan.first().descriptor.url
        )
        assertEquals(MinecraftPlannedDownloadKind.ASSET, plan.first().kind)
    }

    @Test
    fun rejectsWrongIndexIdInvalidHashesAndNegativeSizes() {
        val valid = """{"id":"17","objects":{"a":{"hash":"$hashA","size":1}}}"""
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftAssetIndex.parse(valid, "16")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftAssetIndex.parse(
                """{"id":"17","objects":{"a":{"hash":"bad","size":1}}}""",
                "17"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftAssetIndex.parse(
                """{"id":"17","objects":{"a":{"hash":"$hashA","size":-1}}}""",
                "17"
            )
        }
    }

    @Test
    fun rejectsMalformedAndOversizedIndexes() {
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftAssetIndex.parse("{", "17")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftAssetIndex.parse(" ".repeat(16 * 1024 * 1024 + 1), "17")
        }
    }

    @Test
    fun acceptsZeroByteAssetsAndEmptyObjectMaps() {
        val index = MinecraftAssetIndex.parse(
            """{"id":"17","objects":{"empty":{"hash":"$hashA","size":0}}}""",
            "17"
        )
        val plan = MinecraftAssetDownloadPlanBuilder.build(index)
        assertEquals(0L, plan.single().descriptor.size)
    }
}
