package com.flame.launcher.runtime

import org.json.JSONException
import org.json.JSONObject
import java.net.URI

/**
 * Validated installation descriptors from a Mojang version JSON document.
 * This does not download or install files; it only creates a safe, typed plan.
 */
data class MinecraftVersionDetails(
    val id: String,
    val mainClass: String,
    val assets: String,
    val assetIndex: MinecraftDownloadDescriptor,
    val client: MinecraftDownloadDescriptor,
    val libraries: List<MinecraftLibraryArtifact>,
    val javaMajorVersion: Int?
) {
    companion object {
        fun parse(json: String, expectedVersionId: String): MinecraftVersionDetails {
            require(VERSION_ID.matches(expectedVersionId)) { "Requested Minecraft version ID is invalid." }
            val root = try {
                JSONObject(json)
            } catch (failure: JSONException) {
                throw IllegalArgumentException("Minecraft version details are not valid JSON.", failure)
            }
            val id = root.optString("id")
            require(id == expectedVersionId) {
                "Minecraft metadata ID does not match the requested version."
            }
            val mainClass = root.optString("mainClass")
            require(mainClass.matches(MAIN_CLASS)) { "Minecraft metadata has an invalid main class." }
            val assets = root.optString("assets").takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("Minecraft metadata is missing its assets ID.")
            val downloads = root.optJSONObject("downloads")
                ?: throw IllegalArgumentException("Minecraft metadata is missing download descriptors.")
            val client = parseDownload(
                downloads.optJSONObject("client"),
                "Minecraft client",
                CLIENT_HOSTS
            )
            val assetIndexObject = root.optJSONObject("assetIndex")
                ?: throw IllegalArgumentException("Minecraft metadata is missing its asset index.")
            val assetIndex = parseDownload(assetIndexObject, "Minecraft asset index", CLIENT_HOSTS)

            val rawLibraries = root.optJSONArray("libraries")
                ?: throw IllegalArgumentException("Minecraft metadata is missing its libraries list.")
            val libraries = ArrayList<MinecraftLibraryArtifact>()
            for (index in 0 until rawLibraries.length()) {
                val library = rawLibraries.optJSONObject(index)
                    ?: throw IllegalArgumentException("Minecraft library entry $index is invalid.")
                val name = library.optString("name")
                require(name.isNotBlank() && name.length <= 512) {
                    "Minecraft library entry $index has an invalid coordinate."
                }
                val downloadsObject = library.optJSONObject("downloads") ?: continue
                val artifact = downloadsObject.optJSONObject("artifact")?.let {
                    parseDownload(it, "Library $name", LIBRARY_HOSTS)
                }
                val classifiersObject = downloadsObject.optJSONObject("classifiers")
                val classifiers = LinkedHashMap<String, MinecraftDownloadDescriptor>()
                if (classifiersObject != null) {
                    val keys = classifiersObject.keys()
                    while (keys.hasNext()) {
                        val classifier = keys.next()
                        require(CLASSIFIER.matches(classifier)) { "Library $name has an invalid native classifier." }
                        val descriptor = classifiersObject.optJSONObject(classifier)
                            ?: throw IllegalArgumentException("Library $name has an invalid classifier descriptor.")
                        classifiers[classifier] = parseDownload(
                            descriptor,
                            "Library $name ($classifier)",
                            LIBRARY_HOSTS
                        )
                    }
                }
                if (artifact != null || classifiers.isNotEmpty()) {
                    libraries += MinecraftLibraryArtifact(name, artifact, classifiers)
                }
            }

            val javaMajorVersion = root.optJSONObject("javaVersion")
                ?.optInt("majorVersion")
                ?.takeIf { it in 1..100 }
            return MinecraftVersionDetails(
                id = id,
                mainClass = mainClass,
                assets = assets,
                assetIndex = assetIndex,
                client = client,
                libraries = libraries,
                javaMajorVersion = javaMajorVersion
            )
        }

        private fun parseDownload(
            value: JSONObject?,
            label: String,
            allowedHosts: Set<String>
        ): MinecraftDownloadDescriptor {
            requireNotNull(value) { "$label download descriptor is missing." }
            val url = value.optString("url")
            val sha1 = value.optString("sha1")
            val size = value.optLong("size", -1L)
            require(isHttpsUrl(url, allowedHosts)) { "$label has an untrusted download URL." }
            require(SHA1.matches(sha1)) { "$label has an invalid SHA-1 digest." }
            require(size > 0L) { "$label has an invalid file size." }
            val path = value.optString("path").takeIf(String::isNotBlank)
            return MinecraftDownloadDescriptor(url, sha1.lowercase(), size, path)
        }

        private fun isHttpsUrl(value: String, allowedHosts: Set<String>): Boolean = try {
            val uri = URI(value)
            uri.scheme == "https" &&
                uri.host?.lowercase() in allowedHosts &&
                uri.userInfo == null &&
                uri.port in setOf(-1, 443) &&
                uri.rawPath?.contains("/../") != true
        } catch (_: Exception) {
            false
        }

        private val VERSION_ID = Regex("[A-Za-z0-9._-]{1,64}")
        private val MAIN_CLASS = Regex("[A-Za-z_$][A-Za-z0-9_$.]{1,255}")
        private val SHA1 = Regex("[a-fA-F0-9]{40}")
        private val CLASSIFIER = Regex("[A-Za-z0-9._-]{1,128}")
        private val CLIENT_HOSTS = setOf("piston-data.mojang.com", "piston-meta.mojang.com", "launcher.mojang.com")
        private val LIBRARY_HOSTS = CLIENT_HOSTS + setOf(
            "libraries.minecraft.net",
            "repo1.maven.org",
            "repo.maven.apache.org",
            "maven.fabricmc.net",
            "maven.minecraftforge.net",
            "maven.neoforged.net",
            "maven.quiltmc.org"
        )
    }
}

data class MinecraftDownloadDescriptor(
    val url: String,
    val sha1: String,
    val size: Long,
    val path: String? = null
)

data class MinecraftLibraryArtifact(
    val coordinate: String,
    val artifact: MinecraftDownloadDescriptor?,
    val classifiers: Map<String, MinecraftDownloadDescriptor>
)
