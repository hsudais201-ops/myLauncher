package dev.mylauncher

import org.json.JSONObject

data class LibraryArtifact(
    val name: String,
    val url: String,
    val sha1: String,
    val size: Long,
    val relativePath: String
)

data class MinecraftInstallPlan(
    val versionId: String,
    val mainClass: String,
    val assetIndexId: String,
    val assetIndexUrl: String,
    val assetIndexSha1: String,
    val clientJarUrl: String,
    val clientJarSha1: String,
    val libraries: List<LibraryArtifact>
)

/** Reads the official version JSON and extracts validated download metadata. */
object MinecraftInstallPlanParser {
    fun parse(jsonText: String, expectedId: String): MinecraftInstallPlan {
        require(expectedId.matches(Regex("[A-Za-z0-9._-]{1,100}")))
        val root = JSONObject(jsonText)
        require(root.optString("id") == expectedId) { "Version metadata ID mismatch." }
        val asset = root.getJSONObject("assetIndex")
        val client = root.getJSONObject("downloads").getJSONObject("client")
        val libraries = mutableListOf<LibraryArtifact>()
        val entries = root.getJSONArray("libraries")
        for (i in 0 until entries.length()) {
            val lib = entries.getJSONObject(i)
            if (!isAllowedOnAndroidLinuxArm64(lib)) continue
            val artifact = lib.optJSONObject("downloads")?.optJSONObject("artifact") ?: continue
            val url = artifact.optString("url")
            val hash = artifact.optString("sha1").lowercase()
            val relativePath = artifact.optString("path")
            if (!url.startsWith("https://") || !hash.matches(Regex("[a-f0-9]{40}"))) continue
            if (!isSafeRelativePath(relativePath)) continue
            libraries += LibraryArtifact(lib.optString("name"), url, hash, artifact.optLong("size", -1), relativePath)
        }
        return MinecraftInstallPlan(
            versionId = expectedId,
            mainClass = root.optString("mainClass"),
            assetIndexId = asset.getString("id"),
            assetIndexUrl = asset.getString("url").also { require(it.startsWith("https://")) },
            assetIndexSha1 = asset.getString("sha1").lowercase().also { require(it.matches(Regex("[a-f0-9]{40}"))) },
            clientJarUrl = client.getString("url").also { require(it.startsWith("https://")) },
            clientJarSha1 = client.getString("sha1").lowercase().also { require(it.matches(Regex("[a-f0-9]{40}"))) },
            libraries = libraries
        )
    }

    /**
     * Evaluates Mojang's ordered allow/disallow rules for this Android target.
     * This target is represented as Linux ARM64; native classifier selection and
     * extraction remain separate work.
     */
    private fun isAllowedOnAndroidLinuxArm64(library: JSONObject): Boolean {
        val rules = library.optJSONArray("rules") ?: return true
        var allowed = false
        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue
            val os = rule.optJSONObject("os")
            if (os != null) {
                if (os.has("name") && os.optString("name") != "linux") continue
                if (os.has("arch")) {
                    val pattern = os.optString("arch")
                    val matches = try {
                        Regex(pattern).matches("aarch64") || Regex(pattern).matches("arm64")
                    } catch (_: Exception) {
                        false
                    }
                    if (!matches) continue
                }
                if (os.has("version")) {
                    val pattern = os.optString("version")
                    val matches = try { Regex(pattern).matches("") } catch (_: Exception) { false }
                    if (!matches) continue
                }
            }
            val features = rule.optJSONObject("features")
            if (features != null && features.keys().asSequence().any { features.optBoolean(it) }) continue
            allowed = rule.optString("action") == "allow"
        }
        return allowed
    }

    private fun isSafeRelativePath(path: String): Boolean {
        if (path.isBlank() || path.startsWith("/") || path.startsWith("\\")) return false
        if (path.contains("\\") || path.split("/").any { it.isBlank() || it == "." || it == ".." }) return false
        return path.matches(Regex("[A-Za-z0-9._+/-]{1,500}"))
    }
}
