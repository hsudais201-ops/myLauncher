package com.voidcraft.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class MinecraftVersionInstallPreparationServiceTest {
    private val metadataSha = "0123456789abcdef0123456789abcdef01234567"
    private val assetObjectSha = "abcdef0123456789abcdef0123456789abcdef01"
    private val clientSha = "fedcba9876543210fedcba9876543210fedcba98"
    private val assetIndexJson =
        """{"objects":{"sounds/test.ogg":{"hash":"$assetObjectSha","size":128}}}"""
    private val assetIndexDescriptor = MinecraftAssetIndex(
        id = "1.20",
        url = "https://piston-meta.mojang.com/assets/1.20.json",
        sha1 = sha1(assetIndexJson),
        sizeBytes = assetIndexJson.toByteArray(StandardCharsets.UTF_8).size.toLong(),
        totalSizeBytes = 128L
    )

    @Test
    fun resolvesInheritanceFetchesVerifiedAssetIndexAndBuildsPlan() {
        val parent = entry("1.20.1")
        val child = entry("loader-child")
        val parentDetails = MinecraftVersionDetails(
            id = parent.id,
            type = "release",
            mainClass = "net.minecraft.client.main.Main",
            inheritsFrom = null,
            assets = "1.20",
            assetIndex = assetIndexDescriptor,
            clientDownload = MinecraftArtifactDownload(
                "https://piston-data.mojang.com/client.jar",
                clientSha,
                400L
            ),
            javaMajorVersion = 17,
            clientDownloadVersionId = parent.id
        )
        val childDetails = MinecraftVersionDetails(
            id = child.id,
            type = "release",
            mainClass = "com.example.loader.Main",
            inheritsFrom = parent.id,
            assets = null,
            assetIndex = null,
            clientDownload = null,
            javaMajorVersion = null
        )
        val source = FakeSource(
            entries = mapOf(parent.id to parent, child.id to child),
            details = mapOf(parent.id to parentDetails, child.id to childDetails)
        )
        val metadataClient = MinecraftVersionCatalogClient(
            fetcher = HttpsTextFetcher { url, _ ->
                if (url == assetIndexDescriptor.url) assetIndexJson
                else throw AssertionError("Unexpected metadata request: " + url)
            }
        )
        val service = MinecraftVersionInstallPreparationService(
            source,
            metadataClient,
            MinecraftInstallPlanner(MinecraftPlatformContext("linux", "x86_64"))
        )

        val prepared = service.prepare(child)

        assertEquals(child.id, prepared.requestedEntry.id)
        assertNullSafeEquals(null, prepared.resolvedDetails.inheritsFrom)
        assertEquals("com.example.loader.Main", prepared.resolvedDetails.mainClass)
        assertEquals(parent.id, prepared.resolvedDetails.clientDownloadVersionId)
        assertEquals(1, prepared.assetIndexContents?.objects?.size)
        assertNotNull(prepared.plan.downloads.firstOrNull {
            it.kind == MinecraftInstallPlanner.KIND_ASSET_OBJECT
        })
        assertEquals(
            "versions/1.20.1/1.20.1.jar",
            prepared.plan.downloads.single {
                it.kind == MinecraftInstallPlanner.KIND_CLIENT
            }.destinationRelativePath
        )
        assertFalse(prepared.hasBlockingMetadataWarnings)
        assertTrue(prepared.plan.warnings.isEmpty())
    }

    @Test
    fun reportsMissingInstallMetadataInsteadOfPretendingPlanIsComplete() {
        val entry = entry("1.20.1")
        val details = MinecraftVersionDetails(
            id = entry.id,
            type = "release",
            mainClass = null,
            inheritsFrom = null,
            assets = null,
            assetIndex = null,
            clientDownload = null,
            javaMajorVersion = null
        )
        val service = MinecraftVersionInstallPreparationService(
            source = FakeSource(
                entries = mapOf(entry.id to entry),
                details = mapOf(entry.id to details)
            ),
            metadataClient = MinecraftVersionCatalogClient(
                fetcher = HttpsTextFetcher { _, _ -> throw AssertionError("No asset index was declared") }
            ),
            planner = MinecraftInstallPlanner(MinecraftPlatformContext("linux", "x86_64"))
        )

        val prepared = service.prepare(entry)

        assertTrue(prepared.hasBlockingMetadataWarnings)
        assertTrue(prepared.plan.warnings.any { it.contains("client download") })
        assertTrue(prepared.plan.warnings.any { it.contains("asset index") })
    }

    @Test
    fun invalidAssetIndexHashFailsPreparation() {
        val entry = entry("1.20.1")
        val invalidDescriptor = assetIndexDescriptor.copy(sha1 = metadataSha)
        val details = MinecraftVersionDetails(
            id = entry.id,
            type = "release",
            mainClass = "example.Main",
            inheritsFrom = null,
            assets = "1.20",
            assetIndex = invalidDescriptor,
            clientDownload = MinecraftArtifactDownload(
                "https://piston-data.mojang.com/client.jar", clientSha, 400L
            ),
            javaMajorVersion = 17,
            clientDownloadVersionId = entry.id
        )
        val service = MinecraftVersionInstallPreparationService(
            source = FakeSource(mapOf(entry.id to entry), mapOf(entry.id to details)),
            metadataClient = MinecraftVersionCatalogClient(
                fetcher = HttpsTextFetcher { _, _ -> assetIndexJson }
            ),
            planner = MinecraftInstallPlanner(MinecraftPlatformContext("linux", "x86_64"))
        )

        assertThrows(DownloadIntegrityException::class.java) { service.prepare(entry) }
    }

    private fun entry(id: String) = MinecraftVersionEntry(
        id = id,
        type = "release",
        metadataUrl = "https://piston-meta.mojang.com/versions/" + id + ".json",
        updatedAt = "2025-01-01T00:00:00Z",
        releaseTime = "2025-01-01T00:00:00Z",
        sha1 = metadataSha,
        complianceLevel = 1
    )

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun assertNullSafeEquals(expected: String?, actual: String?) {
        assertEquals(expected, actual)
    }

    private class FakeSource(
        private val entries: Map<String, MinecraftVersionEntry>,
        private val details: Map<String, MinecraftVersionDetails>
    ) : MinecraftVersionMetadataSource {
        override fun findEntry(versionId: String): MinecraftVersionEntry? = entries[versionId]

        override fun fetchDetails(entry: MinecraftVersionEntry): MinecraftVersionDetails =
            details[entry.id] ?: throw AssertionError("Missing test details for " + entry.id)
    }
}
