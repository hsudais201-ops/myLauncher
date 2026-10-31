package com.hyperlauncher.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftVersionManifestTest {
    @Test
    fun parsesOfficialEntriesAndSkipsUnsafeMetadata() {
        val json = """
            {
              "versions": [
                {"id":"1.21.4","type":"release","releaseTime":"2024-12-03T00:00:00+00:00","url":"https://piston-meta.mojang.com/v1/packages/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/1.21.4.json","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
                {"id":"24w14a","type":"snapshot","releaseTime":"2024-04-02T00:00:00+00:00","url":"https://launchermeta.mojang.com/v1/packages/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/24w14a.json","sha1":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"},
                {"id":"../bad","type":"release","url":"https://piston-meta.mojang.com/bad","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
                {"id":"1.19.4","type":"old_beta","url":"https://piston-meta.mojang.com/old","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
                {"id":"1.20.1","type":"release","url":"http://evil.example/file","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
              ]
            }
        """.trimIndent()

        val versions = MinecraftVersionManifest.parseManifest(json)

        assertEquals(2, versions.size)
        assertEquals("1.21.4", versions[0].id)
        assertEquals("release", versions[0].type)
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", versions[0].metadataSha1)
        assertEquals("24w14a", versions[1].id)
    }

    @Test
    fun onlyAllowsHttpsMojangMetadataHosts() {
        assertTrue(MinecraftVersionManifest.isTrustedMojangUrl("https://piston-meta.mojang.com/file"))
        assertTrue(MinecraftVersionManifest.isTrustedMojangUrl("https://launchermeta.mojang.com/file"))
        assertFalse(MinecraftVersionManifest.isTrustedMojangUrl("http://piston-meta.mojang.com/file"))
        assertFalse(MinecraftVersionManifest.isTrustedMojangUrl("https://evil.example/file"))
    }

    @Test
    fun rejectsManifestWithNoUsableVersions() {
        assertThrows(IllegalStateException::class.java) {
            MinecraftVersionManifest.parseManifest("""{"versions":[{"id":"../bad","type":"release"}]}""")
        }
    }

    @Test
    fun latestReleaseAliasResolvesToFirstOfficialRelease() {
        val versions = listOf(
            MinecraftVersion("24w14a", "snapshot", "", "https://piston-meta.mojang.com/snapshot", "a".repeat(40)),
            MinecraftVersion("1.21.4", "release", "", "https://piston-meta.mojang.com/release", "b".repeat(40)),
            MinecraftVersion("1.21.3", "release", "", "https://piston-meta.mojang.com/older", "c".repeat(40))
        )
        assertEquals("1.21.4", MinecraftVersionManifest.findVersion(versions, "release")?.id)
        assertEquals("24w14a", MinecraftVersionManifest.findVersion(versions, "snapshot")?.id)
    }

    @Test
    fun explicitVersionIdDoesNotChangeToLatest() {
        val versions = listOf(
            MinecraftVersion("1.21.4", "release", "", "https://piston-meta.mojang.com/latest", "a".repeat(40)),
            MinecraftVersion("1.20.1", "release", "", "https://piston-meta.mojang.com/pinned", "b".repeat(40))
        )
        assertEquals("1.20.1", MinecraftVersionManifest.findVersion(versions, "1.20.1")?.id)
        assertEquals(null, MinecraftVersionManifest.findVersion(versions, "1.19.4"))
    }

}
