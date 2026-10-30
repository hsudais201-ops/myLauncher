package com.hyperlauncher.mobile

import org.json.JSONArray
import org.json.JSONObject

data class MinecraftLibrary(val name: String, val artifact: PlannedMinecraftFile)

/**
 * Resolves standard Maven artifacts from the official version JSON.
 * Native classifiers are deliberately handled by a later platform-specific
 * stage; this resolver never guesses a classifier or downloads arbitrary URLs.
 */
object MinecraftLibraryResolver {
    fun parse(versionId: String, json: String): List<MinecraftLibrary> {
        require(Regex("^[0-9][A-Za-z0-9._-]{0,63}$").matches(versionId)) {
            "Invalid Minecraft version ID"
        }
        val libraries = JSONObject(json).optJSONArray("libraries") ?: JSONArray()
        val result = ArrayList<MinecraftLibrary>()
        for (index in 0 until libraries.length()) {
            val library = libraries.getJSONObject(index)
            if (!isAllowedByRules(library.optJSONArray("rules"))) continue
            val name = library.optString("name")
            require(name.isNotBlank() && name.length <= 512) { "Library has an invalid Maven name" }
            val downloads = library.optJSONObject("downloads") ?: continue
            val artifact = downloads.optJSONObject("artifact") ?: continue
            val path = artifact.optString("path")
            require(isSafeMavenPath(path)) { "Library artifact path is unsafe" }
            val descriptor = MinecraftDownload(
                url = artifact.optString("url"),
                sha1 = artifact.optString("sha1"),
                size = artifact.optLong("size", -1L)
            )
            require(MinecraftVersionDetailsResolver.isTrustedDownloadUrl(descriptor.url)) {
                "Library download URL is not trusted"
            }
            require(Regex("^[a-fA-F0-9]{40}$").matches(descriptor.sha1)) {
                "Library artifact has an invalid SHA-1 checksum"
            }
            require(descriptor.size > 0L) { "Library artifact has an invalid size" }
            result += MinecraftLibrary(
                name = name,
                artifact = PlannedMinecraftFile(
                    descriptor.copy(sha1 = descriptor.sha1.lowercase()),
                    "libraries/$path",
                    "library"
                )
            )
        }
        return result.distinctBy { it.artifact.relativePath }
    }

    private fun isSafeMavenPath(path: String): Boolean {
        if (path.isBlank() || path.length > 1024 || path.startsWith("/") || path.startsWith("\\")) return false
        if (path.contains('\\')) return false
        val segments = path.split('/')
        return segments.all { it.isNotBlank() && it != "." && it != ".." } &&
            segments.last().contains('.')
    }

    private fun isAllowedByRules(rules: JSONArray?): Boolean {
        if (rules == null || rules.length() == 0) return true
        var allowed = false
        for (index in 0 until rules.length()) {
            val rule = rules.getJSONObject(index)
            val os = rule.optJSONObject("os")
            val matches = os == null || (
                (os.optString("name").isBlank() || os.optString("name") == "linux") &&
                (os.optString("arch").isBlank() || runCatching {
                    Regex(os.optString("arch")).containsMatchIn("aarch64")
                }.getOrDefault(false))
            )
            if (matches) allowed = rule.optString("action") == "allow"
        }
        return allowed
    }
}
