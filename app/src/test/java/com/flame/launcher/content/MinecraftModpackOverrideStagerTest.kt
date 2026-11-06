package com.flame.launcher.content

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftModpackOverrideStagerTest {
    @Test
    fun stagesClientOverridesWithoutCommittingAndSkipsServerFiles() {
        val instance = Files.createTempDirectory("flame-stager-instance").toFile()
        val staging = Files.createTempDirectory("flame-stager-stage").toFile()
        val archive = createArchive(
            "overrides/config/options.txt" to "client config",
            "server-overrides/config/server.txt" to "server config"
        )
        try {
            val inspection = MinecraftModpackArchiveInspector().inspect(archive)
                as MinecraftModpackArchiveInspector.Result.Accepted
            val result = MinecraftModpackOverrideStager().stage(
                archive, inspection.inspection, instance, staging
            )
            assertTrue("Expected staged overrides: $result", result is MinecraftModpackOverrideStageResult.Staged)
            val staged = result as MinecraftModpackOverrideStageResult.Staged
            assertEquals(1, staged.files.size)
            assertEquals(1, staged.skippedServerFiles)
            assertTrue(staged.files.single().stagedFile.readText() == "client config")
            assertFalse(File(instance, "config/options.txt").exists())
            assertTrue(staged.files.single().destination.path.endsWith("config/options.txt"))
        } finally {
            instance.deleteRecursively()
            staging.deleteRecursively()
            archive.delete()
        }
    }

    @Test
    fun rejectsExistingOverrideDestinationBeforeStaging() {
        val instance = Files.createTempDirectory("flame-stager-existing").toFile()
        val staging = Files.createTempDirectory("flame-stager-existing-stage").toFile()
        val archive = createArchive("overrides/config/options.txt" to "new")
        try {
            File(instance, "config").mkdirs()
            File(instance, "config/options.txt").writeText("keep")
            val inspection = MinecraftModpackArchiveInspector().inspect(archive)
                as MinecraftModpackArchiveInspector.Result.Accepted
            val result = MinecraftModpackOverrideStager().stage(
                archive, inspection.inspection, instance, staging
            )
            assertTrue(result is MinecraftModpackOverrideStageResult.Failed)
            assertEquals("keep", File(instance, "config/options.txt").readText())
        } finally {
            instance.deleteRecursively()
            staging.deleteRecursively()
            archive.delete()
        }
    }

    private fun createArchive(vararg entries: Pair<String, String>): File {
        val archive = Files.createTempFile("flame-stager", ".mrpack").toFile()
        ZipOutputStream(archive.outputStream()).use { zip ->
            val manifest = """{"formatVersion":1,"game":"minecraft","name":"Test","versionId":"test","dependencies":{"minecraft":"1.20.1"},"files":[]}"""
            zip.putNextEntry(ZipEntry("modrinth.index.json"))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
            entries.forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray())
                zip.closeEntry()
            }
        }
        return archive
    }
}
