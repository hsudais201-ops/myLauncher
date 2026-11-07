package com.flame.launcher.runtime

import org.json.JSONException
import org.json.JSONObject

/**
 * Parsed Mojang asset index. Asset object URLs are derived only from validated
 * SHA-1 hashes and the fixed Mojang resource host.
 */
data class MinecraftAssetIndex(
    val id: String,
    val objects: Map<String, MinecraftAssetObject>,
    val totalSize: Long?
) {
    companion object {
        fun parse(json: String, expectedId: String): MinecraftAssetIndex {
            require(expectedId.matches(ASSET_INDEX_ID)) { "Requested asset index ID is invalid." }
            require(json.toByteArray(Charsets.UTF_8).size in 1..MAX_INDEX_BYTES) {
                "Minecraft asset index is empty or exceeds the size limit."
            }
            val root = try {
                JSONObject(json)
            } catch (failure: JSONException) {
                throw IllegalArgumentException("Minecraft asset index is not valid JSON.", failure)
            }
            val id = root.optString("id")
            require(id == expectedId) { "Minecraft asset index ID does not match the requested index." }
            val rawObjects = root.optJSONObject("objects")
                ?: throw IllegalArgumentException("Minecraft asset index is missing its objects map.")
            require(rawObjects.length() <= MAX_ASSET_OBJECTS) {
                "Minecraft asset index contains too many objects."
            }
            val objects = LinkedHashMap<String, MinecraftAssetObject>()
            val keys = rawObjects.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                require(name.isNotBlank() && name.length <= MAX_ASSET_NAME_LENGTH) {
                    "Minecraft asset index contains an invalid asset name."
                }
                val value = rawObjects.optJSONObject(name)
                    ?: throw IllegalArgumentException("Minecraft asset $name has an invalid descriptor.")
                val hash = value.optString("hash")
                val size = value.optLong("size", -1L)
                require(SHA1.matches(hash)) { "Minecraft asset $name has an invalid SHA-1 hash." }
                require(size >= 0L) { "Minecraft asset $name has an invalid size." }
                objects[name] = MinecraftAssetObject(name, hash.lowercase(), size)
            }
            val totalSize = root.optLong("totalSize", -1L).takeIf { it >= 0L }
            return MinecraftAssetIndex(id, objects, totalSize)
        }

        private const val MAX_INDEX_BYTES = 16 * 1024 * 1024
        private const val MAX_ASSET_OBJECTS = 500_000
        private const val MAX_ASSET_NAME_LENGTH = 1024
        private val ASSET_INDEX_ID = Regex("[A-Za-z0-9._-]{1,64}")
        private val SHA1 = Regex("[a-fA-F0-9]{40}")
    }
}

data class MinecraftAssetObject(
    val name: String,
    val sha1: String,
    val size: Long
)

object MinecraftAssetDownloadPlanBuilder {
    fun build(index: MinecraftAssetIndex): List<MinecraftPlannedDownload> {
        val byPath = LinkedHashMap<String, MinecraftPlannedDownload>()
        index.objects.values.forEach { asset ->
            val path = "assets/objects/${asset.sha1.take(2)}/${asset.sha1}"
            val download = MinecraftPlannedDownload(
                kind = MinecraftPlannedDownloadKind.ASSET,
                relativePath = path,
                descriptor = MinecraftDownloadDescriptor(
                    url = "https://resources.download.minecraft.net/${asset.sha1.take(2)}/${asset.sha1}",
                    sha1 = asset.sha1,
                    size = asset.size,
                    path = path
                )
            )
            val previous = byPath[path]
            require(previous == null || previous.descriptor.size == asset.size) {
                "Minecraft asset hash maps to conflicting file sizes."
            }
            if (previous == null) byPath[path] = download
        }
        return byPath.values.toList()
    }
}
