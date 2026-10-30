package com.hyperlauncher.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class MinecraftVersionDetailsResolverTest {
    @Test
    fun parsesClientAndAssetIndexDownloadMetadata() {
        val json = """
            {
              "id":"1.21.4",
              "mainClass":"net.minecraft.client.main.Main",
              "downloads":{"client":{"url":"https://piston-data.mojang.com/v1/objects/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/client.jar","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":123456}},
              "assetIndex":{"id":"19","url":"https://piston-meta.mojang.com/v1/packages/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/19.json","sha1":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","size":654321}
            }
        """.trimIndent()

        val details = MinecraftVersionDetailsResolver.parse("1.21.4", json)

        assertEquals("net.minecraft.client.main.Main", details.mainClass)
        assertEquals(123456L, details.client.size)
        assertEquals("19", details.assetIndex?.url?.substringAfterLast("/"))
    }

    @Test
    fun rejectsWrongVersionAndUntrustedDownloadHost() {
        val wrongVersion = """{"id":"1.20.1","mainClass":"Main","downloads":{"client":{"url":"https://piston-data.mojang.com/client.jar","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":1}}}"""
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftVersionDetailsResolver.parse("1.21.4", wrongVersion)
        }

        val unsafeHost = """{"id":"1.21.4","mainClass":"Main","downloads":{"client":{"url":"https://evil.example/client.jar","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":1}}}"""
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftVersionDetailsResolver.parse("1.21.4", unsafeHost)
        }
    }

    @Test
    fun validatesDownloadHosts() {
        assertTrue(MinecraftVersionDetailsResolver.isTrustedDownloadUrl("https://piston-data.mojang.com/client.jar"))
        assertTrue(MinecraftVersionDetailsResolver.isTrustedDownloadUrl("https://libraries.minecraft.net/library.jar"))
        assertFalse(MinecraftVersionDetailsResolver.isTrustedDownloadUrl("http://piston-data.mojang.com/client.jar"))
        assertFalse(MinecraftVersionDetailsResolver.isTrustedDownloadUrl("https://evil.example/client.jar"))
    }
}
