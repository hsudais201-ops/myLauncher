package com.flame.launcher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MinecraftVersionManifestTest {
    private val releaseSha = "0123456789abcdef0123456789abcdef01234567"
    private val snapshotSha = "abcdef0123456789abcdef0123456789abcdef01"

    private fun manifest(
        releaseUrl: String = "https://piston-meta.mojang.com/v1/packages/$releaseSha/1.21.json",
        releaseDigest: String = releaseSha,
        latestRelease: String = "1.21"
    ) = """
        {
          "latest": {"release": "$latestRelease", "snapshot": "24w14a"},
          "versions": [
            {
              "id": "1.21", "type": "release",
              "url": "$releaseUrl", "time": "2024-06-13T08:00:00+00:00",
              "releaseTime": "2024-06-13T08:00:00+00:00",
              "sha1": "$releaseDigest", "complianceLevel": 1
            },
            {
              "id": "24w14a", "type": "snapshot",
              "url": "https://piston-meta.mojang.com/v1/packages/$snapshotSha/24w14a.json",
              "time": "2024-04-03T12:00:00+00:00",
              "releaseTime": "2024-04-03T12:00:00+00:00",
              "sha1": "$snapshotSha", "complianceLevel": 1
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesLatestVersionsAndPreservesMetadata() {
        val parsed = MinecraftVersionManifest.parse(manifest())
        assertEquals("1.21", parsed.latestRelease)
        assertEquals("24w14a", parsed.latestSnapshot)
        assertEquals(2, parsed.versions.size)
        assertEquals("release", parsed.findVersion("1.21")?.type)
        assertEquals(releaseSha, parsed.findVersion("1.21")?.sha1)
        assertNotNull(parsed.findVersion("24w14a"))
        assertNull(parsed.findVersion("missing"))
    }

    @Test
    fun rejectsManifestThatExceedsParserSizeLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftVersionManifest.parse(" ".repeat(4 * 1024 * 1024 + 1))
        }
    }

    @Test
    fun rejectsMalformedJsonAndMissingLatestVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftVersionManifest.parse("{not-json")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftVersionManifest.parse("""{"latest":{},"versions":[]}""")
        }
    }

    @Test
    fun rejectsUntrustedMetadataHostAndNonHttpsUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftVersionManifest.parse(
                manifest(releaseUrl = "https://attacker.example/version.json")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftVersionManifest.parse(
                manifest(releaseUrl = "http://piston-meta.mojang.com/version.json")
            )
        }
    }

    @Test
    fun rejectsInvalidDigestAndDuplicateVersionIds() {
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftVersionManifest.parse(manifest(releaseDigest = "not-a-sha1"))
        }
        val duplicate = manifest().replace(
            """"id": "24w14a"""",
            """"id": "1.21""""
        )
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftVersionManifest.parse(duplicate)
        }
    }

    @Test
    fun rejectsLatestReleaseThatIsNotAReleaseEntry() {
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftVersionManifest.parse(manifest(latestRelease = "24w14a"))
        }
    }
}
