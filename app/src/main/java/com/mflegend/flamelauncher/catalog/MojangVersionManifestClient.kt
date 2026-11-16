package com.mflegend.flamelauncher.catalog

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class MojangVersion(
    val id: String,
    val type: String,
    val url: String,
    val releaseTime: String,
    val sha1: String?
)

data class MojangVersionManifest(
    val latestRelease: String?,
    val latestSnapshot: String?,
    val versions: List<MojangVersion>
)

/** Fetches Mojang public version metadata; this does not download game files. */
class MojangVersionManifestClient {
    fun fetch(): MojangVersionManifest {
        val connection = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "FlameLauncher-Android/0.1.0")
        }
        try {
            val status = connection.responseCode
            require(status in 200..299) { "Mojang manifest request failed (HTTP $status)." }
            val body = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_RESPONSE_BYTES) { "Mojang manifest is larger than expected." }
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8)
            }
            val root = JSONObject(body)
            val latest = root.optJSONObject("latest")
            val array = root.getJSONArray("versions")
            val versions = buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val id = item.optString("id")
                    val url = item.optString("url")
                    val approvedUrl = runCatching { MojangMetadataEndpointPolicy.isApproved(URL(url)) }.getOrDefault(false)
                    if (id.matches(Regex("[A-Za-z0-9._-]{1,100}")) && approvedUrl) {
                        add(MojangVersion(
                            id = id,
                            type = item.optString("type", "unknown"),
                            url = url,
                            releaseTime = item.optString("releaseTime"),
                            sha1 = item.optString("sha1").takeIf { it.isNotBlank() }
                        ))
                    }
                }
            }
            return MojangVersionManifest(
                latestRelease = latest?.optString("release")?.takeIf { it.isNotBlank() },
                latestSnapshot = latest?.optString("snapshot")?.takeIf { it.isNotBlank() },
                versions = versions
            )
        } finally {
            connection.disconnect()
        }
    }

    /** Fetches one official version JSON and verifies the manifest-provided SHA-1. */
    fun fetchVersionMetadata(
        version: MojangVersion,
        control: DownloadControl? = null
    ): ByteArray {
        control?.checkpoint()
        val metadataUrl = URL(version.url)
        require(MojangMetadataEndpointPolicy.isApproved(metadataUrl)) {
            "Version metadata URL is not an approved Mojang HTTPS endpoint."
        }
        val connection = (metadataUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "FlameLauncher-Android/0.1.0")
        }
        try {
            control?.checkpoint()
            val status = connection.responseCode
            require(status in 200..299) { "Version metadata request failed (HTTP $status)." }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    control?.checkpoint()
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_VERSION_METADATA_BYTES) { "Version metadata is larger than expected." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            control?.checkpoint()
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            require(json.optString("id") == version.id) { "Downloaded metadata version ID does not match the catalog." }
            val expectedSha1 = version.sha1
            if (!expectedSha1.isNullOrBlank()) {
                val actualSha1 = java.security.MessageDigest.getInstance("SHA-1")
                    .digest(bytes).joinToString("") { "%02x".format(it) }
                require(actualSha1.equals(expectedSha1, ignoreCase = true)) {
                    "Version metadata SHA-1 verification failed."
                }
            }
            return bytes
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024
        const val MAX_VERSION_METADATA_BYTES = 2 * 1024 * 1024
    }
}
