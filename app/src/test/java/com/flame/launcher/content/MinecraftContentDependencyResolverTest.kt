package com.flame.launcher.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftContentDependencyResolverTest {
    private fun version(
        projectId: String,
        versionId: String = "$projectId-v1",
        dependencies: List<MinecraftContentDependency> = emptyList()
    ) = MinecraftContentVersion(
        provider = ContentProviderId.MODRINTH,
        projectId = projectId,
        versionId = versionId,
        versionNumber = "1.0.0",
        displayName = projectId,
        gameVersions = setOf("1.20.1"),
        loaders = setOf(MinecraftLoader.FABRIC),
        fileName = "$projectId.jar",
        downloadUrl = "https://cdn.modrinth.com/$projectId.jar",
        dependencies = dependencies
    )

    private fun dependency(
        projectId: String,
        type: DependencyType = DependencyType.REQUIRED,
        versionId: String? = null
    ) = MinecraftContentDependency(projectId, versionId, type)

    @Test
    fun requiredDependenciesAreResolvedBeforeRoot() {
        val root = version("root", dependencies = listOf(dependency("library")))
        val library = version("library")

        val result = MinecraftContentDependencyResolver.resolve(listOf(root)) { _, id, _ ->
            if (id == "library") library else null
        }

        assertTrue(result is MinecraftContentDependencyResolver.Result.Resolved)
        assertEquals(
            listOf("library", "root"),
            (result as MinecraftContentDependencyResolver.Result.Resolved)
                .installOrder.map { it.projectId }
        )
    }

    @Test
    fun pinnedDependencyVersionIsPassedToLookupAndEnforced() {
        val root = version(
            "root",
            dependencies = listOf(dependency("library", versionId = "library-v2"))
        )
        val library = version("library", versionId = "library-v2")
        var requestedVersion: String? = null

        val result = MinecraftContentDependencyResolver.resolve(listOf(root)) { _, id, pin ->
            requestedVersion = pin
            if (id == "library") library else null
        }

        assertEquals("library-v2", requestedVersion)
        assertTrue(result is MinecraftContentDependencyResolver.Result.Resolved)
    }

    @Test
    fun missingRequiredDependencyFailsResolution() {
        val root = version("root", dependencies = listOf(dependency("missing")))

        val result = MinecraftContentDependencyResolver.resolve(listOf(root)) { _, _, _ -> null }

        assertEquals(
            MinecraftContentDependencyResolver.Failure.MissingRequiredDependency(
                MinecraftContentDependencyResolver.ProjectKey(ContentProviderId.MODRINTH, "root"),
                "missing"
            ),
            (result as MinecraftContentDependencyResolver.Result.Failed).reason
        )
    }

    @Test
    fun dependencyCycleIsRejected() {
        val first = version("first", dependencies = listOf(dependency("second")))
        val second = version("second", dependencies = listOf(dependency("first")))

        val result = MinecraftContentDependencyResolver.resolve(listOf(first)) { _, id, _ ->
            if (id == "second") second else null
        }

        assertTrue(
            (result as MinecraftContentDependencyResolver.Result.Failed).reason is
                MinecraftContentDependencyResolver.Failure.CircularDependency
        )
    }

    @Test
    fun optionalAndEmbeddedDependenciesAreNotFetched() {
        val root = version(
            "root",
            dependencies = listOf(
                dependency("optional", DependencyType.OPTIONAL),
                dependency("embedded", DependencyType.EMBEDDED)
            )
        )
        val requested = mutableListOf<String>()

        val result = MinecraftContentDependencyResolver.resolve(listOf(root)) { _, id, _ ->
            requested += id
            null
        }

        assertEquals(emptyList<String>(), requested)
        assertEquals(
            listOf("root"),
            (result as MinecraftContentDependencyResolver.Result.Resolved)
                .installOrder.map { it.projectId }
        )
    }

    @Test
    fun incompatibleDependencyBlocksWhenBothProjectsAreSelected() {
        val root = version(
            "root",
            dependencies = listOf(dependency("blocked", DependencyType.INCOMPATIBLE))
        )
        val blocked = version("blocked")

        val result = MinecraftContentDependencyResolver.resolve(listOf(root, blocked)) { _, _, _ ->
            null
        }

        assertTrue(
            (result as MinecraftContentDependencyResolver.Result.Failed).reason is
                MinecraftContentDependencyResolver.Failure.IncompatibleDependency
        )
    }

    @Test
    fun sameProjectCannotBeSelectedAtTwoVersions() {
        val first = version("shared", versionId = "v1")
        val second = version("shared", versionId = "v2")

        val result = MinecraftContentDependencyResolver.resolve(listOf(first, second)) { _, _, _ ->
            null
        }

        assertTrue(
            (result as MinecraftContentDependencyResolver.Result.Failed).reason is
                MinecraftContentDependencyResolver.Failure.ConflictingVersions
        )
    }

    @Test
    fun repeatedDependencyIsInstalledOnlyOnce() {
        val root = version(
            "root",
            dependencies = listOf(dependency("library"), dependency("library"))
        )
        val library = version("library")

        val result = MinecraftContentDependencyResolver.resolve(listOf(root)) { _, id, _ ->
            if (id == "library") library else null
        }

        assertEquals(
            listOf("library", "root"),
            (result as MinecraftContentDependencyResolver.Result.Resolved)
                .installOrder.map { it.projectId }
        )
    }
    @Test
    fun explicitlySelectedRootVersionSatisfiesUnpinnedDependency() {
        val root = version("root", dependencies = listOf(dependency("library")))
        val selectedLibrary = version("library", versionId = "library-v2")
        var lookupCalled = false

        val result = MinecraftContentDependencyResolver.resolve(
            listOf(root, selectedLibrary)
        ) { _, _, _ ->
            lookupCalled = true
            null
        }

        assertEquals(false, lookupCalled)
        assertEquals(
            listOf("library-v2", "root-v1"),
            (result as MinecraftContentDependencyResolver.Result.Resolved)
                .installOrder.map { it.versionId }
        )
    }


    @Test
    fun selectedRootDependencyStillResolvesItsTransitiveDependencies() {
        val root = version("root", dependencies = listOf(dependency("library")))
        val selectedLibrary = version(
            "library",
            versionId = "library-v2",
            dependencies = listOf(dependency("core"))
        )
        val core = version("core")
        val requested = mutableListOf<String>()

        val result = MinecraftContentDependencyResolver.resolve(
            listOf(root, selectedLibrary)
        ) { _, id, _ ->
            requested += id
            if (id == "core") core else null
        }

        assertEquals(listOf("core"), requested)
        assertEquals(
            listOf("core", "library", "root"),
            (result as MinecraftContentDependencyResolver.Result.Resolved)
                .installOrder.map { it.projectId }
        )
    }

    @Test
    fun selectedRootVersionConflictingWithPinnedDependencyIsRejected() {
        val root = version(
            "root",
            dependencies = listOf(dependency("library", versionId = "library-v1"))
        )
        val selectedLibrary = version("library", versionId = "library-v2")

        val result = MinecraftContentDependencyResolver.resolve(
            listOf(root, selectedLibrary)
        ) { _, _, _ -> null }

        assertEquals(
            MinecraftContentDependencyResolver.Failure.ConflictingVersions(
                MinecraftContentDependencyResolver.ProjectKey(ContentProviderId.MODRINTH, "library"),
                "library-v2",
                "library-v1"
            ),
            (result as MinecraftContentDependencyResolver.Result.Failed).reason
        )
    }


    @Test
    fun transitiveRequiredDependenciesUseDependencyFirstOrder() {
        val root = version("root", dependencies = listOf(dependency("library")))
        val library = version("library", dependencies = listOf(dependency("core")))
        val core = version("core")

        val result = MinecraftContentDependencyResolver.resolve(listOf(root)) { _, id, _ ->
            when (id) {
                "library" -> library
                "core" -> core
                else -> null
            }
        }

        assertEquals(
            listOf("core", "library", "root"),
            (result as MinecraftContentDependencyResolver.Result.Resolved)
                .installOrder.map { it.projectId }
        )
    }

    @Test
    fun incompatibleDeclarationDoesNotBlockWhenConflictingProjectIsNotSelected() {
        val root = version(
            "root",
            dependencies = listOf(dependency("blocked", DependencyType.INCOMPATIBLE))
        )

        val result = MinecraftContentDependencyResolver.resolve(listOf(root)) { _, _, _ -> null }

        assertTrue(result is MinecraftContentDependencyResolver.Result.Resolved)
    }

    @Test
    fun lookupExceptionFailsResolutionWithoutEscaping() {
        val root = version("root", dependencies = listOf(dependency("library")))

        val result = MinecraftContentDependencyResolver.resolve(listOf(root)) { _, _, _ ->
            throw IllegalStateException("provider offline")
        }

        assertEquals(
            MinecraftContentDependencyResolver.Failure.ProviderLookupFailed(
                MinecraftContentDependencyResolver.ProjectKey(ContentProviderId.MODRINTH, "root"),
                "library",
                "provider offline"
            ),
            (result as MinecraftContentDependencyResolver.Result.Failed).reason
        )
    }

    @Test
    fun providerCannotSubstituteAnotherVersionForPinnedDependency() {
        val root = version(
            "root",
            dependencies = listOf(dependency("library", versionId = "library-v2"))
        )
        val wrongLibrary = version("library", versionId = "library-v1")

        val result = MinecraftContentDependencyResolver.resolve(listOf(root)) { _, _, _ ->
            wrongLibrary
        }

        assertEquals(
            MinecraftContentDependencyResolver.Failure.PinnedVersionMismatch(
                MinecraftContentDependencyResolver.ProjectKey(ContentProviderId.MODRINTH, "library"),
                "library-v2",
                "library-v1"
            ),
            (result as MinecraftContentDependencyResolver.Result.Failed).reason
        )
    }

    @Test
    fun invalidRootVersionIsRejected() {
        val invalid = version("root", versionId = "")

        val result = MinecraftContentDependencyResolver.resolve(listOf(invalid)) { _, _, _ -> null }

        assertEquals(
            MinecraftContentDependencyResolver.Failure.InvalidVersionReference(
                ContentProviderId.MODRINTH,
                "root",
                ""
            ),
            (result as MinecraftContentDependencyResolver.Result.Failed).reason
        )
    }
}
