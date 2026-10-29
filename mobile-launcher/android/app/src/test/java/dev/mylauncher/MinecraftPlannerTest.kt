package dev.mylauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MinecraftPlannerTest {
    @Test
    fun legacyArgumentsPreserveQuotedSpaces() {
        assertEquals(
            listOf("--username", "Alex Player", "--width", "854"),
            MinecraftArgumentPlanner.tokenizeLegacy("""--username "Alex Player" --width 854""")
        )
    }

    @Test
    fun legacyArgumentsHandleEscapedQuotesAndEmptyTokens() {
        assertEquals(
            listOf("--name", "Alex \"The Builder\"", ""),
            MinecraftArgumentPlanner.tokenizeLegacy("--name \"Alex \\\"The Builder\\\"\" \"\"")
        )
    }

    @Test
    fun legacyArgumentsRejectUnclosedQuotes() {
        val error = runCatching { MinecraftArgumentPlanner.tokenizeLegacy("""--username "Alex""") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun classpathPlannerReportsMissingClientAndLibraries() {
        val root = createTempDir(prefix = "mylauncher-test-")
        try {
            val plan = MinecraftInstallPlan(
                versionId = "1.20.1",
                mainClass = "net.minecraft.client.main.Main",
                assetIndexId = "1.20",
                assetIndexUrl = "https://example.invalid/index.json",
                assetIndexSha1 = "0".repeat(40),
                clientJarUrl = "https://example.invalid/client.jar",
                clientJarSha1 = "0".repeat(40),
                libraries = listOf(
                    LibraryArtifact("test:missing:1", "https://example.invalid/lib.jar", "0".repeat(40), 1, "test/missing/1/missing-1.jar")
                )
            )
            val result = MinecraftClasspathPlanner.build(root, plan)
            assertFalse(result.complete)
            assertTrue(result.classpath.isEmpty())
            assertEquals(2, result.missingArtifacts.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun classpathPlannerRejectsTraversalPaths() {
        val root = createTempDir(prefix = "mylauncher-test-")
        try {
            val plan = MinecraftInstallPlan(
                versionId = "1.20.1",
                mainClass = "net.minecraft.client.main.Main",
                assetIndexId = "1.20",
                assetIndexUrl = "https://example.invalid/index.json",
                assetIndexSha1 = "0".repeat(40),
                clientJarUrl = "https://example.invalid/client.jar",
                clientJarSha1 = "0".repeat(40),
                libraries = listOf(
                    LibraryArtifact("test:escape:1", "https://example.invalid/lib.jar", "0".repeat(40), 1, "../../outside.jar")
                )
            )
            val result = MinecraftClasspathPlanner.build(root, plan)
            assertFalse(result.complete)
            assertTrue(result.missingArtifacts.any { it.startsWith("Invalid library path:") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun modernArgumentPlannerSubstitutesPlaceholdersAndReadsJavaVersion() {
        val json = """{
          "mainClass":"net.minecraft.client.main.Main",
          "javaVersion":{"majorVersion":21},
          "arguments":{
            "jvm":["-Xmx2G","-Duser.home=${'$'}{game_directory}"],
            "game":["--username","${'$'}{auth_player_name}",{"rules":[{"action":"allow","os":{"name":"linux"}}],"value":["--width","854"]}]
          }
        }"""
        val result = MinecraftArgumentPlanner.parse(
            json,
            mapOf("game_directory" to "/data/game", "auth_player_name" to "Alex")
        )
        assertEquals("net.minecraft.client.main.Main", result.mainClass)
        assertEquals(21, result.javaMajorVersion)
        assertEquals(listOf("-Xmx2G", "-Duser.home=/data/game"), result.jvmArguments)
        assertEquals(listOf("--username", "Alex", "--width", "854"), result.gameArguments)
    }

    @Test
    fun modernArgumentPlannerDefaultsRulesToDeniedUntilAllowed() {
        val json = """{
          "mainClass":"example.Main",
          "arguments":{
            "jvm":[
              {"rules":[{"action":"allow","os":{"name":"windows"}}],"value":"-Dwindows=true"},
              {"rules":[{"action":"allow","os":{"name":"linux"}},{"action":"disallow","os":{"name":"linux"}}],"value":"-Dblocked=true"},
              {"rules":[{"action":"allow","os":{"name":"linux"}}],"value":"-Dlinux=true"}
            ],
            "game":[]
          }
        }"""
        val result = MinecraftArgumentPlanner.parse(json, emptyMap())
        assertEquals(listOf("-Dlinux=true"), result.jvmArguments)
    }
}
