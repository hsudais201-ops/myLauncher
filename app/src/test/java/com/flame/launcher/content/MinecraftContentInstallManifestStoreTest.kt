package com.flame.launcher.content

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftContentInstallManifestStoreTest {
    private fun plan(
        instance: File,
        contentType: MinecraftContentType = MinecraftContentType.MOD,
        world: File? = null
    ): MinecraftContentInstallPlan {
        val project = MinecraftContentProject(
            provider = ContentProviderId.MODRINTH,
            projectId = "test-project",
            slug = "test-project",
            title = "Test project",
            description = "",
            contentType = contentType,
            author = "Test"
        )
        val version = MinecraftContentVersion(
            provider = ContentProviderId.MODRINTH,
            projectId = "test-project",
            versionId = "test-version",
            versionNumber = "1.0",
            displayName = "1.0",
            gameVersions = setOf("1.20.1"),
            loaders = setOf(MinecraftLoader.FABRIC),
            fileName = if (contentType == MinecraftContentType.DATAPACK) "test-pack.zip" else "test-mod.jar",
            downloadUrl = "https://cdn.modrinth.com/data/test-project/file"
        )
        val result = MinecraftContentInstallPlanner.plan(
            listOf(MinecraftContentInstallItem(project, version)),
            MinecraftInstanceTarget(
                minecraftVersion = "1.20.1",
                loader = MinecraftLoader.FABRIC,
                contentDirectory = instance.absolutePath,
                selectedWorldDirectory = world?.absolutePath
            )
        )
        assertTrue("Expected ready plan, got $result", result is MinecraftInstallPlanResult.Ready)
        return (result as MinecraftInstallPlanResult.Ready).plan
    }

    private fun writeInstalledFile(plan: MinecraftContentInstallPlan, payload: String = "owned bytes"): File {
        val destination = File(plan.items.single().destinationPath)
        assertTrue(destination.parentFile!!.mkdirs() || destination.parentFile!!.isDirectory)
        destination.writeText(payload)
        return destination.canonicalFile
    }

    @Test
    fun recordsAndReloadsSuccessfulManagedInstall() {
        val appPrivate = Files.createTempDirectory("flame-manifest-app").toFile()
        val instance = Files.createTempDirectory("flame-manifest-instance").toFile()
        val plan = plan(instance)
        val installedFile = writeInstalledFile(plan)
        val store = MinecraftContentInstallManifestStore(appPrivate)

        val saved = store.recordSuccessfulInstall(
            plan,
            MinecraftContentInstallTransactionResult.Installed(listOf(installedFile.path))
        )

        assertTrue("Expected saved manifest, got $saved", saved is MinecraftContentInstallManifestResult.Saved)
        val loaded = store.load(instance)
        assertTrue("Expected loaded manifest, got $loaded", loaded is MinecraftContentInstallManifestResult.Loaded)
        val manifest = (loaded as MinecraftContentInstallManifestResult.Loaded).manifest
        assertEquals(instance.canonicalPath, manifest.instanceContentDirectory)
        assertEquals(1, manifest.entries.size)
        assertEquals("test-project", manifest.entries.single().projectId)
        assertEquals(installedFile.path, manifest.entries.single().installedPath)
        assertTrue(store.verifyManagedFile(manifest.entries.single()))
    }

    @Test
    fun datapackRecordIsBoundToSelectedWorldDatapacksDirectory() {
        val appPrivate = Files.createTempDirectory("flame-manifest-world-app").toFile()
        val instance = Files.createTempDirectory("flame-manifest-world-instance").toFile()
        val world = Files.createTempDirectory("flame-selected-world").toFile()
        val plan = plan(instance, MinecraftContentType.DATAPACK, world)
        val installedFile = writeInstalledFile(plan)
        val store = MinecraftContentInstallManifestStore(appPrivate)

        val saved = store.recordSuccessfulInstall(
            plan,
            MinecraftContentInstallTransactionResult.Installed(listOf(installedFile.path))
        )

        assertTrue("Expected saved datapack manifest, got $saved", saved is MinecraftContentInstallManifestResult.Saved)
        val loaded = store.load(instance) as MinecraftContentInstallManifestResult.Loaded
        val entry = loaded.manifest.entries.single()
        assertEquals(MinecraftContentType.DATAPACK, entry.contentType)
        assertEquals(world.canonicalPath, entry.selectedWorldDirectory)
        assertEquals(File(world, "datapacks/test-pack.zip").canonicalPath, entry.installedPath)
        assertTrue(store.verifyManagedFile(entry))
    }

    @Test
    fun refusesToRecordAFileOutsideItsContentTypeDirectory() {
        val appPrivate = Files.createTempDirectory("flame-manifest-escape-app").toFile()
        val instance = Files.createTempDirectory("flame-manifest-escape-instance").toFile()
        val plan = plan(instance)
        val outside = Files.createTempFile("flame-managed-escape", ".jar").toFile().apply {
            writeText("not inside mods")
        }
        val forgedPlan = plan.copy(items = plan.items.map {
            it.copy(destinationPath = outside.absolutePath)
        })
        val store = MinecraftContentInstallManifestStore(appPrivate)

        val result = store.recordSuccessfulInstall(
            forgedPlan,
            MinecraftContentInstallTransactionResult.Installed(listOf(outside.canonicalPath))
        )

        assertTrue("Expected path rejection, got $result", result is MinecraftContentInstallManifestResult.Failed)
        val loaded = store.load(instance) as MinecraftContentInstallManifestResult.Loaded
        assertTrue(loaded.manifest.entries.isEmpty())
    }

    @Test
    fun detectsManagedFileChangedAfterInstallation() {
        val appPrivate = Files.createTempDirectory("flame-manifest-tamper-app").toFile()
        val instance = Files.createTempDirectory("flame-manifest-tamper-instance").toFile()
        val plan = plan(instance)
        val installedFile = writeInstalledFile(plan)
        val store = MinecraftContentInstallManifestStore(appPrivate)
        val saved = store.recordSuccessfulInstall(
            plan,
            MinecraftContentInstallTransactionResult.Installed(listOf(installedFile.path))
        )
        assertTrue(saved is MinecraftContentInstallManifestResult.Saved)
        val entry = (saved as MinecraftContentInstallManifestResult.Saved).manifest.entries.single()

        installedFile.writeText("changed by user")

        assertFalse(store.verifyManagedFile(entry))
    }

    @Test
    fun missingManifestLoadsAsEmptyWithoutCreatingOne() {
        val appPrivate = Files.createTempDirectory("flame-manifest-empty-app").toFile()
        val instance = Files.createTempDirectory("flame-manifest-empty-instance").toFile()
        val result = MinecraftContentInstallManifestStore(appPrivate).load(instance)

        assertTrue(result is MinecraftContentInstallManifestResult.Loaded)
        assertFalse((result as MinecraftContentInstallManifestResult.Loaded).existed)
        assertTrue(result.manifest.entries.isEmpty())
        assertEquals(0, appPrivate.listFiles()?.size ?: 0)
    }
}
