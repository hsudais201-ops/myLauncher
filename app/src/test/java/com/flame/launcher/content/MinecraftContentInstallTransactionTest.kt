package com.flame.launcher.content

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftContentInstallTransactionTest {
    private val target = MinecraftInstanceTarget(
        minecraftVersion = "1.20.1",
        loader = MinecraftLoader.FABRIC,
        contentDirectory = "/unused"
    )

    private fun item(projectId: String, fileName: String): MinecraftContentInstallItem {
        val project = MinecraftContentProject(
            provider = ContentProviderId.MODRINTH,
            projectId = projectId,
            slug = projectId,
            title = projectId,
            description = "",
            contentType = MinecraftContentType.MOD,
            author = "Test"
        )
        val version = MinecraftContentVersion(
            provider = ContentProviderId.MODRINTH,
            projectId = projectId,
            versionId = "v1",
            versionNumber = "1.0",
            displayName = "1.0",
            gameVersions = setOf("1.20.1"),
            loaders = setOf(MinecraftLoader.FABRIC),
            fileName = fileName,
            downloadUrl = "https://cdn.modrinth.com/data/$projectId/file"
        )
        return MinecraftContentInstallItem(project, version)
    }

    private fun buildPlan(root: File, vararg selected: MinecraftContentInstallItem): MinecraftContentInstallPlan {
        val result = MinecraftContentInstallPlanner.plan(
            selected.toList(),
            target.copy(contentDirectory = root.absolutePath)
        )
        assertTrue("Expected valid plan: $result", result is MinecraftInstallPlanResult.Ready)
        return (result as MinecraftInstallPlanResult.Ready).plan
    }

    private fun stage(
        directory: File,
        install: MinecraftPlannedInstall,
        payload: ByteArray = "verified content".toByteArray()
    ): MinecraftStagedDownloadResult.Downloaded {
        val file = File(directory, "${install.projectId}-${install.versionId}.stage")
        file.writeBytes(payload)
        val withSize = install.copy(expectedSizeBytes = payload.size.toLong(), sha1 = null, sha512 = null)
        return MinecraftStagedDownloadResult.Downloaded(withSize, file, payload.size.toLong())
    }

    private fun withStagedSizes(plan: MinecraftContentInstallPlan) =
        plan.copy(items = plan.items.map { it.copy(expectedSizeBytes = "verified content".toByteArray().size.toLong()) })

    @Test
    fun installsVerifiedStagedFileIntoPlannedDirectory() {
        val root = Files.createTempDirectory("flame-instance").toFile()
        val staging = Files.createTempDirectory("flame-stage").toFile()
        val plan = withStagedSizes(buildPlan(root, item("alpha", "alpha.jar")))
        val download = stage(staging, plan.items.single())

        val result = MinecraftContentInstallTransaction().install(plan, listOf(download), staging)

        assertTrue("Expected install success: $result", result is MinecraftContentInstallTransactionResult.Installed)
        assertEquals("verified content", File(root, "mods/alpha.jar").readText())
    }

    @Test
    fun existingDestinationIsRejectedAndNeverChanged() {
        val root = Files.createTempDirectory("flame-instance-existing").toFile()
        val staging = Files.createTempDirectory("flame-stage-existing").toFile()
        val mods = File(root, "mods").apply { mkdirs() }
        File(mods, "alpha.jar").writeText("user file")
        val plan = withStagedSizes(buildPlan(root, item("alpha", "alpha.jar")))
        val download = stage(staging, plan.items.single())

        val result = MinecraftContentInstallTransaction().install(plan, listOf(download), staging)

        assertTrue(result is MinecraftContentInstallTransactionResult.Failed)
        assertEquals("user file", File(mods, "alpha.jar").readText())
    }

    @Test
    fun failureOnLaterFileRollsBackEarlierFiles() {
        val root = Files.createTempDirectory("flame-instance-rollback").toFile()
        val staging = Files.createTempDirectory("flame-stage-rollback").toFile()
        val plan = withStagedSizes(buildPlan(root, item("alpha", "alpha.jar"), item("beta", "beta.jar")))
        val downloads = plan.items.map { stage(staging, it) }
        val delegate = TestFileOperations()
        val failingOperations = object : MinecraftInstallFileOperations by delegate {
            private var moves = 0
            override fun moveNoReplace(source: File, destination: File) {
                moves += 1
                if (moves == 2) error("injected second-file failure")
                delegate.moveNoReplace(source, destination)
            }
        }

        val result = MinecraftContentInstallTransaction(failingOperations).install(plan, downloads, staging)

        assertTrue("Expected transaction failure: $result", result is MinecraftContentInstallTransactionResult.Failed)
        assertFalse(File(root, "mods/alpha.jar").exists())
        assertFalse(File(root, "mods/beta.jar").exists())
        assertTrue((result as MinecraftContentInstallTransactionResult.Failed).rolledBackPaths.any {
            it.endsWith("alpha.jar")
        })
    }

    @Test
    fun stagedFileOutsideStagingRootIsRejected() {
        val root = Files.createTempDirectory("flame-instance-escape").toFile()
        val staging = Files.createTempDirectory("flame-stage-escape").toFile()
        val outside = Files.createTempFile("flame-outside", ".jar").toFile().apply {
            writeText("verified content")
        }
        val plan = withStagedSizes(buildPlan(root, item("alpha", "alpha.jar")))
        val download = MinecraftStagedDownloadResult.Downloaded(
            plan.items.single(), outside, outside.length()
        )

        val result = MinecraftContentInstallTransaction().install(plan, listOf(download), staging)

        assertTrue(result is MinecraftContentInstallTransactionResult.Failed)
        assertFalse(File(root, "mods/alpha.jar").exists())
    }

    @Test
    fun datapackIsInstalledOnlyIntoSelectedWorldDatapacksDirectory() {
        val root = Files.createTempDirectory("flame-world").toFile()
        val staging = Files.createTempDirectory("flame-stage-datapack").toFile()
        val selected = item("pack", "pack.zip").copy(
            project = item("pack", "pack.zip").project.copy(contentType = MinecraftContentType.DATAPACK)
        )
        val result = MinecraftContentInstallPlanner.plan(
            listOf(selected),
            target.copy(selectedWorldDirectory = root.absolutePath)
        )
        assertTrue(result is MinecraftInstallPlanResult.Ready)
        val plan = withStagedSizes((result as MinecraftInstallPlanResult.Ready).plan)
        val download = stage(staging, plan.items.single())

        val installed = MinecraftContentInstallTransaction().install(plan, listOf(download), staging)

        assertTrue(installed is MinecraftContentInstallTransactionResult.Installed)
        assertTrue(File(root, "datapacks/pack.zip").isFile)
        assertFalse(File(root, "mods/pack.zip").exists())
    }

    private class TestFileOperations : MinecraftInstallFileOperations {
        override fun canonical(file: File): File = file.canonicalFile
        override fun exists(file: File): Boolean = Files.exists(file.toPath())
        override fun isRegularFile(file: File): Boolean = Files.isRegularFile(file.toPath())
        override fun canRead(file: File): Boolean = file.canRead()
        override fun mkdirs(directory: File): Boolean = directory.mkdirs() || directory.isDirectory
        override fun copyAndSync(source: File, destination: File) {
            destination.writeBytes(source.readBytes())
        }
        override fun moveNoReplace(source: File, destination: File) {
            Files.move(source.toPath(), destination.toPath())
        }
        override fun delete(file: File): Boolean = Files.deleteIfExists(file.toPath())
    }
}
