package com.flame.launcher.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftContentInstallPlannerTest {
    private val target = MinecraftInstanceTarget(
        minecraftVersion = "1.20.1",
        loader = MinecraftLoader.FABRIC,
        contentDirectory = "/instances/survival"
    )

    private fun item(
        type: MinecraftContentType,
        fileName: String = "example.jar",
        url: String = "https://cdn.modrinth.com/data/example/file",
        projectId: String = type.name.lowercase()
    ): MinecraftContentInstallItem {
        val project = MinecraftContentProject(
            provider = ContentProviderId.MODRINTH,
            projectId = projectId,
            slug = projectId,
            title = projectId,
            description = "",
            contentType = type,
            author = "Test"
        )
        val version = MinecraftContentVersion(
            provider = ContentProviderId.MODRINTH,
            projectId = projectId,
            versionId = "version-1",
            versionNumber = "1.0",
            displayName = "1.0",
            gameVersions = setOf("1.20.1"),
            loaders = setOf(MinecraftLoader.FABRIC),
            fileName = fileName,
            downloadUrl = url,
            fileSizeBytes = 1024,
            sha1 = "a".repeat(40),
            sha512 = "b".repeat(128)
        )
        return MinecraftContentInstallItem(project, version)
    }

    private fun readyPlan(
        item: MinecraftContentInstallItem,
        target: MinecraftInstanceTarget = this.target,
        staging: String? = null
    ): MinecraftContentInstallPlan {
        val result = MinecraftContentInstallPlanner.plan(listOf(item), target, staging)
        assertTrue("Expected a ready plan, got $result", result is MinecraftInstallPlanResult.Ready)
        return (result as MinecraftInstallPlanResult.Ready).plan
    }

    @Test
    fun modInstallsIntoModsDirectory() {
        val planned = readyPlan(item(MinecraftContentType.MOD)).items.single()
        assertEquals("/instances/survival/mods/example.jar", planned.destinationPath)
        assertEquals(MinecraftInstallAction.INSTALL_FILE, planned.action)
    }

    @Test
    fun resourcePackInstallsIntoResourcepacksDirectory() {
        val planned = readyPlan(item(MinecraftContentType.RESOURCE_PACK)).items.single()
        assertEquals("/instances/survival/resourcepacks/example.jar", planned.destinationPath)
    }

    @Test
    fun shaderInstallsIntoShaderpacksDirectory() {
        val planned = readyPlan(item(MinecraftContentType.SHADER)).items.single()
        assertEquals("/instances/survival/shaderpacks/example.jar", planned.destinationPath)
    }

    @Test
    fun datapackInstallsIntoSelectedWorld() {
        val planned = readyPlan(
            item(MinecraftContentType.DATAPACK, fileName = "pack.zip"),
            target.copy(selectedWorldDirectory = "/worlds/survival")
        ).items.single()
        assertEquals("/worlds/survival/datapacks/pack.zip", planned.destinationPath)
    }

    @Test
    fun modpackIsStagedForImportAndNotInstalledIntoGameDirectory() {
        val planned = readyPlan(
            item(MinecraftContentType.MODPACK, fileName = "pack.mrpack"),
            staging = "/data/user/0/com.flame.launcher/files/imports"
        ).items.single()
        assertEquals(
            "/data/user/0/com.flame.launcher/files/imports/pack.mrpack",
            planned.destinationPath
        )
        assertEquals(MinecraftInstallAction.STAGE_MODPACK_ARCHIVE, planned.action)
    }

    @Test
    fun traversalFileNameIsRejected() {
        val result = MinecraftContentInstallPlanner.plan(
            listOf(item(MinecraftContentType.MOD, fileName = "../evil.jar")),
            target
        )
        assertTrue(result is MinecraftInstallPlanResult.Rejected)
    }

    @Test
    fun traversalInstanceDirectoryIsRejected() {
        val result = MinecraftContentInstallPlanner.plan(
            listOf(item(MinecraftContentType.MOD)),
            target.copy(contentDirectory = "/instances/../shared")
        )
        assertTrue(result is MinecraftInstallPlanResult.Rejected)
    }

    @Test
    fun untrustedDownloadHostIsRejected() {
        val result = MinecraftContentInstallPlanner.plan(
            listOf(item(MinecraftContentType.MOD, url = "https://attacker.invalid/evil.jar")),
            target
        )
        assertTrue(result is MinecraftInstallPlanResult.Rejected)
    }

    @Test
    fun datapackWithoutWorldIsRejected() {
        val result = MinecraftContentInstallPlanner.plan(
            listOf(item(MinecraftContentType.DATAPACK)),
            target
        )
        assertTrue(result is MinecraftInstallPlanResult.Rejected)
    }

    @Test
    fun invalidChecksumIsRejected() {
        val invalid = item(MinecraftContentType.MOD).let { item ->
            item.copy(version = item.version.copy(sha1 = "not-a-checksum"))
        }
        val result = MinecraftContentInstallPlanner.plan(listOf(invalid), target)
        assertTrue(result is MinecraftInstallPlanResult.Rejected)
    }

    @Test
    fun emptySelectionIsRejected() {
        assertFalse(
            MinecraftContentInstallPlanner.plan(emptyList(), target) is MinecraftInstallPlanResult.Ready
        )
    }
}
