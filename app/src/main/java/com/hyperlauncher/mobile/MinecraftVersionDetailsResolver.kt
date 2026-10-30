package com.hyperlauncher.mobile

import org.json.JSONObject
import java.net.URL

data class MinecraftDownload(
    val url: String,
    val sha1: String,
    val size: Long
)

data class MinecraftVersionDetails(
    val id: String,
    val mainClass: String,
    val client: MinecraftDownload,
    val assetIndexId: String?,
    val assetIndex: MinecraftDownload?
)

/**
 * Resolves the official per-version JSON into the minimum data needed to plan
 * a verified client download. Actual installation is deliberately separate.
 */
object MinecraftVersionDetailsResolver {
    private val sha1Pattern = Regex("^[a-fA-F0-9]{40}$")
    private val allowedDownloadHosts = setOf(
        "piston-data.mojang.com",
        "piston-meta.mojang.com",
        "launchermeta.mojang.com",
        "launcher.mojang.com",
        "resources.download.minecraft.net",
        "libraries.minecraft.net"
    )

    fun fetch(version: MinecraftVersion): MinecraftVersionDetails {
        val json = MinecraftVersionManifest.downloadJson(version.metadataUrl, 16 * 1024 * 1024)
        val actualHash = sha1(json.toByteArray(Charsets.UTF_8))
        require(actualHash.equals(version.metadataSha1, ignoreCase = true)) {
            "Minecraft version metadata checksum did not match"
        }
        return parse(version.id, json)
    }

    internal fun parse(expectedId: String, json: String): MinecraftVersionDetails {
        val root = JSONObject(json)
        val actualId = root.optString("id")
        require(actualId == expectedId) { "Version metadata ID did not match the selected version" }
        val clientObject = root.getJSONObject("downloads").getJSONObject("client")
        val client = parseDownload(clientObject)
        val assetObject = root.optJSONObject("assetIndex")
        val assetIndexId = assetObject?.optString("id")?.takeIf { it.isNotBlank() }
        val assetIndex = assetObject?.let { parseDownload(it) }
        require((assetIndex == null) == (assetIndexId == null)) { "Asset index metadata is incomplete" }
        val mainClass = root.optString("mainClass")
        require(mainClass.isNotBlank() && mainClass.length <= 256) { "Version metadata has no valid main class" }
        return MinecraftVersionDetails(actualId, mainClass, client, assetIndexId, assetIndex)
    }

    private fun parseDownload(item: JSONObject): MinecraftDownload {
        val url = item.optString("url")
        val sha1 = item.optString("sha1")
        val size = item.optLong("size", -1L)
        require(isTrustedDownloadUrl(url)) { "Minecraft download URL is not trusted" }
        require(sha1Pattern.matches(sha1)) { "Minecraft download has an invalid SHA-1 checksum" }
        require(size > 0L) { "Minecraft download has an invalid file size" }
        return MinecraftDownload(url, sha1.lowercase(), size)
    }

    internal fun isTrustedDownloadUrl(value: String): Boolean {
        return try {
            val url = URL(value)
            url.protocol == "https" && url.host.lowercase() in allowedDownloadHosts &&
                url.userInfo == null && url.port in listOf(-1, 443)
        } catch (_: Exception) {
            false
        }
    }

    private fun sha1(value: ByteArray): String {
        return java.security.MessageDigest.getInstance("SHA-1")
            .digest(value)
            .joinToString("") { "%02x".format(it) }
    }
}
