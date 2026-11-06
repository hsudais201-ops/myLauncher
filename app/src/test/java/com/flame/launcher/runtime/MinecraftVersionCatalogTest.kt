package com.flame.launcher.runtime

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class MinecraftVersionCatalogTest {
    private class Response(
        url: URL,
        private val bytes: ByteArray
    ) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode(): Int = HTTP_OK
        override fun getContentLengthLong(): Long = bytes.size.toLong()
        override fun getInputStream(): InputStream = ByteArrayInputStream(bytes)
    }

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun loadsManifestThenVerifiedVersionDetails() {
        val details = """
            {
              "id":"1.21",
              "mainClass":"net.minecraft.client.main.Main",
              "assets":"17",
              "assetIndex":{
                "url":"https://piston-meta.mojang.com/assets/index.json",
                "sha1":"abcdef0123456789abcdef0123456789abcdef01",
                "size":10
              },
              "downloads":{
                "client":{
                  "url":"https://piston-data.mojang.com/client.jar",
                  "sha1":"0123456789abcdef0123456789abcdef01234567",
                  "size":20
                }
              },
              "libraries":[]
            }
        """.trimIndent().toByteArray()
        val digest = sha1(details)
        val versionUrl = "https://piston-meta.mojang.com/v1/packages/$digest/1.21.json"
        val manifest = """
            {
              "latest":{"release":"1.21","snapshot":"24w14a"},
              "versions":[
                {
                  "id":"1.21","type":"release","url":"$versionUrl",
                  "sha1":"$digest","releaseTime":"2024-06-13T00:00:00+00:00",
                  "complianceLevel":1
                },
                {
                  "id":"24w14a","type":"snapshot",
                  "url":"https://piston-meta.mojang.com/v1/packages/abcdef0123456789abcdef0123456789abcdef01/24w14a.json",
                  "sha1":"abcdef0123456789abcdef0123456789abcdef01",
                  "releaseTime":"2024-04-03T00:00:00+00:00","complianceLevel":1
                }
              ]
            }
        """.trimIndent().toByteArray()

        val catalog = MinecraftVersionCatalog(
            connectionFactory = { url ->
                val bytes = if (url.toString() == MinecraftMetadataDownloader.MANIFEST_URL) {
                    manifest
                } else {
                    details
                }
                Response(url, bytes)
            }
        )

        val loadedManifest = catalog.loadManifest()
        assertEquals("1.21", loadedManifest.latestRelease)
        val selected = loadedManifest.findVersion("1.21")!!
        val loadedDetails = catalog.loadVersionDetails(selected)
        assertEquals("1.21", loadedDetails.id)
        assertEquals("net.minecraft.client.main.Main", loadedDetails.mainClass)
    }
}
