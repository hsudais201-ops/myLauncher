package com.flame.launcher.content

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Read-only CurseForge Core API client for Minecraft content.
 *
 * CurseForge requires an application-approved API key. Keep the key in a secure
 * runtime configuration source and inject it; never commit it or place it in source.
 * Call this client from an IO dispatcher.
 */
class CurseForgeContentProvider(
    private val apiKey: String,
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) {
    init {
        require(apiKey.isNotBlank()) { "A CurseForge API key is required." }
        require(connectTimeoutMs > 0 && readTimeoutMs > 0)
    }

    @Throws(IOException::class)
    fun searchProjects(
        query: String,
        type: MinecraftContentType,
        minecraftVersion: String? = null,
        loader: MinecraftLoader? = null,
        offset: Int = 0,
        limit: Int = DEFAULT_PAGE_SIZE
    ): List<MinecraftContentProject> {
        require(offset >= 0) { "Offset cannot be negative." }
        require(limit in 1..MAX_PAGE_SIZE) { "Page size must be between 1 and $MAX_PAGE_SIZE." }
        val classId = type.toCurseForgeClassId()
            ?: throw IllegalArgumentException("CurseForge does not expose this content type through its standard catalog.")
        val parameters = linkedMapOf(
            "gameId" to MINECRAFT_GAME_ID.toString(),
            "classId" to classId.toString(),
            "index" to offset.toString(),
            "pageSize" to limit.toString(),
            "sortField" to "2",
            "sortOrder" to "desc"
        )
        query.trim().takeIf(String::isNotBlank)?.let { parameters["searchFilter"] = it }
        minecraftVersion?.takeIf(String::isNotBlank)?.let { parameters["gameVersion"] = it }
        loader?.toCurseForgeLoader()?.takeIf { it != 0 }?.let { parameters["modLoaderType"] = it.toString() }

        val data = JSONObject(get("/v1/mods/search", parameters)).optJSONArray("data") ?: JSONArray()
        return buildList {
            for (index in 0 until data.length()) {
                val mod = data.optJSONObject(index) ?: continue
                val id = mod.optLong("id", -1L)
                if (id <= 0L) continue
                val authors = mod.optJSONArray("authors")
                val author = authors?.optJSONObject(0)?.optString("name").orEmpty().ifBlank { "Unknown" }
                val logo = mod.optJSONObject("logo")?.optString("url")
                add(
                    MinecraftContentProject(
                        provider = ContentProviderId.CURSEFORGE,
                        projectId = id.toString(),
                        slug = mod.optString("slug").ifBlank { id.toString() },
                        title = mod.optString("name").ifBlank { id.toString() },
                        description = mod.optString("summary"),
                        contentType = type,
                        author = author,
                        downloads = mod.optLong("downloadCount", 0L).coerceAtLeast(0L),
                        iconUrl = logo?.takeIf(String::isNotBlank)
                    )
                )
            }
        }
    }

    @Throws(IOException::class)
    fun getProject(projectId: String, type: MinecraftContentType): MinecraftContentProject {
        val id = projectId.toLongOrNull()?.takeIf { it > 0L }
            ?: throw IllegalArgumentException("Invalid CurseForge project ID.")
        val mod = JSONObject(get("/v1/mods/$id", emptyMap())).optJSONObject("data")
            ?: throw IOException("CurseForge returned no project data.")
        val authors = mod.optJSONArray("authors")
        return MinecraftContentProject(
            provider = ContentProviderId.CURSEFORGE,
            projectId = mod.optLong("id", id).toString(),
            slug = mod.optString("slug").ifBlank { id.toString() },
            title = mod.optString("name").ifBlank { id.toString() },
            description = mod.optString("summary"),
            contentType = type,
            author = authors?.optJSONObject(0)?.optString("name").orEmpty().ifBlank { "Unknown" },
            downloads = mod.optLong("downloadCount", 0L).coerceAtLeast(0L),
            iconUrl = mod.optJSONObject("logo")?.optString("url")?.takeIf(String::isNotBlank)
        )
    }

    @Throws(IOException::class)
    fun getProjectVersions(
        projectId: String,
        minecraftVersion: String? = null,
        loader: MinecraftLoader? = null
    ): List<MinecraftContentVersion> {
        val id = projectId.toLongOrNull()?.takeIf { it > 0L }
            ?: throw IllegalArgumentException("Invalid CurseForge project ID.")
        val parameters = linkedMapOf("pageSize" to MAX_PAGE_SIZE.toString(), "index" to "0")
        minecraftVersion?.takeIf(String::isNotBlank)?.let { parameters["gameVersion"] = it }
        loader?.toCurseForgeLoader()?.takeIf { it != 0 }?.let { parameters["modLoaderType"] = it.toString() }
        val files = JSONObject(get("/v1/mods/$id/files", parameters)).optJSONArray("data") ?: JSONArray()
        return buildList {
            for (index in 0 until files.length()) {
                val file = files.optJSONObject(index) ?: continue
                if (!file.optBoolean("isAvailable", true)) continue
                val fileId = file.optLong("id", -1L)
                val fileName = file.optString("fileName")
                if (fileId <= 0L || !isSafeFileName(fileName)) continue
                val gameVersions = file.optJSONArray("gameVersions").toStringSet()
                if (minecraftVersion != null && minecraftVersion !in gameVersions) continue
                val fileLoader = file.optInt("modLoader", 0).toMinecraftLoader()
                if (loader != null && loader != MinecraftLoader.UNKNOWN &&
                    fileLoader != MinecraftLoader.UNKNOWN && loader != fileLoader
                ) continue
                val downloadUrl = file.optString("downloadUrl").takeIf(::isApprovedDownloadUrl)
                    ?: resolveDownloadUrl(id, fileId)
                    ?: continue
                val sha1 = file.optJSONArray("hashes").toHash(1)
                val dependencies = file.optJSONArray("dependencies").toDependencies()
                add(
                    MinecraftContentVersion(
                        provider = ContentProviderId.CURSEFORGE,
                        projectId = id.toString(),
                        versionId = fileId.toString(),
                        versionNumber = file.optString("displayName").ifBlank { fileId.toString() },
                        displayName = file.optString("displayName").ifBlank { fileName },
                        gameVersions = gameVersions,
                        loaders = setOfNotNull(fileLoader.takeIf { it != MinecraftLoader.UNKNOWN }),
                        fileName = fileName,
                        downloadUrl = downloadUrl,
                        fileSizeBytes = file.optLong("fileLength", -1L).takeIf { it > 0L },
                        sha1 = sha1,
                        dependencies = dependencies
                    )
                )
            }
        }
    }

    private fun resolveDownloadUrl(modId: Long, fileId: Long): String? = try {
        val json = JSONObject(get("/v1/mods/$modId/files/$fileId/download-url", emptyMap()))
        json.optString("data").takeIf(::isApprovedDownloadUrl)
    } catch (_: IOException) {
        null
    }

    private fun get(path: String, parameters: Map<String, String>): String {
        val query = parameters.entries.joinToString("&") { (key, value) ->
            "${key.encodeQueryComponent()}=${value.encodeQueryComponent()}"
        }
        val endpoint = URL(API_BASE + path + if (query.isEmpty()) "" else "?$query")
        val connection = connectionFactory(endpoint).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                val detail = connection.errorStream?.use { it.readBytesBounded(MAX_ERROR_BYTES) }
                    ?.toString(StandardCharsets.UTF_8).orEmpty()
                throw IOException("CurseForge returned HTTP $status${if (detail.isBlank()) "" else ": $detail"}")
            }
            val bytes = connection.inputStream.use { it.readBytesBounded(MAX_RESPONSE_BYTES) }
            return bytes.toString(StandardCharsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private fun String.encodeQueryComponent(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.name())

    private fun java.io.InputStream.readBytesBounded(maxBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) throw IOException("CurseForge response exceeded its safety limit.")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun JSONArray?.toStringSet(): Set<String> = buildSet {
        if (this@toStringSet == null) return@buildSet
        for (index in 0 until this@toStringSet.length()) {
            this@toStringSet.optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
    }

    private fun JSONArray?.toHash(algorithm: Int): String? {
        if (this == null) return null
        for (index in 0 until length()) {
            val hash = optJSONObject(index) ?: continue
            if (hash.optInt("algo") == algorithm) {
                return hash.optString("value").takeIf { it.matches(Regex("(?i)^[a-f0-9]{40}$")) }
            }
        }
        return null
    }

    private fun JSONArray?.toDependencies(): List<MinecraftContentDependency> = buildList {
        if (this@toDependencies == null) return@buildList
        for (index in 0 until this@toDependencies.length()) {
            val item = this@toDependencies.optJSONObject(index) ?: continue
            val dependencyId = item.optLong("modId", -1L)
            val type = when (item.optInt("relationType")) {
                1 -> DependencyType.EMBEDDED
                2 -> DependencyType.OPTIONAL
                3 -> DependencyType.REQUIRED
                5 -> DependencyType.INCOMPATIBLE
                else -> null
            } ?: continue
            if (dependencyId > 0L) add(MinecraftContentDependency(dependencyId.toString(), type = type))
        }
    }

    private fun MinecraftContentType.toCurseForgeClassId(): Int? = when (this) {
        MinecraftContentType.MOD -> 6
        MinecraftContentType.MODPACK -> 4471
        MinecraftContentType.RESOURCE_PACK -> 12
        MinecraftContentType.SHADER -> 6552
        MinecraftContentType.DATAPACK -> null
    }

    private fun MinecraftLoader.toCurseForgeLoader(): Int = when (this) {
        MinecraftLoader.UNKNOWN -> 0
        MinecraftLoader.FORGE -> 1
        MinecraftLoader.FABRIC -> 4
        MinecraftLoader.QUILT -> 5
        MinecraftLoader.NEOFORGE -> 6
    }

    private fun Int.toMinecraftLoader(): MinecraftLoader = when (this) {
        1 -> MinecraftLoader.FORGE
        4 -> MinecraftLoader.FABRIC
        5 -> MinecraftLoader.QUILT
        6 -> MinecraftLoader.NEOFORGE
        else -> MinecraftLoader.UNKNOWN
    }

    private fun isApprovedDownloadUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", true) &&
            uri.host?.lowercase() in APPROVED_DOWNLOAD_HOSTS &&
            uri.userInfo == null && uri.port == -1 && uri.fragment == null
    }.getOrDefault(false)

    private fun isSafeFileName(value: String): Boolean =
        value.isNotBlank() && value == value.trim() && value != "." && value != ".." &&
            value.none { it == '/' || it == '\\' || it == ':' || it.code < 32 || it.code == 127 }

    companion object {
        const val API_BASE = "https://api.curseforge.com"
        const val MINECRAFT_GAME_ID = 432
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 50
        const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
        const val MAX_ERROR_BYTES = 8 * 1024
        const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000
        const val DEFAULT_READ_TIMEOUT_MS = 20_000
        const val USER_AGENT = "FlameLauncher/0.1.0 (content-browser)"
        private val APPROVED_DOWNLOAD_HOSTS = setOf("mediafilez.forgecdn.net", "edge.forgecdn.net")
    }
}
