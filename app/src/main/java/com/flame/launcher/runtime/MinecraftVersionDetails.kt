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
            require(json.toByteArray(Charsets.UTF_8).size in 1..MAX_DETAILS_BYTES) {
                "Minecraft version details are empty or exceed the size limit."
            }
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
            require(mainClass.length <= 256 && mainClass.matches(MAIN_CLASS)) {
                "Minecraft metadata has an invalid main class."
            }
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
            require(rawLibraries.length() <= MAX_LIBRARY_ENTRIES) {
                "Minecraft metadata contains too many library entries."
            }
            val libraries = ArrayList<MinecraftLibraryArtifact>()
            for (index in 0 until rawLibraries.length()) {
                val library = rawLibraries.optJSONObject(index)
                    ?: throw IllegalArgumentException("Minecraft library entry $index is invalid.")
                val name = library.optString("name")
                require(name.isNotBlank() && name.length <= 512) {
                    "Minecraft library entry $index has an invalid coordinate."
                }
                val rules = parseLibraryRules(library.optJSONArray("rules"), name)
                val natives = parseNativeMappings(library.optJSONObject("natives"), name)
                val extractExcludes = parseExtractExcludes(library.optJSONObject("extract"), name)
                val downloadsObject = library.optJSONObject("downloads") ?: continue
                val artifact = downloadsObject.optJSONObject("artifact")?.let {
                    parseDownload(it, "Library $name", LIBRARY_HOSTS)
                }
                val classifiersObject = downloadsObject.optJSONObject("classifiers")
                val classifiers = LinkedHashMap<String, MinecraftDownloadDescriptor>()
                if (classifiersObject != null) {
                    require(classifiersObject.length() <= MAX_CLASSIFIERS_PER_LIBRARY) {
                        "Library $name contains too many native classifiers."
                    }
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
                    libraries += MinecraftLibraryArtifact(
                        coordinate = name,
                        artifact = artifact,
                        classifiers = classifiers,
                        rules = rules,
                        natives = natives,
                        extractExcludes = extractExcludes
                    )
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

        private fun parseLibraryRules(
            rawRules: org.json.JSONArray?,
            libraryName: String
        ): List<MinecraftLibraryRule> {
            if (rawRules == null) return emptyList()
            require(rawRules.length() <= MAX_RULES_PER_LIBRARY) {
                "Library $libraryName contains too many compatibility rules."
            }
            return (0 until rawRules.length()).map { index ->
                val rule = rawRules.optJSONObject(index)
                    ?: throw IllegalArgumentException("Library $libraryName has an invalid rule at index $index.")
                val action = rule.optString("action")
                require(action == "allow" || action == "disallow") {
                    "Library $libraryName has an unsupported rule action."
                }
                val rawOs = rule.opt("os")
                require(rawOs == null || rawOs === JSONObject.NULL || rawOs is JSONObject) {
                    "Library $libraryName has an invalid operating-system rule."
                }
                val os = rawOs as? JSONObject
                val osName = optionalString(os, "name", "Library $libraryName OS name")
                require(osName == null || osName in SUPPORTED_OS_NAMES) {
                    "Library $libraryName has an unsupported operating-system rule."
                }
                val osArchPattern = optionalString(os, "arch", "Library $libraryName OS architecture")
                val osVersionPattern = optionalString(os, "version", "Library $libraryName OS version")
                validateRegex(osArchPattern, "Library $libraryName OS architecture")
                validateRegex(osVersionPattern, "Library $libraryName OS version")

                val rawFeatures = rule.opt("features")
                require(rawFeatures == null || rawFeatures === JSONObject.NULL || rawFeatures is JSONObject) {
                    "Library $libraryName has invalid feature rules."
                }
                val featuresObject = rawFeatures as? JSONObject
                require((featuresObject?.length() ?: 0) <= MAX_FEATURES_PER_RULE) {
                    "Library $libraryName has too many feature rules."
                }
                val features = LinkedHashMap<String, Boolean>()
                if (featuresObject != null) {
                    val keys = featuresObject.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        require(FEATURE_NAME.matches(key)) {
                            "Library $libraryName has an invalid feature name."
                        }
                        val value = featuresObject.opt(key)
                        require(value is Boolean) {
                            "Library $libraryName feature $key must be true or false."
                        }
                        features[key] = value
                    }
                }
                MinecraftLibraryRule(action, osName, osArchPattern, osVersionPattern, features)
            }
        }

        private fun parseNativeMappings(
            rawNatives: JSONObject?,
            libraryName: String
        ): Map<String, String> {
            if (rawNatives == null) return emptyMap()
            require(rawNatives.length() <= SUPPORTED_OS_NAMES.size) {
                "Library $libraryName has too many native mappings."
            }
            val mappings = LinkedHashMap<String, String>()
            val keys = rawNatives.keys()
            while (keys.hasNext()) {
                val osName = keys.next()
                require(osName in SUPPORTED_OS_NAMES) {
                    "Library $libraryName has an unsupported native operating system."
                }
                val template = rawNatives.optString(osName)
                val normalizedTemplate = template.replace(36.toChar().toString() + "{arch}", "64")
                require(template.count { it.code == 36 } <= 1 && CLASSIFIER.matches(normalizedTemplate)) {
                    "Library $libraryName has an invalid native classifier template."
                }
                mappings[osName] = template
            }
            return mappings
        }

        private fun parseExtractExcludes(
            rawExtract: JSONObject?,
            libraryName: String
        ): List<String> {
            if (rawExtract == null) return emptyList()
            val excludes = rawExtract.optJSONArray("exclude") ?: return emptyList()
            require(excludes.length() <= MAX_EXCLUDES_PER_LIBRARY) {
                "Library $libraryName has too many native extraction exclusions."
            }
            return (0 until excludes.length()).map { index ->
                val rawPath = excludes.optString(index)
                val normalized = rawPath.trimEnd('/')
                require(normalized.isNotBlank() && isSafeRelativePath(normalized)) {
                    "Library $libraryName has an unsafe native extraction exclusion."
                }
                normalized
            }
        }

        private fun optionalString(value: JSONObject?, key: String, label: String): String? {
            if (value == null) return null
            val raw = value.opt(key)
            if (raw == null || raw === JSONObject.NULL) return null
            require(raw is String && raw.length <= MAX_RULE_STRING_LENGTH) {
                "$label is invalid."
            }
            return raw.takeIf(String::isNotBlank)
        }

        private fun validateRegex(pattern: String?, label: String) {
            if (pattern == null) return
            try {
                Regex(pattern)
            } catch (failure: Exception) {
                throw IllegalArgumentException("$label contains an invalid regular expression.", failure)
            }
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
            if (path != null) {
                require(isSafeRelativePath(path)) { "$label has an unsafe installation path." }
            }
            return MinecraftDownloadDescriptor(url, sha1.lowercase(), size, path)
        }

        private fun isSafeRelativePath(value: String): Boolean =
            !value.startsWith("/") &&
                !value.startsWith("\\\\") &&
                '\\' !in value &&
                ':' !in value &&
                value.split('/').all { it.isNotBlank() && it != "." && it != ".." }

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

        private const val MAX_DETAILS_BYTES = 8 * 1024 * 1024
        private const val MAX_LIBRARY_ENTRIES = 10_000
        private const val MAX_CLASSIFIERS_PER_LIBRARY = 256
        private const val MAX_RULES_PER_LIBRARY = 256
        private const val MAX_FEATURES_PER_RULE = 64
        private const val MAX_EXCLUDES_PER_LIBRARY = 256
        private const val MAX_RULE_STRING_LENGTH = 256
        private val VERSION_ID = Regex("[A-Za-z0-9._-]{1,64}")
        private val MAIN_CLASS = Regex(
            "[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*"
        )
        private val SHA1 = Regex("[a-fA-F0-9]{40}")
        private val CLASSIFIER = Regex("[A-Za-z0-9._-]{1,128}")
        private val FEATURE_NAME = Regex("[A-Za-z0-9_]{1,64}")
        private val SUPPORTED_OS_NAMES = setOf("windows", "osx", "linux")
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
    val classifiers: Map<String, MinecraftDownloadDescriptor>,
    val rules: List<MinecraftLibraryRule> = emptyList(),
    val natives: Map<String, String> = emptyMap(),
    val extractExcludes: List<String> = emptyList()
)

data class MinecraftLibraryRule(
    val action: String,
    val osName: String?,
    val osArchPattern: String?,
    val osVersionPattern: String?,
    val features: Map<String, Boolean>
)
