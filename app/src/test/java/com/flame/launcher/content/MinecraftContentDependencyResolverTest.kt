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
}
