package com.hyperlauncher.mobile

import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class MinecraftVersion(
    val id: String,
    val type: String,
    val releaseTime: String
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

    fun fetch(): List<MinecraftVersion> {
        val connection = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "HyperLauncher/0.1.0 (Android)")
        }

        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Version service returned HTTP $code")
            }
            val length = connection.contentLengthLong
            if (length > MAX_MANIFEST_BYTES) {
                throw IllegalStateException("Version manifest is unexpectedly large")
            }

            val json = BufferedInputStream(connection.inputStream).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_MANIFEST_BYTES) {
                        throw IllegalStateException("Version manifest exceeded the size limit")
                    }
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
            return parseManifest(json)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseManifest(json: String): List<MinecraftVersion> {
        val root = JSONObject(json)
        val versions = root.getJSONArray("versions")
        val result = ArrayList<MinecraftVersion>(versions.length())
        for (i in 0 until versions.length()) {
            val item = versions.getJSONObject(i)
            val id = item.optString("id")
            val type = item.optString("type")
            if (!safeVersionId.matches(id)) continue
            if (type != "release" && type != "snapshot") continue
            result += MinecraftVersion(
                id = id,
                type = type,
                releaseTime = item.optString("releaseTime")
            )
        }
        if (result.isEmpty()) {
            throw IllegalStateException("Version service returned no usable versions")
        }
        return result
    }
}
