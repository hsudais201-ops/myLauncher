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
    fun keepsDesktopNativeMappingAsBlockerInsteadOfAssumingAndroidCompatibility() {
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
    fun ignoresClassifierDataWhenNoNativeMappingIsDeclared() {
        val metadata = json(
            libraries = """
                [
                  {"name":"org.example:classifier-only:1","downloads":{"artifact":{"path":"org/example/classifier-only/1/library.jar","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":120},"classifiers":{"optional":{"path":"optional.jar","sha1":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","size":120}}}}
                ]
            """
        )

        val plan = MinecraftLaunchClasspathPlanner.plan(metadata, "1.20.1", android)

        assertTrue(plan.nativeLibrariesPending.isEmpty())
    }

    @Test
    fun reportsAndroidNativeMappingAsPendingInsteadOfPuttingItOnClasspath() {
        val metadata = json(
            libraries = """
                [
                  {"name":"org.example:android-native:1","downloads":{"artifact":{"path":"org/example/android-native/1/library.jar","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":120},"classifiers":{"natives-android":{"path":"native.jar","sha1":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","size":120}}},"natives":{"android":"natives-android"}}
                ]
            """
        )

        val plan = MinecraftLaunchClasspathPlanner.plan(metadata, "1.20.1", android)

        assertEquals(1, plan.libraryJarPaths.size)
        assertEquals(listOf("org.example:android-native:1"), plan.nativeLibrariesPending)
    }

    @Test
    fun rejectsMissingDesktopNativeClassifierEvenOnAndroid() {
        val metadata = json(
            libraries = """
                [
                  {"name":"org.example:broken-linux-native:1","downloads":{"artifact":{"path":"library.jar","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":120}},"natives":{"linux":"missing-linux-classifier"}}
                ]
            """
        )
        assertTrue(runCatching {
            MinecraftLaunchClasspathPlanner.plan(metadata, "1.20.1", android)
        }.isFailure)
    }

    @Test
    fun rejectsUnsafeOrUnverifiedNativeClassifierMetadata() {
        val unsafePath = json(
            libraries = """
                [
                  {"name":"org.example:unsafe-native:1","downloads":{"artifact":{"path":"library.jar","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":120},"classifiers":{"natives-linux":{"path":"../native.jar","sha1":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","size":120}}},"natives":{"linux":"natives-linux"}}
                ]
            """
        )
        val invalidChecksum = json(
            libraries = """
                [
                  {"name":"org.example:invalid-native:1","downloads":{"artifact":{"path":"library.jar","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":120},"classifiers":{"natives-linux":{"path":"native.jar","sha1":"not-a-checksum","size":120}}},"natives":{"linux":"natives-linux"}}
                ]
            """
        )
        assertTrue(runCatching {
            MinecraftLaunchClasspathPlanner.plan(unsafePath, "1.20.1", android)
        }.isFailure)
        assertTrue(runCatching {
            MinecraftLaunchClasspathPlanner.plan(invalidChecksum, "1.20.1", android)
        }.isFailure)
    }

    @Test
    fun rejectsNativeMappingWithoutItsDeclaredClassifier() {
        val metadata = json(
            libraries = """
                [
                  {"name":"org.example:broken-native:1","downloads":{"artifact":{"path":"library.jar","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":120}},"natives":{"android":"missing"}}
                ]
            """
        )
        assertTrue(runCatching {
            MinecraftLaunchClasspathPlanner.plan(metadata, "1.20.1", android)
        }.isFailure)
    }

    @Test
    fun rejectsNativeClassifierDestinationCollisionsWithOtherPayloads() {
        val caseInsensitiveCollision = json(
            libraries = """
                [
                  {"name":"ordinary","downloads":{"artifact":{"path":"org/example/Native.jar","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":120}}},
                  {"name":"native","downloads":{"artifact":{"path":"native-wrapper.jar","sha1":"cccccccccccccccccccccccccccccccccccccccc","size":120},"classifiers":{"natives-android":{"path":"org/example/native.jar","sha1":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","size":120}}},"natives":{"android":"natives-android"}}
                ]
            """
        )
        val conflictingExactPath = json(
            libraries = """
                [
                  {"name":"ordinary","downloads":{"artifact":{"path":"org/example/shared.jar","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":120}}},
                  {"name":"native","downloads":{"artifact":{"path":"native-wrapper.jar","sha1":"cccccccccccccccccccccccccccccccccccccccc","size":120},"classifiers":{"natives-android":{"path":"org/example/shared.jar","sha1":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","size":120}}},"natives":{"android":"natives-android"}}
                ]
            """
        )
        val fileDirectoryCollision = json(
            libraries = """
                [
                  {"name":"ordinary","downloads":{"artifact":{"path":"org/example","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":120}}},
                  {"name":"native","downloads":{"artifact":{"path":"native-wrapper.jar","sha1":"cccccccccccccccccccccccccccccccccccccccc","size":120},"classifiers":{"natives-android":{"path":"org/example/native.jar","sha1":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","size":120}}},"natives":{"android":"natives-android"}}
                ]
            """
        )

        listOf(caseInsensitiveCollision, conflictingExactPath, fileDirectoryCollision).forEach { metadata ->
            assertTrue(runCatching {
                MinecraftLaunchClasspathPlanner.plan(metadata, "1.20.1", android)
            }.isFailure)
        }
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
                  {"name":"org.example:native-only:1.0","downloads":{"classifiers":{"natives-android":{"path":"native.jar","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":10}}},"natives":{"android":"natives-android"}}
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
    fun rejectsCaseInsensitiveArtifactPathCollisions() {
        val metadata = json(
            libraries = """
                [
                  {"name":"first","downloads":{"artifact":{"path":"org/example/Library.jar","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":10}}},
                  {"name":"second","downloads":{"artifact":{"path":"org/example/library.jar","sha1":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","size":10}}}
                ]
            """
        )
        assertTrue(runCatching {
            MinecraftLaunchClasspathPlanner.plan(metadata, "1.20.1", android)
        }.isFailure)
    }

    @Test
    fun rejectsLibraryFileDirectoryPrefixCollisions() {
        val metadata = json(
            libraries = """
                [
                  {"name":"file","downloads":{"artifact":{"path":"org/example","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":10}}},
                  {"name":"child","downloads":{"artifact":{"path":"org/example/library.jar","sha1":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","size":10}}}
                ]
            """
        )
        assertTrue(runCatching {
            MinecraftLaunchClasspathPlanner.plan(metadata, "1.20.1", android)
        }.isFailure)
    }

    @Test
    fun rejectsNonAdjacentLibraryFileDirectoryPrefixCollisions() {
        val metadata = json(
            libraries = """
                [
                  {"name":"first","downloads":{"artifact":{"path":"org/example","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":10}}},
                  {"name":"middle","downloads":{"artifact":{"path":"com/example/middle.jar","sha1":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","size":10}}},
                  {"name":"child","downloads":{"artifact":{"path":"org/example/child.jar","sha1":"cccccccccccccccccccccccccccccccccccccccc","size":10}}}
                ]
            """
        )
        assertTrue(runCatching {
            MinecraftLaunchClasspathPlanner.plan(metadata, "1.20.1", android)
        }.isFailure)
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
