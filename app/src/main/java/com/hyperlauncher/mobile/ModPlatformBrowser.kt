package com.hyperlauncher.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Search adapter for the official Modrinth and CurseForge APIs.
 * CurseForge credentials are supplied at runtime and are never written to disk.
 */
object ModPlatformBrowser {
    private const val MODRINTH_BASE = "https://api.modrinth.com/v2"
    private const val CURSEFORGE_BASE = "https://api.curseforge.com/v1"
    private const val MINECRAFT_GAME_ID = 432
    private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    private val projectTypes = setOf("mod", "modpack", "resourcepack", "shader")
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
        curseForgeKey: String?
    ): JSONObject {
        require(source == "modrinth" || source == "curseforge") { "Choose Modrinth or CurseForge" }
        require(projectTypes.contains(projectType)) { "Unsupported project type" }
        require(loaders.contains(loader)) { "Unsupported mod loader" }
        require(query.length <= 80) { "Search text is too long" }
        val version = gameVersion.trim()
        require(version.isEmpty() || versionPattern.matches(version)) { "Enter a valid Minecraft version" }
        return if (source == "modrinth") {
            searchModrinth(query.trim(), projectType, version, loader, sort)
        } else {
            searchCurseForge(query.trim(), projectType, version, sort, curseForgeKey)
        }
    }

    private fun searchModrinth(
        query: String,
        type: String,
        version: String,
        loader: String,
        sort: String
    ): JSONObject {
        val facets = JSONArray().put(JSONArray().put("project_type:" + type))
        if (version.isNotEmpty()) facets.put(JSONArray().put("versions:" + version))
        if (loader != "any") facets.put(JSONArray().put("categories:" + loader))
        val index = when (sort) {
            "downloads" -> "downloads"
            "updated" -> "updated"
            else -> "relevance"
        }
        val url = MODRINTH_BASE + "/search?query=" + encode(query) +
            "&facets=" + encode(facets.toString()) +
            "&index=" + index + "&limit=24"
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
            .put("results", results)
    }

    private fun searchCurseForge(
        query: String,
        type: String,
        version: String,
        sort: String,
        apiKey: String?
    ): JSONObject {
        val key = apiKey?.trim().orEmpty()
        require(key.length >= 8) {
            "CurseForge requires your own API key. Add it in the CurseForge section to search."
        }
        val classId = resolveCurseForgeClass(type, key)
        val sortField = when (sort) {
            "downloads" -> 2
            "updated" -> 3
            else -> 13
        }
        val url = CURSEFORGE_BASE + "/mods/search?gameId=" + MINECRAFT_GAME_ID +
            "&classId=" + classId +
            "&searchFilter=" + encode(query) +
            (if (version.isNotEmpty()) "&gameVersion=" + encode(version) else "") +
            "&sortField=" + sortField + "&sortOrder=desc&pageSize=24&index=0"
        val body = requestJson(url, "api.curseforge.com", key)
        val hits = body.optJSONArray("data") ?: JSONArray()
        val results = JSONArray()
        for (indexInResults in 0 until hits.length()) {
            val item = hits.optJSONObject(indexInResults) ?: continue
            val id = item.optLong("id", 0L)
            if (id <= 0L) continue
            val links = item.optJSONObject("links")
            val website = safeCurseForgePage(links?.optString("websiteUrl").orEmpty(), item.optString("slug"))
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
        val target = when (type) {
            "modpack" -> listOf("modpack")
            "resourcepack" -> listOf("resource pack", "texture pack", "resourcepack")
            "shader" -> listOf("shader")
            else -> listOf("mods", "mod")
        }
        var resolved = 0
        for (i in 0 until categories.length()) {
            val item = categories.optJSONObject(i) ?: continue
            if (!item.optBoolean("isClass", false)) continue
            val name = item.optString("name").lowercase()
            if (target.any { name == it || name.contains(it) }) {
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

    private fun safeCurseForgePage(candidate: String, slug: String): String {
        val safe = safeHttpsUrl(candidate)
        if (safe.startsWith("https://www.curseforge.com/") ||
            safe.startsWith("https://curseforge.com/")) return safe
        require(slug.matches(Regex("^[A-Za-z0-9._-]{1,100}$"))) {
            "CurseForge returned an invalid project link"
        }
        val section = "minecraft/" + when {
            slug.isBlank() -> "search"
            else -> "search"
        }
        return "https://www.curseforge.com/" + section + "?search=" + encode(slug)
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

    private fun requestJson(value: String, allowedHost: String, apiKey: String?): JSONObject {
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
            return JSONObject(payload)
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
