package com.voidcraft.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class MinecraftInstallPlannerTest {
    private val shaA = "0123456789abcdef0123456789abcdef01234567"
    private val shaB = "abcdef0123456789abcdef0123456789abcdef01"
    private val shaC = "fedcba9876543210fedcba9876543210fedcba98"

    @Test
    fun assetIndexParserValidatesHashesAndBuildsContentAddressedPaths() {
        val json = """
            {
              "virtual": true,
              "map_to_resources": false,
              "objects": {
                "sounds/test.ogg": { "hash": "$shaA", "size": 128 },
                "textures/test.png": { "hash": "$shaB", "size": 256 }
              }
            }
        """.trimIndent()
        val index = MinecraftAssetIndexParser().parse(json)

        assertEquals(2, index.objects.size)
        assertEquals(384L, index.totalSizeBytes)
        assertTrue(index.virtual)
        assertFalse(index.mapToResources)
        assertEquals("assets/objects/01/$shaA", index.objects[0].relativeObjectPath)
        assertEquals("https://resources.download.minecraft.net/01/$shaA", index.objects[0].url)
    }

    @Test
    fun assetIndexParserRejectsInvalidHashAndOversizedCount() {
        val badHash = """{"objects":{"a":{"hash":"bad","size":1}}}"""
        assertThrows(IllegalArgumentException::class.java) { MinecraftAssetIndexParser().parse(badHash) }
    }

    @Test
    fun libraryRuleEvaluationDefaultsAllowButAppliesOrderedOverrides() {
        val evaluator = MinecraftLibraryRuleEvaluator()
        val linux = MinecraftPlatformContext("linux", "x86_64", "6.8.0")
        assertTrue(evaluator.isAllowed(emptyList(), linux))

        val rules = listOf(
            MinecraftLibraryRule("allow", null, null, null, emptyMap()),
            MinecraftLibraryRule("disallow", "linux", "^6\\.8\\..+$", null, emptyMap())
        )
        assertFalse(evaluator.isAllowed(rules, linux))
        assertTrue(evaluator.isAllowed(rules, linux.copy(osVersion = "6.7.9")))
    }

    @Test
    fun parsesLibraryArtifactsClassifiersAndOrderedRules() {
        val json = versionDetailsJson()
        val details = MinecraftVersionDetailsParser().parse(json)
        assertEquals(3, details.libraries.size)

        val lwjgl = details.libraries.first { it.name.startsWith("org.lwjgl:lwjgl:") }
        assertEquals("natives-linux-" + "64", lwjgl.natives["linux"]?.replace("$" + "{arch}", "64"))
        assertEquals("org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux-64.jar",
            lwjgl.downloads?.classifiers?.get("natives-linux-64")?.path)
        assertEquals(listOf("META-INF/"), lwjgl.extractExcludes)
        assertEquals("osx", details.libraries.last().rules.first().osName)
    }

    @Test
    fun plannerBuildsClientLibraryNativeAndAssetDownloadsWithoutConflicts() {
        val details = MinecraftVersionDetailsParser().parse(versionDetailsJson())
        val entry = MinecraftVersionEntry(
            id = details.id,
            type = "release",
            metadataUrl = "https://piston-meta.mojang.com/v1/packages/$shaA/1.21.4.json",
            updatedAt = "2025-01-01T00:00:00Z",
            releaseTime = "2025-01-01T00:00:00Z",
            sha1 = shaA,
            complianceLevel = 1
        )
        val assets = MinecraftAssetIndexParser().parse(
            """{"objects":{"sound.ogg":{"hash":"$shaB","size":20}}}"""
        )
        val plan = MinecraftInstallPlanner(MinecraftPlatformContext("linux", "x86_64", "6.8.0"))
            .createPlan(entry, details, assets)

        assertTrue(plan.downloads.any { it.kind == MinecraftInstallPlanner.KIND_CLIENT })
        assertTrue(plan.downloads.any { it.kind == MinecraftInstallPlanner.KIND_VERSION_METADATA })
        assertTrue(plan.downloads.any { it.kind == MinecraftInstallPlanner.KIND_LIBRARY && it.logicalName?.startsWith("com.example:common:") == true })
        val native = plan.downloads.single { it.kind == MinecraftInstallPlanner.KIND_NATIVE_LIBRARY }
        assertEquals(shaC, native.expectedSha1)
        assertEquals(listOf("META-INF/"), native.extractExcludes)
        assertTrue(plan.downloads.any { it.kind == MinecraftInstallPlanner.KIND_ASSET_INDEX })
        assertTrue(plan.downloads.any { it.kind == MinecraftInstallPlanner.KIND_ASSET_OBJECT })
        assertFalse(plan.downloads.any { it.logicalName?.startsWith("org.example:mac-only:") == true })
        assertTrue(plan.warnings.isEmpty())
    }

    @Test
    fun plannerWarnsWhenAssetIndexIsNotResolvedOrVersionInherits() {
        val original = MinecraftVersionDetailsParser().parse(versionDetailsJson())
        val inherited = original.copy(inheritsFrom = "1.20.1")
        val entry = MinecraftVersionEntry(
            id = inherited.id,
            type = "release",
            metadataUrl = "https://piston-meta.mojang.com/version.json",
            updatedAt = "2025-01-01T00:00:00Z",
            releaseTime = "2025-01-01T00:00:00Z",
            sha1 = shaA,
            complianceLevel = 1
        )
        val plan = MinecraftInstallPlanner(MinecraftPlatformContext("linux", "x86_64"))
            .createPlan(entry, inherited)

        assertTrue(plan.warnings.any { it.contains("Asset index contents") })
        assertTrue(plan.warnings.any { it.contains("parent metadata") })
        assertFalse(plan.isFullyResolved)
    }

    @Test
    fun mavenFallbackCannotCreatePathTraversal() {
        assertEquals("org/example/lib/1.2/lib-1.2.jar", MinecraftMavenPath.derive("org.example:lib:1.2"))
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftMavenPath.validateRelativePath("../../outside.jar")
        }
    }

    private fun versionDetailsJson(): String = """
        {
          "id": "1.21.4",
          "type": "release",
          "mainClass": "net.minecraft.client.main.Main",
          "assetIndex": {
            "id": "1.21",
            "url": "https://piston-meta.mojang.com/v1/packages/$shaA/assets.json",
            "sha1": "$shaA",
            "size": 2000,
            "totalSize": 384
          },
          "downloads": {
            "client": {
              "url": "https://piston-data.mojang.com/v1/objects/$shaB/client.jar",
              "sha1": "$shaB",
              "size": 1200
            }
          },
          "javaVersion": { "majorVersion": 21 },
          "libraries": [
            {
              "name": "com.example:common:1.0",
              "downloads": {
                "artifact": {
                  "path": "com/example/common/1.0/common-1.0.jar",
                  "url": "https://libraries.minecraft.net/com/example/common/1.0/common-1.0.jar",
                  "sha1": "$shaA",
                  "size": 100
                }
              }
            },
            {
              "name": "org.lwjgl:lwjgl:3.3.3",
              "url": "https://libraries.minecraft.net/",
              "downloads": {
                "artifact": {
                  "path": "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar",
                  "url": "https://libraries.minecraft.net/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar",
                  "sha1": "$shaA",
                  "size": 10
                },
                "classifiers": {
                  "natives-linux-64": {
                    "path": "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux-64.jar",
                    "url": "https://libraries.minecraft.net/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux-64.jar",
                    "sha1": "$shaC",
                    "size": 30
                  }
                }
              },
              "rules": [
                { "action": "allow", "os": { "name": "linux" } },
                { "action": "disallow", "os": { "name": "osx", "version": "^10\\.5\\.\\d$" } }
              ],
              "natives": { "linux": "natives-linux-$" + "{arch}" },
              "extract": { "exclude": ["META-INF/"] }
            },
            {
              "name": "org.example:mac-only:1.0",
              "downloads": {
                "artifact": {
                  "path": "org/example/mac-only/1.0/mac-only-1.0.jar",
                  "url": "https://libraries.minecraft.net/org/example/mac-only/1.0/mac-only-1.0.jar",
                  "sha1": "$shaC",
                  "size": 15
                }
              },
              "rules": [{ "action": "allow", "os": { "name": "osx" } }]
            }
          ]
        }
    """.trimIndent()
}
