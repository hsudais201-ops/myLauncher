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
            MinecraftArgumentPlanner.tokenizeLegacy("""--name "Alex \"The Builder\"" """)
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
}
