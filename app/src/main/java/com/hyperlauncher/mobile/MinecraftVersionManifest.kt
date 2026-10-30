package com.hyperlauncher.mobile

import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class MinecraftVersion(
    val id: String,
    val type: String,
    val releaseTime: String,
    val metadataUrl: String,
    val metadataSha1: String
)

/**
 * Reads Mojang's public version manifest. This retrieves metadata only; it
 * does not download game files or imply that a version is installed.
 */
object MinecraftVersionManifest {
    private const val MANIFEST_URL =
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    private const val MAX_MANIFEST_BYTES = 8 * 1024 * 1024
    private val safeVersionId = Regex("^[0-9][A-Za-z0-9._-]{0,63}$")
    private val safeSha1 = Regex("^[a-fA-F0-9]{40}$")
    private val allowedHosts = setOf("piston-meta.mojang.com", "launchermeta.mojang.com")

    fun fetch(): List<MinecraftVersion> {
        return parseManifest(downloadJson(MANIFEST_URL, MAX_MANIFEST_BYTES))
    }

    internal fun parseManifest(json: String): List<MinecraftVersion> {
        val versions = JSONObject(json).getJSONArray("versions")
        val result = ArrayList<MinecraftVersion>(versions.length())
        for (i in 0 until versions.length()) {
            val item = versions.getJSONObject(i)
            val id = item.optString("id")
            val type = item.optString("type")
            val metadataUrl = item.optString("url")
            val sha1 = item.optString("sha1")
            if (!safeVersionId.matches(id)) continue
            if (type != "release" && type != "snapshot") continue
            if (!isTrustedMojangUrl(metadataUrl) || !safeSha1.matches(sha1)) continue
            result += MinecraftVersion(
                id = id,
                type = type,
                releaseTime = item.optString("releaseTime"),
                metadataUrl = metadataUrl,
                metadataSha1 = sha1.lowercase()
            )
        }
        if (result.isEmpty()) {
            throw IllegalStateException("Version service returned no usable versions")
        }
        return result
    }

    internal fun isTrustedMojangUrl(value: String): Boolean {
        return try {
            val url = URL(value)
            url.protocol == "https" && url.host.lowercase() in allowedHosts &&
                url.userInfo == null && url.port in listOf(-1, 443)
        } catch (_: Exception) {
            false
        }
    }

    internal fun downloadJson(url: String, maxBytes: Int): String {
        require(isTrustedMojangUrl(url)) { "Untrusted Minecraft metadata URL" }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "HyperLauncher/0.1.0 (Android)")
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Minecraft metadata service returned HTTP $code")
            }
            if (connection.contentLengthLong > maxBytes) {
                throw IllegalStateException("Minecraft metadata is unexpectedly large")
            }
            return BufferedInputStream(connection.inputStream).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) {
                        throw IllegalStateException("Minecraft metadata exceeded the size limit")
                    }
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }
}
