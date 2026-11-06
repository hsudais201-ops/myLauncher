package com.flame.launcher.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MinecraftModpackOverrideImporterTest {
    private val importer = MinecraftModpackOverrideImporter()
    private val inspector = MinecraftModpackArchiveInspector()

    @Test
    fun importsClientOverridesAndSkipsServerOverrides() {
        val root = Files.createTempDirectory("flame-instance").toFile()
        val staging = Files.createTempDirectory("flame-staging").toFile()
        val archive = createArchive(
            "overrides/config/example.txt" to "hello",
            "server-overrides/config/server.txt" to "server"
        )
        try {
            val inspection = inspector.inspect(archive) as MinecraftModpackArchiveInspector.Result.Accepted
            val result = importer.importOverrides(archive, inspection.inspection, root, staging)
            assertTrue(result is MinecraftModpackOverrideImportResult.Imported)
            val imported = result as MinecraftModpackOverrideImportResult.Imported
            assertEquals(1, imported.installedPaths.size)
            assertEquals(1, imported.skippedServerFiles)
            assertEquals("hello", File(root, "config/example.txt").readText())
            assertTrue(!File(root, "config/server.txt").exists())
        } finally {
            root.deleteRecursively()
            staging.deleteRecursively()
            archive.delete()
        }
    }

    @Test
    fun refusesToOverwriteExistingInstanceFiles() {
        val root = Files.createTempDirectory("flame-instance").toFile()
        val staging = Files.createTempDirectory("flame-staging").toFile()
        val archive = createArchive("overrides/config/options.txt" to "new")
        try {
            File(root, "config").mkdirs()
            File(root, "config/options.txt").writeText("keep")
            val inspection = inspector.inspect(archive) as MinecraftModpackArchiveInspector.Result.Accepted
            val result = importer.importOverrides(archive, inspection.inspection, root, staging)
            assertTrue(result is MinecraftModpackOverrideImportResult.Failed)
            assertEquals("keep", File(root, "config/options.txt").readText())
        } finally {
            root.deleteRecursively()
            staging.deleteRecursively()
            archive.delete()
        }
    }

    @Test
    fun rejectsArchiveWhoseBytesChangeWithoutChangingPathsOrManifest() {
        val root = Files.createTempDirectory("flame-instance").toFile()
        val staging = Files.createTempDirectory("flame-staging").toFile()
        val archive = createArchive("overrides/config/options.txt" to "first")
        try {
            val inspection = inspector.inspect(archive) as MinecraftModpackArchiveInspector.Result.Accepted
            createArchiveAt(archive, "overrides/config/options.txt" to "other")
            val result = importer.importOverrides(archive, inspection.inspection, root, staging)
            assertTrue(result is MinecraftModpackOverrideImportResult.Failed)
            assertTrue(!File(root, "config/options.txt").exists())
        } finally {
            root.deleteRecursively()
            staging.deleteRecursively()
            archive.delete()
        }
    }

    @Test
    fun rejectsArchiveThatWasChangedAfterInspection() {
        val root = Files.createTempDirectory("flame-instance").toFile()
        val staging = Files.createTempDirectory("flame-staging").toFile()
        val archive = createArchive("overrides/config/first.txt" to "first")
        try {
            val inspection = inspector.inspect(archive) as MinecraftModpackArchiveInspector.Result.Accepted
            createArchiveAt(archive, "overrides/config/second.txt" to "second")
            val result = importer.importOverrides(archive, inspection.inspection, root, staging)
            assertTrue(result is MinecraftModpackOverrideImportResult.Failed)
            assertTrue(!File(root, "config/second.txt").exists())
        } finally {
            root.deleteRecursively()
            staging.deleteRecursively()
            archive.delete()
        }
    }

    private fun createArchive(vararg entries: Pair<String, String>): File {
        val file = Files.createTempFile("flame-overrides", ".mrpack").toFile()
        createArchiveAt(file, *entries)
        return file
    }

    private fun createArchiveAt(file: File, vararg entries: Pair<String, String>) {
        ZipOutputStream(file.outputStream()).use { zip ->
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
    }
}
