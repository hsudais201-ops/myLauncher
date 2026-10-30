package com.hyperlauncher.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftAssetIndexResolverTest {
    @Test
    fun parsesAssetsAndBuildsContentAddressedPaths() {
        val json = """
            {
              "id":"19",
              "objects":{
                "minecraft/sounds/random/click.ogg":{"hash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":1200},
                "minecraft/lang/en_us.json":{"hash":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","size":2400}
              }
            }
        """.trimIndent()

        val index = MinecraftAssetIndexResolver.parse("19", json)

        assertEquals(2, index.objects.size)
        assertEquals(3600L, index.totalBytes)
        val first = index.objects.first { it.hash.startsWith("aa") }
        assertEquals("objects/aa/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", first.relativePath)
        assertTrue(first.download.url.endsWith("/aa/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
    }

    @Test
    fun rejectsWrongIndexIdAndUnsafeOrInvalidObjects() {
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftAssetIndexResolver.parse("wrong", """{"id":"19","objects":{}}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftAssetIndexResolver.parse("19", """{"id":"19","objects":{"../escape":{"hash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":10}}}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftAssetIndexResolver.parse("19", """{"id":"19","objects":{"ok":{"hash":"bad","size":10}}}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftAssetIndexResolver.parse("19", """{"id":"19","objects":{"minecraft/../escape":{"hash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":10}}}""")
        }
    }
}
