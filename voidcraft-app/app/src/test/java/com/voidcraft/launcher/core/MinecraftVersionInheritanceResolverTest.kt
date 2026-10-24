package com.voidcraft.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftVersionInheritanceResolverTest {
    private val sha = "0123456789abcdef0123456789abcdef01234567"
    private val clientHash = "abcdef0123456789abcdef0123456789abcdef01"
    private val assetHash = "fedcba9876543210fedcba9876543210fedcba98"

    @Test
    fun recursivelyResolvesParentsAndMergesChildOverridesSafely() {
        val base = entry("1.20.1")
        val parent = entry("loader-parent")
        val child = entry("loader-child")

        val baseShared = library("org.example:shared:1.0", "base")
        val childShared = library("org.example:shared:1.0", "child")
        val baseDetails = details(
            id = base.id,
            mainClass = "net.minecraft.client.main.Main",
            assets = "1.20",
            assetIndex = assetIndex("base-assets"),
            hasClient = true,
            javaMajor = 17,
            libraries = listOf(baseShared, library("org.example:base:1.0"))
        )
        val parentDetails = details(
            id = parent.id,
            inheritsFrom = base.id,
            libraries = listOf(library("org.example:parent:1.0"))
        )
        val childDetails = details(
            id = child.id,
            inheritsFrom = parent.id,
            mainClass = "com.example.loader.Main",
            libraries = listOf(childShared, library("org.example:child:1.0"))
        )
        val source = FakeSource(
            entries = listOf(base, parent, child).associateBy { it.id },
            details = listOf(baseDetails, parentDetails, childDetails).associateBy { it.id }
        )

        val resolved = MinecraftVersionInheritanceResolver(source).resolve(child)

        assertEquals(child.id, resolved.id)
        assertNull(resolved.inheritsFrom)
        assertEquals("com.example.loader.Main", resolved.mainClass)
        assertEquals("1.20", resolved.assets)
        assertEquals("base-assets", resolved.assetIndex?.id)
        assertEquals(17, resolved.javaMajorVersion)
        assertEquals(baseDetails.clientDownload, resolved.clientDownload)
        assertEquals(base.id, resolved.clientDownloadVersionId)
        assertEquals(
            setOf(
                "org.example:shared:1.0",
                "org.example:base:1.0",
                "org.example:parent:1.0",
                "org.example:child:1.0"
            ),
            resolved.libraries.map { it.name }.toSet()
        )
        assertEquals("https://libraries.minecraft.net/child/", resolved.libraries.first { it.name == "org.example:shared:1.0" }.urlBase)
        assertEquals(listOf(child.id, parent.id, base.id), source.fetchOrder)
    }

    @Test
    fun plannerStoresInheritedClientInParentVersionDirectory() {
        val base = entry("1.20.1")
        val child = entry("loader-child")
        val baseDetails = details(
            id = base.id,
            mainClass = "base.Main",
            assets = "1.20",
            assetIndex = assetIndex("base-assets"),
            hasClient = true,
            javaMajor = 17,
            libraries = listOf(library("org.example:base:1.0"))
        )
        val childDetails = details(id = child.id, inheritsFrom = base.id)
        val resolver = MinecraftVersionInheritanceResolver(
            FakeSource(
                entries = mapOf(base.id to base, child.id to child),
                details = mapOf(base.id to baseDetails, child.id to childDetails)
            )
        )
        val resolved = resolver.resolve(child)
        val plan = MinecraftInstallPlanner(MinecraftPlatformContext("linux", "x86_64"))
            .createPlan(
                child,
                resolved,
                MinecraftAssetIndexContents(emptyList(), 0L, false, false)
            )

        val client = plan.downloads.single { it.kind == MinecraftInstallPlanner.KIND_CLIENT }
        assertEquals("versions/1.20.1/1.20.1.jar", client.destinationRelativePath)
        assertEquals(base.id, plan.clientArtifactVersionId)
        assertEquals("versions/loader-child/loader-child.json",
            plan.downloads.single { it.kind == MinecraftInstallPlanner.KIND_VERSION_METADATA }.destinationRelativePath)
        assertTrue(plan.warnings.isEmpty())
    }

    @Test
    fun reportsMissingParentAndInheritanceCycle() {
        val missing = entry("loader-child")
        val sourceMissing = FakeSource(
            entries = mapOf(missing.id to missing),
            details = mapOf(missing.id to details(missing.id, inheritsFrom = "missing-parent"))
        )
        assertThrows(VersionInheritanceException::class.java) {
            MinecraftVersionInheritanceResolver(sourceMissing).resolve(missing)
        }

        val a = entry("loader-a")
        val b = entry("loader-b")
        val sourceCycle = FakeSource(
            entries = mapOf(a.id to a, b.id to b),
            details = mapOf(
                a.id to details(a.id, inheritsFrom = b.id),
                b.id to details(b.id, inheritsFrom = a.id)
            )
        )
        val error = assertThrows(VersionInheritanceException::class.java) {
            MinecraftVersionInheritanceResolver(sourceCycle).resolve(a)
        }
        assertTrue(error.message.orEmpty().contains("cycle"))
    }

    @Test
    fun mergerRejectsWrongOrUnresolvedParent() {
        val child = details("loader-child", inheritsFrom = "loader-parent")
        val wrongParent = details("wrong-parent")
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftVersionInheritanceMerger.merge(child, wrongParent)
        }

        val unresolvedParent = details("loader-parent", inheritsFrom = "grandparent")
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftVersionInheritanceMerger.merge(child, unresolvedParent)
        }
    }

    private fun entry(id: String) = MinecraftVersionEntry(
        id = id,
        type = "release",
        metadataUrl = "https://piston-meta.mojang.com/versions/" + id + ".json",
        updatedAt = "2025-01-01T00:00:00Z",
        releaseTime = "2025-01-01T00:00:00Z",
        sha1 = sha,
        complianceLevel = 1
    )

    private fun details(
        id: String,
        inheritsFrom: String? = null,
        mainClass: String? = null,
        assets: String? = null,
        assetIndex: MinecraftAssetIndex? = null,
        hasClient: Boolean = false,
        javaMajor: Int? = null,
        libraries: List<MinecraftLibrary> = emptyList()
    ) = MinecraftVersionDetails(
        id = id,
        type = "release",
        mainClass = mainClass,
        inheritsFrom = inheritsFrom,
        assets = assets,
        assetIndex = assetIndex,
        clientDownload = if (hasClient) {
            MinecraftArtifactDownload(
                "https://piston-data.mojang.com/versions/" + id + ".jar",
                clientHash,
                120L
            )
        } else null,
        javaMajorVersion = javaMajor,
        libraries = libraries,
        clientDownloadVersionId = if (hasClient) id else null
    )

    private fun assetIndex(id: String) = MinecraftAssetIndex(
        id = id,
        url = "https://piston-meta.mojang.com/assets/" + id + ".json",
        sha1 = assetHash,
        sizeBytes = 100L,
        totalSizeBytes = 500L
    )

    private fun library(name: String, baseLabel: String = name) = MinecraftLibrary(
        name = name,
        urlBase = "https://libraries.minecraft.net/" + baseLabel + "/",
        downloads = null,
        rules = emptyList(),
        natives = emptyMap(),
        extractExcludes = emptyList()
    )

    private class FakeSource(
        private val entries: Map<String, MinecraftVersionEntry>,
        private val details: Map<String, MinecraftVersionDetails>
    ) : MinecraftVersionMetadataSource {
        val fetchOrder = mutableListOf<String>()

        override fun findEntry(versionId: String): MinecraftVersionEntry? = entries[versionId]

        override fun fetchDetails(entry: MinecraftVersionEntry): MinecraftVersionDetails {
            fetchOrder.add(entry.id)
            return details[entry.id] ?: throw AssertionError("Missing test metadata for " + entry.id)
        }
    }
}
