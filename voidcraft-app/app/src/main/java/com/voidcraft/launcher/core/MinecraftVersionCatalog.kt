package com.voidcraft.launcher.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant

data class MinecraftLatestVersions(val release: String, val snapshot: String)

data class MinecraftVersionEntry(
    val id: String,
    val type: String,
    val metadataUrl: String,
    val updatedAt: String,
    val releaseTime: String,
    val sha1: String,
    val complianceLevel: Int?
)

data class MinecraftVersionCatalog(
    val latest: MinecraftLatestVersions,
    val versions: List<MinecraftVersionEntry>
) {
    fun find(id: String): MinecraftVersionEntry? = versions.firstOrNull { it.id == id }
}

data class MinecraftArtifactDownload(val url: String, val sha1: String?, val sizeBytes: Long?)

data class MinecraftAssetIndex(
    val id: String,
    val url: String,
    val sha1: String?,
    val sizeBytes: Long?,
    val totalSizeBytes: Long?
)

data class MinecraftVersionDetails(
    val id: String,
    val type: String,
    val mainClass: String?,
    val inheritsFrom: String?,
    val assets: String?,
    val assetIndex: MinecraftAssetIndex?,
    val clientDownload: MinecraftArtifactDownload?,
    val javaMajorVersion: Int?
)

/** URL rules for official Mojang launcher metadata and its distribution CDNs. */
object MojangUrlPolicy {
    val allowedHosts: Set<String> = setOf(
        "piston-meta.mojang.com",
        "launchermeta.mojang.com",
        "launcher.mojang.com",
        "piston-data.mojang.com",
        "resources.download.minecraft.net",
        "libraries.minecraft.net"
    )

    fun validate(urlText: String): URL {
        val url = try {
            URL(urlText)
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid metadata URL", error)
        }
        require(url.protocol.equals("https", ignoreCase = true)) { "Metadata URLs must use HTTPS" }
        require(url.host.isNotBlank() && url.host.lowercase() in allowedHosts) {
            "Metadata URL host is not an approved Mojang distribution host"
        }
        require(url.userInfo == null && url.ref == null) {
            "Metadata URLs must not include credentials or fragments"
        }
        require(url.port == -1 || url.port == 443) { "Non-standard HTTPS ports are not allowed" }
        return url
    }
}

class MinecraftVersionManifestParser {
    fun parse(json: String): MinecraftVersionCatalog {
        require(json.length in 1..MAX_MANIFEST_CHARS) { "Version manifest size is outside the supported range" }
        val root = parseObject(json)
        val latestObject = root.getAsJsonObject("latest")
            ?: throw IllegalArgumentException("Version manifest is missing latest")
        val latest = MinecraftLatestVersions(
            requiredString(latestObject, "release"),
            requiredString(latestObject, "snapshot")
        )
        validateVersionId(latest.release)
        validateVersionId(latest.snapshot)

        val versionArray = root.get("versions")
        require(versionArray != null && versionArray.isJsonArray) {
            "Version manifest is missing the versions array"
        }
        require(versionArray.asJsonArray.size() in 1..MAX_VERSIONS) {
            "Version manifest count is outside the supported range"
        }
        val seen = HashSet<String>()
        val versions = versionArray.asJsonArray.mapIndexed { index, element ->
            require(element.isJsonObject) { "Version entry " + index + " is not an object" }
            val item = element.asJsonObject
            val id = requiredString(item, "id")
            validateVersionId(id)
            require(seen.add(id)) { "Duplicate Minecraft version ID: " + id }
            val type = requiredString(item, "type")
            require(TYPE_PATTERN.matches(type)) { "Invalid version type for " + id }
            val url = requiredString(item, "url")
            MojangUrlPolicy.validate(url)
            val time = requiredString(item, "time")
            val releaseTime = requiredString(item, "releaseTime")
            validateTimestamp(time, "time", id)
            validateTimestamp(releaseTime, "releaseTime", id)
            val sha1 = requiredString(item, "sha1").lowercase()
            require(SHA1_PATTERN.matches(sha1)) { "Invalid SHA-1 in version entry " + id }
            val compliance = optionalInt(item, "complianceLevel")
            if (compliance != null) require(compliance in 0..1) { "Invalid compliance level for " + id }
            MinecraftVersionEntry(id, type, url, time, releaseTime, sha1, compliance)
        }
        require(latest.release in seen) { "Latest release is missing from the version list" }
        require(latest.snapshot in seen) { "Latest snapshot is missing from the version list" }
        return MinecraftVersionCatalog(latest, versions)
    }

    internal fun validateVersionId(value: String) {
        require(ID_PATTERN.matches(value)) { "Invalid Minecraft version ID: " + value }
    }

    private fun parseObject(json: String): JsonObject {
        val parsed = try {
            JsonParser.parseString(json.removePrefix("\uFEFF"))
        } catch (error: Exception) {
            throw IllegalArgumentException("Version metadata is not valid JSON", error)
        }
        require(parsed.isJsonObject) { "Version metadata root must be an object" }
        return parsed.asJsonObject
    }

    internal fun requiredString(obj: JsonObject, key: String): String {
        val element = obj.get(key)
        require(element != null && element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            "Missing or invalid string field: " + key
        }
        return element.asString.also { require(it.isNotBlank()) { "Empty string field: " + key } }
    }

    private fun optionalInt(obj: JsonObject, key: String): Int? {
        val element = obj.get(key) ?: return null
        require(element.isJsonPrimitive && element.asJsonPrimitive.isNumber) { "Invalid integer field: " + key }
        return try {
            element.asInt
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid integer field: " + key, error)
        }
    }

    private fun validateTimestamp(value: String, key: String, id: String) {
        try {
            Instant.parse(value)
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid " + key + " timestamp for " + id, error)
        }
    }

    companion object {
        private const val MAX_MANIFEST_CHARS = 5 * 1024 * 1024
        private const val MAX_VERSIONS = 20_000
        private val SHA1_PATTERN = Regex("[0-9a-fA-F]{40}")
        private val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,127}")
        private val TYPE_PATTERN = Regex("[a-z_]{1,32}")
    }
}

class MinecraftVersionDetailsParser {
    fun parse(json: String): MinecraftVersionDetails {
        require(json.length in 1..MAX_DETAILS_CHARS) { "Version detail size is outside the supported range" }
        val root = try {
            JsonParser.parseString(json.removePrefix("\uFEFF"))
        } catch (error: Exception) {
            throw IllegalArgumentException("Version detail is not valid JSON", error)
        }
        require(root.isJsonObject) { "Version detail root must be an object" }
        val obj = root.asJsonObject
        val id = stringField(obj, "id", true)!!
        MinecraftVersionManifestParser().validateVersionId(id)
        val type = stringField(obj, "type", true)!!
        require(TYPE_PATTERN.matches(type)) { "Invalid version type for " + id }

        val inherits = stringField(obj, "inheritsFrom", false)
        if (inherits != null) MinecraftVersionManifestParser().validateVersionId(inherits)
        val assetIndex = obj.getAsJsonObject("assetIndex")?.let { item ->
            MinecraftAssetIndex(
                id = stringField(item, "id", true)!!,
                url = MojangUrlPolicy.validate(stringField(item, "url", true)!!).toString(),
                sha1 = sha1Field(item, "sha1"),
                sizeBytes = longField(item, "size"),
                totalSizeBytes = longField(item, "totalSize")
            )
        }
        val client = obj.getAsJsonObject("downloads")?.getAsJsonObject("client")?.let { item ->
            MinecraftArtifactDownload(
                url = MojangUrlPolicy.validate(stringField(item, "url", true)!!).toString(),
                sha1 = sha1Field(item, "sha1"),
                sizeBytes = longField(item, "size")
            )
        }
        val javaMajor = obj.getAsJsonObject("javaVersion")?.let { item ->
            val major = longField(item, "majorVersion")
                ?: throw IllegalArgumentException("Invalid Java major version for " + id)
            require(major in 1..100) { "Java major version out of range for " + id }
            major.toInt()
        }
        return MinecraftVersionDetails(
            id = id,
            type = type,
            mainClass = stringField(obj, "mainClass", false),
            inheritsFrom = inherits,
            assets = stringField(obj, "assets", false),
            assetIndex = assetIndex,
            clientDownload = client,
            javaMajorVersion = javaMajor
        )
    }

    private fun stringField(obj: JsonObject, key: String, required: Boolean): String? {
        val element = obj.get(key)
        if (element == null || element.isJsonNull) {
            if (required) throw IllegalArgumentException("Missing field: " + key)
            return null
        }
        require(element.isJsonPrimitive && element.asJsonPrimitive.isString) { "Invalid string field: " + key }
        return element.asString.also {
            if (required) require(it.isNotBlank()) { "Empty string field: " + key }
        }
    }

    private fun sha1Field(obj: JsonObject, key: String): String? {
        val value = stringField(obj, key, false) ?: return null
        require(SHA1_PATTERN.matches(value)) { "Invalid SHA-1 field: " + key }
        return value.lowercase()
    }

    private fun longField(obj: JsonObject, key: String): Long? {
        val element = obj.get(key) ?: return null
        require(element.isJsonPrimitive && element.asJsonPrimitive.isNumber) { "Invalid number field: " + key }
        val value = try {
            element.asLong
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid number field: " + key, error)
        }
        require(value >= 0L) { "Negative number field: " + key }
        return value
    }

    companion object {
        private const val MAX_DETAILS_CHARS = 5 * 1024 * 1024
        private val TYPE_PATTERN = Regex("[a-z_]{1,32}")
        private val SHA1_PATTERN = Regex("[0-9a-fA-F]{40}")
    }
}

fun interface HttpsTextFetcher {
    @Throws(IOException::class)
    fun fetch(url: String, maxBytes: Int = 5 * 1024 * 1024): String
}

/** Bounded UTF-8 fetcher that validates HTTPS redirects and limits response size. */
class UrlConnectionTextFetcher(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000,
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) : HttpsTextFetcher {
    override fun fetch(url: String, maxBytes: Int): String {
        require(maxBytes in 1..MAX_FETCH_BYTES) { "maxBytes is outside the supported range" }
        require(connectTimeoutMillis > 0 && readTimeoutMillis > 0) { "Timeouts must be positive" }
        var current = MojangUrlPolicy.validate(url)
        var redirects = 0
        while (true) {
            val connection = connectionFactory(current)
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = connectTimeoutMillis
                connection.readTimeout = readTimeoutMillis
                val status = connection.responseCode
                if (status in 300..399) {
                    if (redirects >= MAX_REDIRECTS) throw IOException("Too many metadata redirects")
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("Metadata redirect is missing Location")
                    current = MojangUrlPolicy.validate(URL(current, location).toString())
                    redirects += 1
                    continue
                }
                if (status !in 200..299) throw IOException("Metadata request failed with HTTP " + status)
                if (connection.contentLengthLong > maxBytes) {
                    throw IOException("Metadata response exceeds the size limit")
                }
                val output = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > maxBytes) throw IOException("Metadata response exceeds the size limit")
                        output.write(buffer, 0, count)
                    }
                }
                return String(output.toByteArray(), StandardCharsets.UTF_8)
            } finally {
                connection.disconnect()
            }
        }
    }

    companion object {
        private const val MAX_FETCH_BYTES = 5 * 1024 * 1024
        private const val MAX_REDIRECTS = 5
    }
}

class MinecraftVersionCatalogClient(
    private val fetcher: HttpsTextFetcher = UrlConnectionTextFetcher(),
    private val manifestParser: MinecraftVersionManifestParser = MinecraftVersionManifestParser(),
    private val detailsParser: MinecraftVersionDetailsParser = MinecraftVersionDetailsParser()
) {
    fun fetchCatalog(): MinecraftVersionCatalog =
        manifestParser.parse(fetcher.fetch(VERSION_MANIFEST_URL, MAX_METADATA_BYTES))

    fun fetchDetails(entry: MinecraftVersionEntry): MinecraftVersionDetails {
        MojangUrlPolicy.validate(entry.metadataUrl)
        val details = detailsParser.parse(fetcher.fetch(entry.metadataUrl, MAX_METADATA_BYTES))
        require(details.id == entry.id) {
            "Downloaded metadata ID " + details.id + " does not match requested version " + entry.id
        }
        return details
    }

    companion object {
        const val VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        private const val MAX_METADATA_BYTES = 5 * 1024 * 1024
    }
}
