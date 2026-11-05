package com.flame.launcher.content

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Read-only client for the public Modrinth v2 API.
 *
 * Network calls are synchronous by design: callers must invoke this client from an IO
 * dispatcher or worker thread. Responses are size-bounded and redirects are restricted
 * to the Modrinth API host to avoid forwarding requests to arbitrary endpoints.
 */
class ModrinthContentProvider(
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS
) {
    init {
        require(userAgent.isNotBlank()) { "A descriptive Modrinth User-Agent is required." }
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

        val facets = JSONArray().put(JSONArray().put("project_type:${type.toModrinthProjectType()}"))
        if (!minecraftVersion.isNullOrBlank()) {
            facets.put(JSONArray().put("versions:$minecraftVersion"))
        }
        loader?.toModrinthLoader()?.let { facets.put(JSONArray().put("categories:$it")) }

        val parameters = linkedMapOf(
            "query" to query.trim(),
            "facets" to facets.toString(),
            "offset" to offset.toString(),
            "limit" to limit.toString(),
            "index" to "relevance"
        )
        val json = JSONObject(get("/search", parameters))
        val hits = json.optJSONArray("hits") ?: JSONArray()
        buildList {
            for (index in 0 until hits.length()) {
                val hit = hits.optJSONObject(index) ?: continue
                val projectId = hit.optString("project_id").ifBlank {
                    hit.optString("id")
                }
                if (projectId.isBlank()) continue
                add(
                    MinecraftContentProject(
                        provider = ContentProviderId.MODRINTH,
                        projectId = projectId,
                        slug = hit.optString("slug", projectId),
                        title = hit.optString("title", projectId),
                        description = hit.optString("description"),
                        contentType = type,
                        author = hit.optString("author", "Unknown"),
                        downloads = hit.optLong("downloads", 0L).coerceAtLeast(0L),
                        iconUrl = hit.optString("icon_url").takeIf(String::isNotBlank)
                    )
                )
            }
        }
    }

    @Throws(IOException::class)
    fun getProjectVersions(
        projectId: String,
        minecraftVersion: String? = null,
        loader: MinecraftLoader? = null
    ): List<MinecraftContentVersion> {
        require(projectId.matches(PROJECT_ID_PATTERN)) { "Invalid Modrinth project ID." }
        val parameters = linkedMapOf<String, String>()
        if (!minecraftVersion.isNullOrBlank()) {
            parameters["game_versions"] = JSONArray().put(minecraftVersion).toString()
        }
        loader?.toModrinthLoader()?.let {
            parameters["loaders"] = JSONArray().put(it).toString()
        }
        val json = JSONArray(get("/project/$projectId/version", parameters))
        return buildList {
            for (index in 0 until json.length()) {
                val version = json.optJSONObject(index) ?: continue
                val files = version.optJSONArray("files") ?: JSONArray()
                val primaryFile = (0 until files.length())
                    .mapNotNull { files.optJSONObject(it) }
                    .firstOrNull { it.optBoolean("primary", false) }
                    ?: files.optJSONObject(0)
                    ?: continue
                val hashes = primaryFile.optJSONObject("hashes") ?: JSONObject()
                val fileName = primaryFile.optString("filename")
                val downloadUrl = primaryFile.optString("url")
                if (fileName.isBlank() || !isApprovedDownloadUrl(downloadUrl)) continue

                val dependencies = version.optJSONArray("dependencies") ?: JSONArray()
                val mappedDependencies = buildList {
                    for (dependencyIndex in 0 until dependencies.length()) {
                        val dependency = dependencies.optJSONObject(dependencyIndex) ?: continue
                        val dependencyProject = dependency.optString("project_id")
                        val dependencyVersion = dependency.optString("version_id")
                        val dependencyType = dependency.optString("dependency_type")
                            .toDependencyType() ?: continue
                        if (dependencyProject.isNotBlank()) {
                            add(
                                MinecraftContentDependency(
                                    projectId = dependencyProject,
                                    versionId = dependencyVersion.takeIf(String::isNotBlank),
                                    type = dependencyType
                                )
                            )
                        }
                    }
                }
                add(
                    MinecraftContentVersion(
                        provider = ContentProviderId.MODRINTH,
                        projectId = projectId,
                        versionId = version.optString("id"),
                        versionNumber = version.optString("version_number"),
                        displayName = version.optString("name", version.optString("version_number")),
                        gameVersions = version.optJSONArray("game_versions").toStringSet(),
                        loaders = version.optJSONArray("loaders").toLoaderSet(),
                        fileName = fileName,
                        downloadUrl = downloadUrl,
                        fileSizeBytes = primaryFile.optLong("size", -1L).takeIf { it >= 0L },
                        sha1 = hashes.optString("sha1").takeIf(String::isNotBlank),
                        sha512 = hashes.optString("sha512").takeIf(String::isNotBlank),
                        dependencies = mappedDependencies
                    )
                )
            }
        }
    }

    private fun get(path: String, parameters: Map<String, String>): String {
        val query = parameters.entries.joinToString("&") { (key, value) ->
            "${key.encodeQueryComponent()}=${value.encodeQueryComponent()}"
        }
        val endpoint = URL(API_BASE + path + if (query.isEmpty()) "" else "?$query")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", userAgent)
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                val message = connection.errorStream?.use { stream ->
                    stream.readNBytes(MAX_ERROR_BYTES).toString(StandardCharsets.UTF_8)
                }.orEmpty()
                throw IOException("Modrinth returned HTTP $status${if (message.isBlank()) "" else ": $message"}")
            }
            val bytes = connection.inputStream.use { it.readNBytes(MAX_RESPONSE_BYTES + 1) }
            if (bytes.size > MAX_RESPONSE_BYTES) {
                throw IOException("Modrinth response exceeded the $MAX_RESPONSE_BYTES-byte safety limit.")
            }
            return bytes.toString(StandardCharsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private fun String.encodeQueryComponent(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.name())

    private fun JSONArray?.toStringSet(): Set<String> = buildSet {
        if (this@toStringSet == null) return@buildSet
        for (index in 0 until this@toStringSet.length()) {
            this@toStringSet.optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
    }

    private fun JSONArray?.toLoaderSet(): Set<MinecraftLoader> = buildSet {
        if (this@toLoaderSet == null) return@buildSet
        for (index in 0 until this@toLoaderSet.length()) {
            this@toLoaderSet.optString(index).toMinecraftLoader()?.let(::add)
        }
    }

    private fun String.toDependencyType(): DependencyType? = when (lowercase()) {
        "required" -> DependencyType.REQUIRED
        "optional" -> DependencyType.OPTIONAL
        "incompatible" -> DependencyType.INCOMPATIBLE
        "embedded" -> DependencyType.EMBEDDED
        else -> null
    }

    private fun String.toMinecraftLoader(): MinecraftLoader? = when (lowercase()) {
        "fabric" -> MinecraftLoader.FABRIC
        "forge" -> MinecraftLoader.FORGE
        "neoforge" -> MinecraftLoader.NEOFORGE
        "quilt" -> MinecraftLoader.QUILT
        else -> null
    }

    private fun MinecraftLoader.toModrinthLoader(): String? = when (this) {
        MinecraftLoader.FABRIC -> "fabric"
        MinecraftLoader.FORGE -> "forge"
        MinecraftLoader.NEOFORGE -> "neoforge"
        MinecraftLoader.QUILT -> "quilt"
        MinecraftLoader.UNKNOWN -> null
    }

    private fun MinecraftContentType.toModrinthProjectType(): String = when (this) {
        MinecraftContentType.MOD -> "mod"
        MinecraftContentType.MODPACK -> "modpack"
        MinecraftContentType.RESOURCE_PACK -> "resourcepack"
        MinecraftContentType.SHADER -> "shader"
        MinecraftContentType.DATAPACK -> "datapack"
    }

    private fun isApprovedDownloadUrl(value: String): Boolean = runCatching {
        val url = URL(value)
        url.protocol == "https" && url.host.equals("cdn.modrinth.com", ignoreCase = true) &&
            url.userInfo == null && url.port == -1 && url.ref == null
    }.getOrDefault(false)

    companion object {
        const val API_BASE = "https://api.modrinth.com/v2"
        const val DEFAULT_USER_AGENT = "FlameLauncher/0.1.0 (content-browser)"
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
        const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
        const val MAX_ERROR_BYTES = 8 * 1024
        const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000
        const val DEFAULT_READ_TIMEOUT_MS = 20_000
        private val PROJECT_ID_PATTERN = Regex("[A-Za-z0-9]{3,64}")
    }
}
