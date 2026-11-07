package com.flame.launcher.runtime

import org.json.JSONException
import org.json.JSONObject

/**
 * Parsed entry from Mojang's version_manifest_v2.json.
 *
 * This is metadata only: a version is not considered installed or launchable
 * until its version JSON, libraries, assets, and client JAR are verified.
 */
data class MinecraftVersionManifest(
    val latestRelease: String,
    val latestSnapshot: String,
    val versions: List<MinecraftVersionEntry>
) {
    fun findVersion(id: String): MinecraftVersionEntry? =
        versions.firstOrNull { it.id == id }

    companion object {
        fun parse(json: String): MinecraftVersionManifest {
            require(json.toByteArray(Charsets.UTF_8).size in 1..MAX_MANIFEST_BYTES) {
                "Minecraft version manifest is empty or exceeds the size limit."
            }
            val root = try {
                JSONObject(json)
            } catch (failure: JSONException) {
                throw IllegalArgumentException("Minecraft version manifest is not valid JSON.", failure)
            }
            val latest = root.optJSONObject("latest")
                ?: throw IllegalArgumentException("Minecraft version manifest is missing latest metadata.")
            val release = latest.optString("release").takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("Minecraft version manifest is missing the latest release.")
            val snapshot = latest.optString("snapshot").takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("Minecraft version manifest is missing the latest snapshot.")
            val rawVersions = root.optJSONArray("versions")
                ?: throw IllegalArgumentException("Minecraft version manifest is missing its versions list.")

            require(rawVersions.length() in 1..MAX_VERSION_ENTRIES) {
                "Minecraft version manifest has an invalid number of entries."
            }
            val entries = ArrayList<MinecraftVersionEntry>(rawVersions.length())
            val seenIds = HashSet<String>()
            for (index in 0 until rawVersions.length()) {
                val item = rawVersions.optJSONObject(index)
                    ?: throw IllegalArgumentException("Minecraft version entry $index is not an object.")
                val id = item.optString("id")
                val type = item.optString("type")
                val url = item.optString("url")
                val sha1 = item.optString("sha1")
                val releaseTime = item.optString("releaseTime")
                val complianceLevel = item.optInt("complianceLevel", 0)

                require(ID_PATTERN.matches(id)) { "Minecraft version entry $index has an invalid ID." }
                require(type in SUPPORTED_TYPES) { "Minecraft version $id has an unsupported type." }
                require(isTrustedMetadataUrl(url)) { "Minecraft version $id has an untrusted metadata URL." }
                require(SHA1_PATTERN.matches(sha1)) { "Minecraft version $id has an invalid SHA-1 digest." }
                require(releaseTime.isNotBlank()) { "Minecraft version $id is missing its release time." }
                require(complianceLevel >= 0) { "Minecraft version $id has an invalid compliance level." }
                require(seenIds.add(id)) { "Minecraft version manifest contains duplicate ID $id." }
                entries += MinecraftVersionEntry(
                    id = id,
                    type = type,
                    metadataUrl = url,
                    sha1 = sha1.lowercase(),
                    releaseTime = releaseTime,
                    complianceLevel = complianceLevel
                )
            }
            require(entries.any { it.id == release && it.type == "release" }) {
                "Latest release $release is not present in the manifest."
            }
            require(entries.any { it.id == snapshot && it.type == "snapshot" }) {
                "Latest snapshot $snapshot is not present in the manifest."
            }
            return MinecraftVersionManifest(release, snapshot, entries)
        }

        private const val MAX_MANIFEST_BYTES = 4 * 1024 * 1024
        private const val MAX_VERSION_ENTRIES = 10_000
        private val ID_PATTERN = Regex("[A-Za-z0-9._-]{1,64}")
        private val SHA1_PATTERN = Regex("[a-fA-F0-9]{40}")
        private val SUPPORTED_TYPES = setOf("release", "snapshot", "old_beta", "old_alpha")

        private fun isTrustedMetadataUrl(value: String): Boolean = try {
            val uri = java.net.URI(value)
            uri.scheme == "https" &&
                uri.host in setOf("piston-meta.mojang.com", "launchermeta.mojang.com") &&
                uri.userInfo == null &&
                uri.port in setOf(-1, 443)
        } catch (_: Exception) {
            false
        }
    }
}

data class MinecraftVersionEntry(
    val id: String,
    val type: String,
    val metadataUrl: String,
    val sha1: String,
    val releaseTime: String,
    val complianceLevel: Int
)
