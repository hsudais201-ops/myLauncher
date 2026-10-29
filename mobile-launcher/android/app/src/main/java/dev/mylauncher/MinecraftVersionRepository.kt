package dev.mylauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class MinecraftVersion(
    val id: String,
    val type: String,
    val releaseTime: String
)

object MinecraftVersionRepository {
    private const val MANIFEST_URL =
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

    suspend fun fetchVersions(): List<MinecraftVersion> = withContext(Dispatchers.IO) {
        val connection = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MyLauncher/0.1.0")
        }

        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Version service returned HTTP ${connection.responseCode}")
            }

            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val versions = JSONObject(body).getJSONArray("versions")
            buildList {
                for (index in 0 until versions.length()) {
                    val item = versions.getJSONObject(index)
                    add(
                        MinecraftVersion(
                            id = item.getString("id"),
                            type = item.getString("type"),
                            releaseTime = item.optString("releaseTime", "")
                        )
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
