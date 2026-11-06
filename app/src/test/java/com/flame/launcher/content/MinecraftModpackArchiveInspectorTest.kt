package com.flame.launcher.content

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftModpackArchiveInspectorTest {
    @Test
    fun acceptsModrinthArchiveAndReportsOverrides() {
        val archive = archive(
            "modrinth.index.json" to "{}",
            "overrides/config/example.json" to "{}",
            "server-overrides/config/server.json" to "{}"
        )
        try {
            val result = MinecraftModpackArchiveInspector().inspect(archive)
            assertTrue(result is MinecraftModpackArchiveInspector.Result.Accepted)
            val inspection = (result as MinecraftModpackArchiveInspector.Result.Accepted).inspection
            assertEquals(3, inspection.entryCount)
            assertEquals(
                listOf("overrides/config/example.json", "server-overrides/config/server.json"),
                inspection.overrideFiles
            )
        } finally {
            archive.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun rejectsTraversalAndAbsolutePaths() {
        for (unsafe in listOf("../escape.txt", "overrides/../../escape.txt", "/absolute.txt", "C:/drive.txt", "folder\\escape.txt")) {
            val archive = archive("modrinth.index.json" to "{}", unsafe to "bad")
            try {
                val result = MinecraftModpackArchiveInspector().inspect(archive)
                assertTrue("Expected rejection for $unsafe", result is MinecraftModpackArchiveInspector.Result.Rejected)
            } finally {
                archive.parentFile?.deleteRecursively()
            }
        }
    }

    @Test
    fun rejectsDuplicatePathsAndMissingManifest() {
        val duplicate = archive("modrinth.index.json" to "{}", "assets/a.txt" to "one", "assets/a.txt" to "two")
        try {
            assertTrue(
                MinecraftModpackArchiveInspector().inspect(duplicate) is MinecraftModpackArchiveInspector.Result.Rejected
            )
        } finally {
            duplicate.parentFile?.deleteRecursively()
        }

        val missing = archive("readme.txt" to "not a pack")
        try {
            assertTrue(
                MinecraftModpackArchiveInspector().inspect(missing) is MinecraftModpackArchiveInspector.Result.Rejected
            )
        } finally {
            missing.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun enforcesEntryAndExpandedSizeLimits() {
        val tooMany = archive("modrinth.index.json" to "{}", "a.txt" to "a", "b.txt" to "b")
        try {
            val inspector = MinecraftModpackArchiveInspector(
                MinecraftModpackArchiveInspector.Limits(maxEntries = 2)
            )
            assertTrue(inspector.inspect(tooMany) is MinecraftModpackArchiveInspector.Result.Rejected)
        } finally {
            tooMany.parentFile?.deleteRecursively()
        }

        val tooLarge = archive("modrinth.index.json" to "{}", "large.txt" to "0123456789")
        try {
            val inspector = MinecraftModpackArchiveInspector(
                MinecraftModpackArchiveInspector.Limits(maxEntryBytes = 5)
            )
            assertTrue(inspector.inspect(tooLarge) is MinecraftModpackArchiveInspector.Result.Rejected)
        } finally {
            tooLarge.parentFile?.deleteRecursively()
        }
    }

    private fun archive(vararg entries: Pair<String, String>): File {
        val directory = Files.createTempDirectory("flame-mrpack-").toFile()
        val file = File(directory, "test.mrpack")
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return file
    }
}
