package com.flame.launcher.content

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftContentInstallCoordinatorTest {
    @Test
    fun rejectsEmptySelectionBeforeFilesystemWork() {
        val root = Files.createTempDirectory("flame-coordinator-").toFile()
        try {
            val coordinator = MinecraftContentInstallCoordinator(
                MinecraftContentInstallManifestStore(File(root, "manifests"))
            )
            val result = coordinator.install(
                selectedItems = emptyList(),
                target = target(root),
                stagingDirectory = File(root, "downloads"),
                modpackStagingDirectory = File(root, "modpacks"),
                findVersion = { _, _, _ -> null },
                findProject = { _, _ -> null }
            )
            assertTrue(result is MinecraftContentInstallCoordinatorResult.Failed)
            assertTrue((result as MinecraftContentInstallCoordinatorResult.Failed).reason.contains("Select"))
            assertTrue(!File(root, "downloads").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun requiredDependencyMustResolveBeforeAnyDownloadOrInstall() {
        val root = Files.createTempDirectory("flame-coordinator-").toFile()
        try {
            val project = project("root")
            val version = version(
                "root",
                dependencies = listOf(
                    MinecraftContentDependency("missing-dependency", type = DependencyType.REQUIRED)
                )
            )
            val coordinator = MinecraftContentInstallCoordinator(
                MinecraftContentInstallManifestStore(File(root, "manifests"))
            )
            val result = coordinator.install(
                selectedItems = listOf(MinecraftContentInstallItem(project, version)),
                target = target(root),
                stagingDirectory = File(root, "downloads"),
                modpackStagingDirectory = File(root, "modpacks"),
                findVersion = { _, _, _ -> null },
                findProject = { _, _ -> null }
            )
            assertTrue(result is MinecraftContentInstallCoordinatorResult.Failed)
            assertTrue((result as MinecraftContentInstallCoordinatorResult.Failed).reason.contains("Dependency resolution failed"))
            assertTrue(!File(root, "downloads").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun curseForgeDependencyUsesCurseForgeVersionAndProjectLookups() {
        val root = Files.createTempDirectory("flame-coordinator-curseforge-").toFile()
        try {
            val rootProject = curseForgeProject("100")
            val rootVersion = curseForgeVersion(
                "100",
                dependencies = listOf(
                    MinecraftContentDependency("200", type = DependencyType.REQUIRED)
                )
            )
            var versionLookupProvider: ContentProviderId? = null
            var projectLookupProvider: ContentProviderId? = null
            val coordinator = MinecraftContentInstallCoordinator(
                MinecraftContentInstallManifestStore(File(root, "manifests"))
            )

            val result = coordinator.install(
                selectedItems = listOf(MinecraftContentInstallItem(rootProject, rootVersion)),
                target = target(root),
                stagingDirectory = File(root, "downloads"),
                modpackStagingDirectory = File(root, "modpacks"),
                findVersion = { providerId, projectId, versionId ->
                    versionLookupProvider = providerId
                    assertTrue(projectId == "200")
                    assertTrue(versionId == null)
                    curseForgeVersion("200")
                },
                findProject = { providerId, projectId ->
                    projectLookupProvider = providerId
                    assertTrue(projectId == "200")
                    null
                }
            )

            assertTrue(result is MinecraftContentInstallCoordinatorResult.Failed)
            assertTrue(
                (result as MinecraftContentInstallCoordinatorResult.Failed).reason
                    .contains("Could not load project details for required dependency 200")
            )
            assertEquals(ContentProviderId.CURSEFORGE, versionLookupProvider)
            assertEquals(ContentProviderId.CURSEFORGE, projectLookupProvider)
            assertTrue(!File(root, "downloads").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun curseForgeProject(id: String) = MinecraftContentProject(
        provider = ContentProviderId.CURSEFORGE,
        projectId = id,
        slug = id,
        title = "CurseForge $id",
        description = "",
        contentType = MinecraftContentType.MOD,
        author = "Test"
    )

    private fun curseForgeVersion(
        id: String,
        dependencies: List<MinecraftContentDependency> = emptyList()
    ) = MinecraftContentVersion(
        provider = ContentProviderId.CURSEFORGE,
        projectId = id,
        versionId = "$id-file",
        versionNumber = "1.0",
        displayName = "CurseForge $id",
        gameVersions = setOf("1.20.1"),
        loaders = setOf(MinecraftLoader.FABRIC),
        fileName = "$id.jar",
        downloadUrl = "https://edge.forgecdn.net/files/$id.jar",
        fileSizeBytes = 10,
        sha1 = null,
        dependencies = dependencies
    )

    private fun target(root: File) = MinecraftInstanceTarget(
        minecraftVersion = "1.20.1",
        loader = MinecraftLoader.FABRIC,
        contentDirectory = File(root, "instance").absolutePath
    )

    private fun project(id: String) = MinecraftContentProject(
        provider = ContentProviderId.MODRINTH,
        projectId = id,
        slug = id,
        title = id,
        description = "",
        contentType = MinecraftContentType.MOD,
        author = "Test"
    )

    private fun version(
        id: String,
        dependencies: List<MinecraftContentDependency> = emptyList()
    ) = MinecraftContentVersion(
        provider = ContentProviderId.MODRINTH,
        projectId = id,
        versionId = "$id-version",
        versionNumber = "1.0",
        displayName = "1.0",
        gameVersions = setOf("1.20.1"),
        loaders = setOf(MinecraftLoader.FABRIC),
        fileName = "$id.jar",
        downloadUrl = "https://cdn.modrinth.com/data/$id/versions/$id-version/$id.jar",
        fileSizeBytes = 10,
        sha1 = null,
        sha512 = null,
        dependencies = dependencies
    )
}
