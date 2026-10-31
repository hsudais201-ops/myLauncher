package com.hyperlauncher.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder


data class ModFileDownload(
    val descriptor: MinecraftDownload,
    val relativePath: String,
    val fileName: String
)

/**
 * Search adapter for the official Modrinth and CurseForge APIs.
 * CurseForge credentials are supplied at runtime and are never written to disk.
 */
object ModPlatformBrowser {
    private const val MODRINTH_BASE = "https://api.modrinth.com/v2"
    private const val CURSEFORGE_BASE = "https://api.curseforge.com/v1"
    private const val MINECRAFT_GAME_ID = 432
    private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    private const val MAX_MOD_FILE_BYTES = 2L * 1024L * 1024L * 1024L
    private val projectTypes = setOf("mod", "modpack", "resourcepack", "shader", "datapack")
    private val loaders = setOf("any", "fabric", "forge", "neoforge", "quilt")
    private val versionPattern = Regex("^[0-9][A-Za-z0-9._-]{0,31}$")
    private val cachedClassIds = mutableMapOf<String, Int>()

    fun search(
        source: String,
        query: String,
        projectType: String,
        gameVersion: String,
        loader: String,
        sort: String,
        curseForgeKey: String?,
        offset: Int = 0
    ): JSONObject {
        require(source == "modrinth" || source == "curseforge") { "Choose Modrinth or CurseForge" }
        require(projectTypes.contains(projectType)) { "Unsupported project type" }
        require(loaders.contains(loader)) { "Unsupported mod loader" }
        require(query.length <= 80) { "Search text is too long" }
        require(offset in 0..10_000) { "Search page is out of range" }
        val version = gameVersion.trim()
        require(version.isEmpty() || versionPattern.matches(version)) { "Enter a valid Minecraft version" }
        return if (source == "modrinth") {
            searchModrinth(query.trim(), projectType, version, loader, sort, offset)
        } else {
            searchCurseForge(query.trim(), projectType, version, loader, sort, curseForgeKey, offset)
        }
    }


    internal val modDownloadHosts = setOf(
        "cdn.modrinth.com",
        "cdn-raw.modrinth.com",
        "edge.forgecdn.net",
        "mediafilez.forgecdn.net",
        "media.forgecdn.net"
    )

    fun listProjectVersions(
        source: String,
        projectId: String,
        projectType: String,
        gameVersion: String,
        loader: String,
        curseForgeKey: String?
    ): JSONObject {
        require(projectTypes.contains(projectType)) { "Unsupported project type" }
        require(loaders.contains(loader)) { "Unsupported mod loader" }
        val version = gameVersion.trim()
        require(version.isEmpty() || versionPattern.matches(version)) { "Enter a valid Minecraft version" }
        val versions = when (source) {
            "modrinth" -> listModrinthVersions(projectId, projectType, version, loader)
            "curseforge" -> listCurseForgeVersions(projectId, projectType, version, loader, curseForgeKey)
            else -> throw IllegalArgumentException("Choose Modrinth or CurseForge")
        }
        return JSONObject().put("ok", true).put("source", source).put("versions", versions)
    }

    fun resolveFileDownload(
        source: String,
        projectId: String,
        fileId: String,
        fileName: String,
        projectType: String,
        gameVersion: String,
        loader: String,
        curseForgeKey: String?
    ): ModFileDownload {
        val versions = listProjectVersions(
            source, projectId, projectType, gameVersion, loader, curseForgeKey
        ).getJSONArray("versions")
        for (i in 0 until versions.length()) {
            val candidate = versions.getJSONObject(i)
            if (candidate.optString("fileId") != fileId ||
                candidate.optString("fileName") != fileName) continue
            val url = candidate.optString("downloadUrl")
            require(isTrustedModDownloadUrl(url)) { "This file has no supported official download URL" }
            val sha1 = candidate.optString("sha1")
            require(Regex("^[a-fA-F0-9]{40}$").matches(sha1)) {
                "The mod platform did not provide a SHA-1 checksum for this file"
            }
            val size = candidate.optLong("size", -1L)
            require(size in 1L..MAX_MOD_FILE_BYTES) {
                "The mod platform returned a file size outside the supported 2 GiB limit"
            }
            val safeName = safeModFileName(fileName)
            val folder = when (projectType) {
                "resourcepack" -> "resourcepacks"
                "shader" -> "shaderpacks"
                "datapack" -> "datapacks"
                "modpack" -> "modpacks"
                else -> "mods"
            }
            return ModFileDownload(
                descriptor = MinecraftDownload(url, sha1.lowercase(), size),
                relativePath = "$folder/$safeName",
                fileName = safeName
            )
        }
        throw IllegalArgumentException("The selected file is no longer available for these filters. Refresh the versions.")
    }

    internal fun isTrustedModDownloadUrl(value: String): Boolean {
        return try {
            val url = URL(value)
            url.protocol == "https" && url.host.lowercase() in modDownloadHosts &&
                url.userInfo == null && url.port in listOf(-1, 443)
        } catch (_: Exception) {
            false
        }
    }

    private fun safeModFileName(value: String): String {
        val fileName = value.trim()
        require(fileName.length in 1..180 &&
            fileName.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._+ -]{0,179}$")) &&
            fileName != "." && fileName != "..") {
            "The selected file has an unsafe filename"
        }
        return fileName.replace(Regex("\\s+"), "_")
    }

    private fun listModrinthVersions(
        projectId: String,
        projectType: String,
        version: String,
        loader: String
    ): JSONArray {
        require(projectId.matches(Regex("^[A-Za-z0-9_-]{1,64}$"))) { "Invalid Modrinth project ID" }
        val filters = ArrayList<String>()
        if (version.isNotEmpty()) filters += "game_versions=" + encode(JSONArray().put(version).toString())
        if (loader != "any") filters += "loaders=" + encode(JSONArray().put(loader).toString())
        val query = if (filters.isEmpty()) "" else "?" + filters.joinToString("&")
        val endpoint = "$MODRINTH_BASE/project/$projectId/version$query"
        val response = requestJsonArray(endpoint, "api.modrinth.com", null)
        val versions = JSONArray()
        for (i in 0 until response.length()) {
            val versionItem = response.optJSONObject(i) ?: continue
            val versionId = versionItem.optString("id")
            if (versionId.isBlank()) continue
            val files = versionItem.optJSONArray("files") ?: JSONArray()
            for (j in 0 until files.length()) {
                val file = files.optJSONObject(j) ?: continue
                val fileName = file.optString("filename")
                val url = file.optString("url")
                val hashes = file.optJSONObject("hashes")
                val sha1 = hashes?.optString("sha1").orEmpty()
                val size = file.optLong("size", -1L)
                if (!isTrustedModDownloadUrl(url) || !Regex("^[a-fA-F0-9]{40}$").matches(sha1) || size <= 0L) continue
                versions.put(
                    JSONObject()
                        .put("fileId", versionId)
                        .put("fileName", fileName)
                        .put("name", versionItem.optString("name", versionItem.optString("version_number")))
                        .put("versionNumber", versionItem.optString("version_number"))
                        .put("datePublished", versionItem.optString("date_published"))
                        .put("downloads", versionItem.optLong("downloads", 0L))
                        .put("size", size)
                        .put("sha1", sha1)
                        .put("downloadUrl", url)
                        .put("primary", file.optBoolean("primary", false))
                        .put("projectType", projectType)
                )
            }
        }
        return versions
    }

    private fun listCurseForgeVersions(
        projectId: String,
        projectType: String,
        version: String,
        loader: String,
        apiKey: String?
    ): JSONArray {
        require(projectId.matches(Regex("^[0-9]{1,12}$"))) { "Invalid CurseForge project ID" }
        val key = apiKey?.trim().orEmpty()
        require(key.length >= 8) {
            "CurseForge requires your own API key. Add it in the CurseForge section to search."
        }
        val loaderType = curseForgeLoaderType(loader)
        require(loaderType == null || version.isNotEmpty()) {
            "Choose a Minecraft version when filtering CurseForge by mod loader."
        }
        val endpoint = "$CURSEFORGE_BASE/mods/$projectId/files?" +
            (if (version.isNotEmpty()) "&gameVersion=" + encode(version) else "") +
            (if (loaderType != null) "&modLoaderType=$loaderType" else "") +
            "&pageSize=24&index=0"
        val body = requestJson(endpoint, "api.curseforge.com", key)
        val response = body.optJSONArray("data") ?: JSONArray()
        val versions = JSONArray()
        for (i in 0 until response.length()) {
            val item = response.optJSONObject(i) ?: continue
            if (item.optBoolean("isAvailable", true).not()) continue
            val fileId = item.optLong("id", 0L)
            val fileName = item.optString("fileName")
            val downloadUrl = item.optString("downloadUrl")
            val hashes = item.optJSONArray("hashes")
            var sha1 = ""
            for (j in 0 until (hashes?.length() ?: 0)) {
                val hash = hashes?.optJSONObject(j) ?: continue
                if (hash.optInt("algo", -1) == 1) {
                    sha1 = hash.optString("value")
                    break
                }
            }
            val size = item.optLong("fileLength", -1L)
            if (fileId <= 0L || !isTrustedModDownloadUrl(downloadUrl) ||
                !Regex("^[a-fA-F0-9]{40}$").matches(sha1) || size <= 0L) continue
            versions.put(
                JSONObject()
                    .put("fileId", fileId.toString())
                    .put("fileName", fileName)
                    .put("name", item.optString("displayName", fileName))
                    .put("versionNumber", item.optString("gameVersion"))
                    .put("datePublished", item.optString("fileDate"))
                    .put("downloads", item.optLong("downloadCount", 0L))
                    .put("size", size)
                    .put("sha1", sha1)
                    .put("downloadUrl", downloadUrl)
                    .put("primary", false)
                    .put("projectType", projectType)
            )
        }
        return versions
    }

    private fun searchModrinth(
        query: String,
        type: String,
        version: String,
        loader: String,
        sort: String,
        offset: Int
    ): JSONObject {
        val facets = JSONArray().put(JSONArray().put(if (type == "datapack") "all_project_types:datapack" else "project_type:" + type))
        if (version.isNotEmpty()) facets.put(JSONArray().put("versions:" + version))
        if (loader != "any") facets.put(JSONArray().put("categories:" + loader))
        val index = when (sort) {
            "downloads" -> "downloads"
            "updated" -> "updated"
            else -> "relevance"
        }
        val url = MODRINTH_BASE + "/search?query=" + encode(query) +
            "&facets=" + encode(facets.toString()) +
            "&index=" + index + "&limit=24&offset=" + offset
        val body = requestJson(url, "api.modrinth.com", null)
        val hits = body.optJSONArray("hits") ?: JSONArray()
        val results = JSONArray()
        for (indexInResults in 0 until hits.length()) {
            val item = hits.optJSONObject(indexInResults) ?: continue
            val id = item.optString("project_id")
            val slug = item.optString("slug")
            if (id.isBlank() || slug.isBlank()) continue
            val route = when (type) {
                "modpack" -> "modpack"
                "resourcepack" -> "resourcepack"
                "shader" -> "shader"
                "datapack" -> "datapack"
                else -> "mod"
            }
            results.put(
                JSONObject()
                    .put("id", id)
                    .put("title", item.optString("title", "Untitled project"))
                    .put("description", item.optString("description"))
                    .put("author", item.optString("author"))
                    .put("downloads", item.optLong("downloads", 0L))
                    .put("iconUrl", safeHttpsUrl(item.optString("icon_url")))
                    .put("url", "https://modrinth.com/" + route + "/" + slug)
                    .put("source", "Modrinth")
                    .put("type", type)
            )
        }
        return JSONObject()
            .put("ok", true)
            .put("source", "modrinth")
            .put("total", body.optInt("total_hits", results.length()))
            .put("offset", offset)
            .put("results", results)
    }

    private fun searchCurseForge(
        query: String,
        type: String,
        version: String,
        loader: String,
        sort: String,
        apiKey: String?,
        offset: Int
    ): JSONObject {
        val key = apiKey?.trim().orEmpty()
        require(key.length >= 8) {
            "CurseForge requires your own API key. Add it in the CurseForge section to search."
        }
        val classId = resolveCurseForgeClass(type, key)
        val loaderType = curseForgeLoaderType(loader)
        require(loaderType == null || version.isNotEmpty()) {
            "Choose a Minecraft version when filtering CurseForge by mod loader."
        }
        require(offset <= 9_976) { "CurseForge search page is out of range" }
        val sortField = curseForgeSortField(sort)
        val url = CURSEFORGE_BASE + "/mods/search?gameId=" + MINECRAFT_GAME_ID +
            "&classId=" + classId +
            "&searchFilter=" + encode(query) +
            (if (version.isNotEmpty()) "&gameVersion=" + encode(version) else "") +
            (if (loaderType != null) "&modLoaderType=" + loaderType else "") +
            "&sortField=" + sortField + "&sortOrder=desc&pageSize=24&index=" + offset
        val body = requestJson(url, "api.curseforge.com", key)
        val hits = body.optJSONArray("data") ?: JSONArray()
        val results = JSONArray()
        for (indexInResults in 0 until hits.length()) {
            val item = hits.optJSONObject(indexInResults) ?: continue
            val id = item.optLong("id", 0L)
            if (id <= 0L) continue
            val links = item.optJSONObject("links")
            val website = safeCurseForgePage(links?.optString("websiteUrl").orEmpty(), item.optString("slug"), type)
            val logo = item.optJSONObject("logo")
            val authors = item.optJSONArray("authors")
            val author = authors?.optJSONObject(0)?.optString("name").orEmpty()
            results.put(
                JSONObject()
                    .put("id", id.toString())
                    .put("title", item.optString("name", "Untitled project"))
                    .put("description", item.optString("summary"))
                    .put("author", author)
                    .put("downloads", item.optLong("downloadCount", 0L))
                    .put("iconUrl", safeHttpsUrl(logo?.optString("url").orEmpty()))
                    .put("url", website)
                    .put("source", "CurseForge")
                    .put("type", type)
            )
        }
        return JSONObject()
            .put("ok", true)
            .put("source", "curseforge")
            .put("total", body.optJSONObject("pagination")?.optInt("totalCount", results.length())
                ?: results.length())
            .put("offset", offset)
            .put("results", results)
    }

    private fun resolveCurseForgeClass(type: String, apiKey: String): Int {
        synchronized(cachedClassIds) {
            cachedClassIds[type]?.let { return it }
        }
        val categories = requestJson(
            CURSEFORGE_BASE + "/categories?gameId=" + MINECRAFT_GAME_ID + "&classesOnly=true",
            "api.curseforge.com",
            apiKey
        ).optJSONArray("data") ?: JSONArray()
        var resolved = 0
        for (i in 0 until categories.length()) {
            val item = categories.optJSONObject(i) ?: continue
            if (!item.optBoolean("isClass", false)) continue
            if (matchesCurseForgeClass(type, item.optString("name"))) {
                resolved = item.optInt("id", 0)
                if (resolved > 0) break
            }
        }
        if (resolved <= 0) {
            throw IllegalStateException("CurseForge did not return a category for " + type)
        }
        synchronized(cachedClassIds) { cachedClassIds[type] = resolved }
        return resolved
    }


    internal fun curseForgeSortField(sort: String): Int = when (sort) {
        "downloads" -> 6 // TotalDownloads
        "updated" -> 3 // LastUpdated
        else -> 2 // Popularity / relevance
    }

    internal fun matchesCurseForgeClass(type: String, categoryName: String): Boolean {
        val name = categoryName.trim().lowercase()
        val accepted = when (type) {
            "mod" -> setOf("mods")
            "modpack" -> setOf("modpacks", "mod packs")
            "resourcepack" -> setOf("resource packs", "texture packs", "resourcepack", "texturepack")
            "shader" -> setOf("shaders", "shader packs", "shaderpacks")
            "datapack" -> setOf("data packs", "datapacks")
            else -> emptySet()
        }
        return name in accepted
    }

    internal fun curseForgeLoaderType(loader: String): Int? = when (loader) {
        "any" -> null
        "forge" -> 1
        "fabric" -> 4
        "quilt" -> 5
        "neoforge" -> 6
        else -> throw IllegalArgumentException("Unsupported CurseForge mod loader")
    }

    private fun safeCurseForgePage(candidate: String, slug: String, type: String): String {
        val safe = safeHttpsUrl(candidate)
        if (safe.startsWith("https://www.curseforge.com/") ||
            safe.startsWith("https://curseforge.com/")) return safe
        if (slug.isBlank()) return "https://www.curseforge.com/minecraft/search"
        require(slug.matches(Regex("^[A-Za-z0-9._-]{1,100}$"))) {
            "CurseForge returned an invalid project slug"
        }
        val path = when (type) {
            "modpack" -> "modpacks"
            "resourcepack" -> "texture-packs"
            "shader" -> "shaders"
            "datapack" -> "data-packs"
            else -> "mc-mods"
        }
        return "https://www.curseforge.com/minecraft/" + path + "/" + slug
    }

    private fun safeHttpsUrl(value: String): String {
        return try {
            val url = URL(value)
            if (url.protocol == "https" && url.userInfo == null && url.port in listOf(-1, 443)) {
                value
            } else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun requestJson(value: String, allowedHost: String, apiKey: String?): JSONObject =
        JSONObject(requestPayload(value, allowedHost, apiKey))

    private fun requestJsonArray(value: String, allowedHost: String, apiKey: String?): JSONArray =
        JSONArray(requestPayload(value, allowedHost, apiKey))

    private fun requestPayload(value: String, allowedHost: String, apiKey: String?): String {
        val url = URL(value)
        require(url.protocol == "https" && url.host.equals(allowedHost, ignoreCase = true) &&
            url.userInfo == null && url.port in listOf(-1, 443)) {
            "Refusing an untrusted mod-platform URL"
        }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "HyperLauncher/0.2.0 (https://github.com/hsudais201-ops/myLauncher)")
            if (!apiKey.isNullOrBlank()) setRequestProperty("x-api-key", apiKey)
        }
        try {
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                val message = when {
                    status == 401 || status == 403 -> "CurseForge rejected the API key or the key is not approved for third-party access."
                    status == 429 -> "The mod service rate limit was reached. Try again shortly."
                    else -> "Mod service returned HTTP " + status
                }
                throw IllegalStateException(message)
            }
            if (connection.contentLengthLong > MAX_RESPONSE_BYTES) {
                throw IllegalStateException("Mod search response is unexpectedly large")
            }
            val payload = BufferedInputStream(connection.inputStream).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_RESPONSE_BYTES) {
                        throw IllegalStateException("Mod search response exceeded the size limit")
                    }
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
            return payload
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
