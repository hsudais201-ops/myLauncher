package dev.mylauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object MinecraftInstallPlanRepository {
    suspend fun fetch(version: MinecraftVersion): MinecraftInstallPlan = withContext(Dispatchers.IO) {
        require(version.id.matches(Regex("[A-Za-z0-9._-]{1,100}")))
        require(version.url.startsWith("https://")) { "Refusing non-HTTPS metadata URL." }
        val connection = (URL(version.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MyLauncher/0.1.0")
        }
        val json = try {
            val status = connection.responseCode
            if (connection.url.protocol != "https") throw SecurityException("Metadata redirected away from HTTPS.")
            if (status !in 200..299) error("Metadata download returned HTTP $status")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
        MinecraftInstallPlanParser.parse(json, version.id)
    }
}
