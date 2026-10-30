package com.hyperlauncher.mobile

import org.json.JSONObject

data class MinecraftAsset(
    val name: String,
    val hash: String,
    val size: Long
) {
    val relativePath: String
        get() = "objects/${hash.take(2)}/$hash"

    val download: MinecraftDownload
        get() = MinecraftDownload(
            url = "https://resources.download.minecraft.net/${hash.take(2)}/$hash",
            sha1 = hash,
            size = size
        )
}

data class MinecraftAssetIndex(
    val id: String,
    val objects: List<MinecraftAsset>,
    val totalBytes: Long
)

/** Parses Mojang's asset index and maps objects to their content-addressed URLs. */
object MinecraftAssetIndexResolver {
    private val hashPattern = Regex("^[a-fA-F0-9]{40}$")
    private val safeNamePattern = Regex("^[A-Za-z0-9._/ -]{1,512}$")

    fun fetch(descriptor: MinecraftDownload, expectedId: String): MinecraftAssetIndex {
        val json = MinecraftVersionManifest.downloadJson(descriptor.url, 32 * 1024 * 1024)
        val actualHash = java.security.MessageDigest.getInstance("SHA-1")
            .digest(json.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        require(actualHash.equals(descriptor.sha1, ignoreCase = true)) {
            "Minecraft asset index checksum did not match"
        }
        require(json.toByteArray(Charsets.UTF_8).size.toLong() == descriptor.size) {
            "Minecraft asset index size did not match"
        }
        return parse(expectedId, json)
    }

    internal fun parse(expectedId: String, json: String): MinecraftAssetIndex {
        val root = JSONObject(json)
        val id = root.optString("id")
        require(id == expectedId) { "Asset index ID did not match the selected version" }
        val source = root.getJSONObject("objects")
        val assets = ArrayList<MinecraftAsset>(source.length())
        var total = 0L
        val keys = source.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            require(safeNamePattern.matches(name)) { "Asset index contains an unsafe object name" }
            val item = source.getJSONObject(name)
            val hash = item.optString("hash").lowercase()
            val size = item.optLong("size", -1L)
            require(hashPattern.matches(hash)) { "Asset index contains an invalid object hash" }
            require(size >= 0L) { "Asset index contains an invalid object size" }
            total = Math.addExact(total, size)
            assets += MinecraftAsset(name, hash, size)
        }
        return MinecraftAssetIndex(id, assets, total)
    }
}
