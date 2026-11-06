package com.flame.launcher.content

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CurseForgeContentProviderTest {
    @Test
    fun requiresAnApplicationApiKey() {
        assertThrows(IllegalArgumentException::class.java) {
            CurseForgeContentProvider(" ")
        }
    }

    @Test
    fun searchMapsCurseForgeProjectsAndSendsApiKey() {
        var observedKey: String? = null
        var observedUrl: String? = null
        val provider = CurseForgeContentProvider(
            apiKey = "test-key",
            connectionFactory = { url ->
                observedUrl = url.toString()
                FakeConnection(url, """
                    {
                      "data": [{
                        "id": 12345,
                        "name": "Example Mod",
                        "slug": "example-mod",
                        "summary": "A test project",
                        "downloadCount": 987,
                        "authors": [{"name": "Example Author"}],
                        "logo": {"url": "https://media.forgecdn.net/logo.png"}
                      }]
                    }
                """.trimIndent()) { observedKey = it }
            }
        )

        val results = provider.searchProjects(
            query = "example",
            type = MinecraftContentType.MOD,
            minecraftVersion = "1.20.1",
            loader = MinecraftLoader.FABRIC
        )

        assertEquals(1, results.size)
        assertEquals(ContentProviderId.CURSEFORGE, results.single().provider)
        assertEquals("12345", results.single().projectId)
        assertEquals("Example Mod", results.single().title)
        assertEquals("Example Author", results.single().author)
        assertEquals(987L, results.single().downloads)
        assertEquals("test-key", observedKey)
        assertTrue(observedUrl.orEmpty().contains("gameId=432"))
        assertTrue(observedUrl.orEmpty().contains("classId=6"))
        assertTrue(observedUrl.orEmpty().contains("gameVersion=1.20.1"))
        assertTrue(observedUrl.orEmpty().contains("modLoaderType=4"))
    }

    @Test
    fun projectVersionsMapRequiredCurseForgeDependenciesForResolver() {
        val provider = CurseForgeContentProvider(
            apiKey = "test-key",
            connectionFactory = { url ->
                FakeConnection(url, """
                    {
                      "data": [{
                        "id": 54321,
                        "displayName": "Example file",
                        "fileName": "example-mod.jar",
                        "isAvailable": true,
                        "gameVersions": ["1.20.1"],
                        "modLoader": 4,
                        "downloadUrl": "https://edge.forgecdn.net/files/example.jar",
                        "fileLength": 1024,
                        "hashes": [{"algo": 1, "value": "0123456789abcdef0123456789abcdef01234567"}],
                        "dependencies": [
                          {"modId": 98765, "relationType": 3},
                          {"modId": 87654, "relationType": 2}
                        ]
                      }]
                    }
                """.trimIndent())
            }
        )

        val version = provider.getProjectVersions(
            projectId = "12345",
            minecraftVersion = "1.20.1",
            loader = MinecraftLoader.FABRIC
        ).single()

        assertEquals("54321", version.versionId)
        assertEquals(2, version.dependencies.size)
        assertEquals("98765", version.dependencies[0].projectId)
        assertEquals(DependencyType.REQUIRED, version.dependencies[0].type)
        assertEquals("87654", version.dependencies[1].projectId)
        assertEquals(DependencyType.OPTIONAL, version.dependencies[1].type)
        assertEquals("0123456789abcdef0123456789abcdef01234567", version.sha1)
    }

    @Test
    fun datapackSearchIsExplicitlyUnsupportedByCurseForgeCatalog() {
        val provider = CurseForgeContentProvider("test-key")
        assertThrows(IllegalArgumentException::class.java) {
            provider.searchProjects("", MinecraftContentType.DATAPACK)
        }
    }

    private class FakeConnection(
        url: URL,
        private val body: String,
        private val onApiKey: (String?) -> Unit = {}
    ) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun setRequestProperty(key: String, value: String) {
            super.setRequestProperty(key, value)
            if (key.equals("x-api-key", ignoreCase = true)) onApiKey(value)
        }

        override fun getRequestProperty(key: String): String? =
            super.getRequestProperty(key)
        override fun getResponseCode(): Int = 200
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
    }
}
