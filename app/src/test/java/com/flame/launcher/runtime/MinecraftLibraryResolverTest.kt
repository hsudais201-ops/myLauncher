package com.flame.launcher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MinecraftLibraryResolverTest {
    private val digest = "0123456789abcdef0123456789abcdef01234567"
    private val artifact = MinecraftDownloadDescriptor(
        "https://libraries.minecraft.net/artifact.jar", digest, 123, "org/example/library.jar"
    )
    private val linuxNative = MinecraftDownloadDescriptor(
        "https://libraries.minecraft.net/natives-linux-64.jar", digest, 456, "org/example/natives.jar"
    )

    @Test
    fun resolvesOrderedOsArchitectureAndFeatureRulesAndNativeClassifier() {
        val library = MinecraftLibraryArtifact(
            coordinate = "org.example:library:1.0",
            artifact = artifact,
            classifiers = mapOf("natives-linux-64" to linuxNative),
            rules = listOf(
                MinecraftLibraryRule("disallow", "windows", null, null, emptyMap()),
                MinecraftLibraryRule(
                    "allow", "linux", "aarch64", null, mapOf("is_demo_user" to false)
                )
            ),
            natives = mapOf("linux" to ("natives-linux-" + 36.toChar() + "{arch}")),
            extractExcludes = listOf("META-INF")
        )
        val linux = MinecraftLibraryResolver.resolve(
            listOf(library),
            MinecraftLibraryEnvironment(
                "linux", "aarch64", nativeArchitecture = "64",
                features = mapOf("is_demo_user" to false)
            )
        )
        assertEquals(1, linux.size)
        assertEquals(artifact, linux.single().artifact)
        assertEquals(linuxNative, linux.single().nativeArtifact)
        assertEquals(listOf("META-INF"), linux.single().extractExcludes)

        val windows = MinecraftLibraryResolver.resolve(
            listOf(library),
            MinecraftLibraryEnvironment("windows", "x86_64", nativeArchitecture = "64")
        )
        assertEquals(0, windows.size)
    }

    @Test
    fun rulesDefaultToDisallowedAndLaterMatchingRuleWins() {
        val library = MinecraftLibraryArtifact(
            coordinate = "org.example:conditional:1.0",
            artifact = artifact,
            classifiers = emptyMap(),
            rules = listOf(
                MinecraftLibraryRule("allow", "linux", null, null, emptyMap()),
                MinecraftLibraryRule("disallow", "linux", null, null, mapOf("is_demo_user" to true))
            )
        )
        val normal = MinecraftLibraryResolver.resolve(
            listOf(library),
            MinecraftLibraryEnvironment("linux", "aarch64", features = mapOf("is_demo_user" to false))
        )
        assertEquals(1, normal.size)
        val demo = MinecraftLibraryResolver.resolve(
            listOf(library),
            MinecraftLibraryEnvironment("linux", "aarch64", features = mapOf("is_demo_user" to true))
        )
        assertEquals(0, demo.size)
    }

    @Test
    fun libraryWithoutRulesIsPortableAndNoNativeClassifierIsRequired() {
        val library = MinecraftLibraryArtifact(
            coordinate = "org.example:portable:1.0",
            artifact = artifact,
            classifiers = emptyMap()
        )
        val resolved = MinecraftLibraryResolver.resolve(
            listOf(library),
            MinecraftLibraryEnvironment("linux", "aarch64")
        )
        assertEquals(1, resolved.size)
        assertNull(resolved.single().nativeArtifact)
    }

    @Test
    fun requiresNativeArchitectureAndClassifierForNativeMappings() {
        val library = MinecraftLibraryArtifact(
            coordinate = "org.example:native:1.0",
            artifact = null,
            classifiers = emptyMap(),
            natives = mapOf("linux" to ("natives-linux-" + 36.toChar() + "{arch}"))
        )
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftLibraryResolver.resolve(listOf(library), MinecraftLibraryEnvironment("linux", "aarch64"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftLibraryResolver.resolve(
                listOf(library),
                MinecraftLibraryEnvironment("linux", "aarch64", nativeArchitecture = "64")
            )
        }
    }

    @Test
    fun rejectsInvalidTargetEnvironment() {
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftLibraryEnvironment("android", "aarch64")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftLibraryEnvironment("linux", "aarch64", nativeArchitecture = "128")
        }
    }
}
