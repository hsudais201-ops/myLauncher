package com.flame.launcher.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftModpackManifestParserTest {
    private val parser = MinecraftModpackManifestParser()

    @Test
    fun parsesValidManifestAndVerifiesFileMetadata() {
        val result = parser.parse(validManifest())
        assertTrue(result is MinecraftModpackManifestParser.Result.Parsed)
        val manifest = (result as MinecraftModpackManifestParser.Result.Parsed).manifest
        assertEquals("Example Pack", manifest.name)
        assertEquals("1.20.1", manifest.minecraftVersion)
        assertEquals("0.15.11", manifest.loaderDependencies["fabric-loader"])
        assertEquals("mods/example.jar", manifest.files.single().path)
        assertEquals(123L, manifest.files.single().fileSizeBytes)
    }

    @Test
    fun rejectsUnsafePathsUntrustedUrlsAndMissingHashes() {
        val unsafe = validManifest().replace("mods/example.jar", "../outside.jar")
        assertTrue(parser.parse(unsafe) is MinecraftModpackManifestParser.Result.Rejected)

        val untrusted = validManifest().replace("https://cdn.modrinth.com/", "https://evil.example/")
        assertTrue(parser.parse(untrusted) is MinecraftModpackManifestParser.Result.Rejected)

        val noHash = validManifest().replace("\"sha1\": \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",", "")
        assertTrue(parser.parse(noHash) is MinecraftModpackManifestParser.Result.Rejected)
    }

    @Test
    fun rejectsDuplicatePathsAndMalformedOrAmbiguousJson() {
        val duplicatePath = validManifest().replace(
            "\"files\": [",
            "\"files\": [{\"path\":\"mods/example.jar\",\"hashes\":{\"sha1\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"},\"downloads\":[\"https://cdn.modrinth.com/file\"],\"fileSize\":1},"
        )
        assertTrue(parser.parse(duplicatePath) is MinecraftModpackManifestParser.Result.Rejected)
        assertTrue(parser.parse(validManifest().dropLast(1)) is MinecraftModpackManifestParser.Result.Rejected)
        assertTrue(parser.parse(validManifest().replace("\"formatVersion\": 1", "\"formatVersion\": 1, \"formatVersion\": 1")) is MinecraftModpackManifestParser.Result.Rejected)
    }

    private fun validManifest() = """
        {
          "formatVersion": 1,
          "game": "minecraft",
          "name": "Example Pack",
          "versionId": "example-1",
          "dependencies": {"minecraft": "1.20.1", "fabric-loader": "0.15.11"},
          "files": [{
            "path": "mods/example.jar",
            "hashes": {"sha1": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
            "downloads": ["https://cdn.modrinth.com/data/example.jar"],
            "fileSize": 123,
            "env": {"client": "required", "server": "optional"}
          }]
        }
    """.trimIndent()
}
