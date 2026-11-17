package com.mflegend.flamelauncher.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftLaunchClasspathPlannerTest {
    private val android = MojangLibraryRuleContext(
        platformName = "android",
        osVersion = "14",
        osArchitecture = "aarch64"
    )

    @Test
    fun buildsOrderedClientAndLibraryPaths() {
        val metadata = json(
            libraries = """
                [
                  {"name":"org.example:first:1.0","downloads":{"artifact":{"path":"org/example/first/1.0/first-1.0.jar","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":120}}},
                  {"name":"org.example:second:2.0","downloads":{"artifact":{"path":"org/example/second/2.0/second-2.0.jar","sha1":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","size":240}}}
                ]
            """
        )

        val plan = MinecraftLaunchClasspathPlanner.plan(metadata, "1.20.1", android)

        assertEquals("net.minecraft.client.main.Main", plan.mainClass)
        assertEquals(
            listOf(
                "versions/1.20.1/1.20.1.jar",
                "libraries/org/example/first/1.0/first-1.0.jar",
                "libraries/org/example/second/2.0/second-2.0.jar"
            ),
            plan.orderedClasspathPaths
        )
    }

    @Test
    fun appliesAndroidLibraryRulesAndDoesNotIncludeDesktopOnlyArtifact() {
        val metadata = json(
            libraries = """
                [
                  {"name":"org.example:android:1","rules":[{"action":"allow","os":{"name":"android"}}],"downloads":{"artifact":{"path":"org/example/android/1/android-1.jar","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":120}}},
                  {"name":"org.example:linux:1","rules":[{"action":"allow","os":{"name":"linux"}}],"downloads":{"artifact":{"path":"org/example/linux/1/linux-1.jar","sha1":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","size":120}}}
                ]
            """
        )

        val plan = MinecraftLaunchClasspathPlanner.plan(metadata, "1.20.1", android)

        assertEquals(listOf("libraries/org/example/android/1/android-1.jar"), plan.libraryJarPaths)
    }

    @Test
    fun reportsNativeClassifiersAsPendingInsteadOfPuttingThemOnClasspath() {
        val metadata = json(
            libraries = """
                [
                  {"name":"org.lwjgl:lwjgl:3.3.3","downloads":{"artifact":{"path":"org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":120},"classifiers":{"natives-linux":{"path":"org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux.jar","sha1":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","size":120}}},"natives":{"linux":"natives-linux"}}
                ]
            """
        )

        val plan = MinecraftLaunchClasspathPlanner.plan(metadata, "1.20.1", android)

        assertEquals(1, plan.libraryJarPaths.size)
        assertEquals(listOf("org.lwjgl:lwjgl:3.3.3"), plan.nativeLibrariesPending)
    }

    @Test
    fun rejectsTraversalAndConflictingDuplicateArtifacts() {
        val traversal = json(
            libraries = """[{"name":"bad","downloads":{"artifact":{"path":"../evil.jar","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":10}}}]"""
        )
        assertTrue(runCatching {
            MinecraftLaunchClasspathPlanner.plan(traversal, "1.20.1", android)
        }.isFailure)

        val conflicting = json(
            libraries = """
                [
                  {"name":"first","downloads":{"artifact":{"path":"same.jar","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":10}}},
                  {"name":"second","downloads":{"artifact":{"path":"same.jar","sha1":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","size":10}}}
                ]
            """
        )
        assertTrue(runCatching {
            MinecraftLaunchClasspathPlanner.plan(conflicting, "1.20.1", android)
        }.isFailure)
    }

    @Test
    fun reportsApprovedLibrariesWithoutVerifiedArtifactInsteadOfDroppingThem() {
        val metadata = json(
            libraries = """
                [
                  {"name":"org.example:legacy:1.0"},
                  {"name":"org.example:native-only:1.0","downloads":{"classifiers":{"natives-linux":{"path":"native.jar","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":10}}},"natives":{"linux":"natives-linux"}}
                ]
            """
        )

        val plan = MinecraftLaunchClasspathPlanner.plan(metadata, "1.20.1", android)

        assertTrue(plan.libraryJarPaths.isEmpty())
        assertEquals(
            listOf("org.example:legacy:1.0", "org.example:native-only:1.0"),
            plan.unresolvedLibraries
        )
        assertEquals(listOf("org.example:native-only:1.0"), plan.nativeLibrariesPending)
    }

    @Test
    fun rejectsMalformedDownloadsAndArtifactObjects() {
        val malformedDownloads = json(
            libraries = """[{"name":"bad:downloads:1","downloads":42}]"""
        )
        val malformedArtifact = json(
            libraries = """[{"name":"bad:artifact:1","downloads":{"artifact":"not-an-object"}}]"""
        )

        assertTrue(runCatching {
            MinecraftLaunchClasspathPlanner.plan(malformedDownloads, "1.20.1", android)
        }.isFailure)
        assertTrue(runCatching {
            MinecraftLaunchClasspathPlanner.plan(malformedArtifact, "1.20.1", android)
        }.isFailure)
    }

    private fun json(libraries: String): ByteArray = """
        {
          "id":"1.20.1",
          "mainClass":"net.minecraft.client.main.Main",
          "minecraftArguments":"--demo",
          "libraries":$libraries
        }
    """.trimIndent().toByteArray(Charsets.UTF_8)
}
