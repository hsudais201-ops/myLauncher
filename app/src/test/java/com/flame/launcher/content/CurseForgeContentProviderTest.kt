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
        override fun getResponseCode(): Int {
            onApiKey(getRequestProperty("x-api-key"))
            return 200
        }
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
    }
}
