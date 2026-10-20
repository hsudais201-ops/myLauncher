package com.voidcraft.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class MinecraftVersionCatalogTest {
    private val parser = MinecraftVersionManifestParser()
    private val releaseSha = "0123456789abcdef0123456789abcdef01234567"
    private val snapshotSha = "abcdef0123456789abcdef0123456789abcdef01"

    @Test
    fun parsesReleaseSnapshotAndLatestPointers() {
        val catalog = parser.parse(manifest())

        assertEquals("1.21.4", catalog.latest.release)
        assertEquals("24w14a", catalog.latest.snapshot)
        assertEquals(2, catalog.versions.size)
        assertEquals("release", catalog.find("1.21.4")?.type)
        assertEquals("snapshot", catalog.find("24w14a")?.type)
        assertNull(catalog.find("does-not-exist"))
    }

    @Test
    fun acceptsOldVersionIdsAndOptionalComplianceLevel() {
        val catalog = parser.parse(manifest(firstId = "b1.7.3", latestRelease = "b1.7.3", firstCompliance = false))
        assertEquals("b1.7.3", catalog.latest.release)
        assertNull(catalog.versions.first().complianceLevel)
    }

    @Test
    fun rejectsHttpAndUnapprovedMetadataHosts() {
        val insecure = manifest().replace(
            "https://piston-meta.mojang.com",
            "http://piston-meta.mojang.com"
        )
        assertThrows(IllegalArgumentException::class.java) { parser.parse(insecure) }

        val unapproved = manifest().replace(
            "https://piston-meta.mojang.com",
            "https://example.invalid"
        )
        assertThrows(IllegalArgumentException::class.java) { parser.parse(unapproved) }
    }

    @Test
    fun rejectsDuplicateIdsAndDanglingLatestReference() {
        val duplicate = """{"latest":{"release":"1.21.4","snapshot":"24w14a"},"versions":[""" +
            versionEntry("1.21.4", "release", releaseSha, "release.json", true) + "," +
            versionEntry("1.21.4", "snapshot", snapshotSha, "snapshot.json", true) + "]}"
        assertThrows(IllegalArgumentException::class.java) { parser.parse(duplicate) }
        assertThrows(IllegalArgumentException::class.java) { parser.parse(manifest(latestRelease = "2.0")) }
    }

    @Test
    fun rejectsInvalidHashTimestampAndInvalidJson() {
        val badHash = manifest().replace(releaseSha, "nope")
        assertThrows(IllegalArgumentException::class.java) { parser.parse(badHash) }

        val badTime = manifest().replace("2024-12-03T10:00:00Z", "not-a-time")
        assertThrows(IllegalArgumentException::class.java) { parser.parse(badTime) }

        assertThrows(IllegalArgumentException::class.java) { parser.parse("{ definitely not json") }
    }

    @Test
    fun parsesVersionDetailsAndClientDownload() {
        val clientSha = "fedcba9876543210fedcba9876543210fedcba98"
        val json = """
            {
              "id": "1.21.4",
              "type": "release",
              "mainClass": "net.minecraft.client.main.Main",
              "assets": "1.21",
              "assetIndex": {
                "id": "1.21",
                "url": "https://piston-meta.mojang.com/v1/packages/$releaseSha/asset-index.json",
                "sha1": "$releaseSha",
                "size": 2000,
                "totalSize": 300000
              },
              "downloads": {
                "client": {
                  "url": "https://piston-data.mojang.com/v1/objects/$clientSha/client.jar",
                  "sha1": "$clientSha",
                  "size": 28000000
                }
              },
              "javaVersion": { "majorVersion": 21 }
            }
        """.trimIndent()

        val details = MinecraftVersionDetailsParser().parse(json)
        assertEquals("1.21.4", details.id)
        assertEquals("net.minecraft.client.main.Main", details.mainClass)
        assertEquals("1.21", details.assetIndex?.id)
        assertEquals(clientSha, details.clientDownload?.sha1)
        assertEquals(28_000_000L, details.clientDownload?.sizeBytes)
        assertEquals(21, details.javaMajorVersion)
        assertNull(details.inheritsFrom)
    }

    @Test
    fun rejectsMismatchedVersionDetailIdInCatalogClient() {
        val json = """
            {
              "id": "1.20.1",
              "type": "release",
              "downloads": {
                "client": {
                  "url": "https://piston-data.mojang.com/client.jar",
                  "sha1": "$releaseSha",
                  "size": 10
                }
              }
            }
        """.trimIndent()
        val fetcher = HttpsTextFetcher { _, _ -> json }
        val client = MinecraftVersionCatalogClient(fetcher)
        val entry = MinecraftVersionEntry(
            id = "1.21.4",
            type = "release",
            metadataUrl = "https://piston-meta.mojang.com/version.json",
            updatedAt = "2024-12-03T10:00:00Z",
            releaseTime = "2024-12-03T10:00:00Z",
            sha1 = sha1(json),
            complianceLevel = 1
        )
        assertThrows(IllegalArgumentException::class.java) { client.fetchDetails(entry) }
    }

    @Test
    fun rejectsVersionDetailsWithUnexpectedSha1() {
        val json = """{"id":"1.21.4","type":"release"}"""
        val fetcher = HttpsTextFetcher { _, _ -> json }
        val client = MinecraftVersionCatalogClient(fetcher)
        val entry = MinecraftVersionEntry(
            id = "1.21.4",
            type = "release",
            metadataUrl = "https://piston-meta.mojang.com/version.json",
            updatedAt = "2024-12-03T10:00:00Z",
            releaseTime = "2024-12-03T10:00:00Z",
            sha1 = releaseSha,
            complianceLevel = 1
        )
        assertThrows(DownloadIntegrityException::class.java) { client.fetchDetails(entry) }
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun manifest(
        firstId: String = "1.21.4",
        latestRelease: String = "1.21.4",
        firstCompliance: Boolean = true
    ): String {
        val first = versionEntry(firstId, "release", releaseSha, "release.json", firstCompliance)
        val second = versionEntry("24w14a", "snapshot", snapshotSha, "snapshot.json", true)
        return """{"latest":{"release":"$latestRelease","snapshot":"24w14a"},"versions":[""" +
            first + "," + second + "]}"
    }

    private fun versionEntry(
        id: String,
        type: String,
        sha: String,
        fileName: String,
        includeCompliance: Boolean
    ): String {
        val compliance = if (includeCompliance) ",\"complianceLevel\":1" else ""
        return """{
          "id":"$id",
          "type":"$type",
          "url":"https://piston-meta.mojang.com/v1/packages/$sha/$fileName",
          "time":"2024-12-03T10:00:00Z",
          "releaseTime":"2024-12-03T10:00:00Z",
          "sha1":"$sha"$compliance
        }"""
    }
}
