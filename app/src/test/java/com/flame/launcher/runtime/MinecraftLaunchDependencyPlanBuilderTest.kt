package com.flame.launcher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MinecraftLaunchDependencyPlanBuilderTest {
    private val digest = "0123456789abcdef0123456789abcdef01234567"

    private fun descriptor(path: String? = null, size: Long = 100) =
        MinecraftDownloadDescriptor(
            url = "https://libraries.minecraft.net/${path ?: "asset.bin"}",
            sha1 = digest,
            size = size,
            path = path
        )

    private fun details(
        libraries: List<MinecraftLibraryArtifact> = emptyList()
    ) = MinecraftVersionDetails(
        id = "1.21",
        mainClass = "net.minecraft.client.main.Main",
        assets = "17",
        assetIndex = descriptor(),
        client = descriptor(),
        libraries = libraries,
        javaMajorVersion = 21
    )

    @Test
    fun createsStableClientAssetAndLibraryDownloadAndClasspathPlan() {
        val library = MinecraftLibraryArtifact(
            coordinate = "org.example:sample:1.0",
            artifact = descriptor("org/example/sample/1.0/sample-1.0.jar"),
            classifiers = emptyMap()
        )
        val plan = MinecraftLaunchDependencyPlanBuilder.build(
            details(listOf(library)),
            MinecraftLibraryEnvironment("linux", "x86_64")
        )

        assertEquals("1.21", plan.versionId)
        assertEquals("net.minecraft.client.main.Main", plan.mainClass)
        assertEquals(21, plan.requiredJavaMajorVersion)
        assertEquals("17", plan.assetIndexId)
        assertEquals(
            listOf(
                "versions/1.21/1.21.jar",
                "libraries/org/example/sample/1.0/sample-1.0.jar"
            ),
            plan.classPath
        )
        assertEquals(
            listOf(
                MinecraftPlannedDownloadKind.CLIENT,
                MinecraftPlannedDownloadKind.ASSET_INDEX,
                MinecraftPlannedDownloadKind.LIBRARY
            ),
            plan.downloads.map { it.kind }
        )
    }

    @Test
    fun selectsOnlyLibrariesAllowedByTargetRules() {
        val library = MinecraftLibraryArtifact(
            coordinate = "org.example:windows-only:1.0",
            artifact = descriptor("org/example/windows.jar"),
            classifiers = emptyMap(),
            rules = listOf(
                MinecraftLibraryRule("allow", "windows", null, null, emptyMap())
            )
        )
        val plan = MinecraftLaunchDependencyPlanBuilder.build(
            details(listOf(library)),
            MinecraftLibraryEnvironment("linux", "aarch64")
        )
        assertEquals(listOf("versions/1.21/1.21.jar"), plan.classPath)
        assertEquals(2, plan.downloads.size)
    }

    @Test
    fun keepsNativeLibrariesOutOfClasspathAndCarriesExtractionExcludes() {
        val native = descriptor("org/example/natives-linux-64.jar")
        val library = MinecraftLibraryArtifact(
            coordinate = "org.example:native:1.0",
            artifact = descriptor("org/example/native.jar"),
            classifiers = mapOf("natives-linux-64" to native),
            natives = mapOf("linux" to "natives-linux-64"),
            extractExcludes = listOf("META-INF")
        )
        val plan = MinecraftLaunchDependencyPlanBuilder.build(
            details(listOf(library)),
            MinecraftLibraryEnvironment("linux", "x86_64")
        )
        assertEquals(2, plan.classPath.size)
        assertEquals(1, plan.nativeLibraries.size)
        assertEquals(MinecraftPlannedDownloadKind.NATIVE, plan.nativeLibraries.single().kind)
        assertEquals(listOf("META-INF"), plan.nativeLibraries.single().extractExcludes)
    }

    @Test
    fun rejectsLibrariesWithoutInstallableArtifactPaths() {
        val library = MinecraftLibraryArtifact(
            coordinate = "org.example:missing-path:1.0",
            artifact = descriptor(),
            classifiers = emptyMap()
        )
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftLaunchDependencyPlanBuilder.build(
                details(listOf(library)),
                MinecraftLibraryEnvironment("linux", "x86_64")
            )
        }
    }

    @Test
    fun doesNotRequireJavaMetadataWhenVersionOmitsIt() {
        val plan = MinecraftLaunchDependencyPlanBuilder.build(
            details().copy(javaMajorVersion = null),
            MinecraftLibraryEnvironment("linux", "x86_64")
        )
        assertNull(plan.requiredJavaMajorVersion)
    }
}
