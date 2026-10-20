package com.voidcraft.launcher.core

import java.util.LinkedHashMap
import java.util.regex.PatternSyntaxException

data class MinecraftPlatformContext(
    val osName: String,
    val architecture: String,
    val osVersion: String = "",
    val features: Set<String> = emptySet()
) {
    init {
        require(osName in SUPPORTED_OS_NAMES) {
            "OS name must use a Minecraft metadata key (windows, osx, linux)"
        }
        require(architecture.isNotBlank() && architecture.length <= 128) {
            "Architecture must be provided by the runtime adapter"
        }
    }

    companion object {
        val SUPPORTED_OS_NAMES = setOf("windows", "osx", "linux")
    }
}

class MinecraftLibraryRuleEvaluator {
    /**
     * Mojang version metadata has an ordered rule list: no rules means allowed;
     * with rules, the default is denied and the last matching rule decides.
     */
    fun isAllowed(rules: List<MinecraftLibraryRule>, platform: MinecraftPlatformContext): Boolean {
        if (rules.isEmpty()) return true
        var allowed = false
        rules.forEach { rule ->
            if (matches(rule, platform)) allowed = rule.action == "allow"
        }
        return allowed
    }

    private fun matches(rule: MinecraftLibraryRule, platform: MinecraftPlatformContext): Boolean {
        if (rule.action != "allow" && rule.action != "disallow") return false
        if (rule.osName != null && rule.osName != platform.osName) return false
        if (rule.osVersionRegex != null && !regexMatches(rule.osVersionRegex, platform.osVersion)) return false
        if (rule.osArchitectureRegex != null && !regexMatches(rule.osArchitectureRegex, platform.architecture)) return false
        return rule.features.all { (feature, required) ->
            platform.features.contains(feature) == required
        }
    }

    private fun regexMatches(expression: String, value: String): Boolean {
        return try {
            Regex(expression).containsMatchIn(value)
        } catch (_: PatternSyntaxException) {
            false
        }
    }
}

data class MinecraftAssetObject(
    val name: String,
    val hash: String,
    val sizeBytes: Long
) {
    val relativeObjectPath: String
        get() = "assets/objects/" + hash.take(2) + "/" + hash

    val url: String
        get() = "https://resources.download.minecraft.net/" + hash.take(2) + "/" + hash
}

data class MinecraftAssetIndexContents(
    val objects: List<MinecraftAssetObject>,
    val totalSizeBytes: Long,
    val virtual: Boolean,
    val mapToResources: Boolean
)

class MinecraftAssetIndexParser {
    fun parse(json: String): MinecraftAssetIndexContents {
        require(json.length in 1..MAX_JSON_CHARS) { "Asset-index size is outside the supported range" }
        val root = try {
            com.google.gson.JsonParser.parseString(json.removePrefix("\uFEFF"))
        } catch (error: Exception) {
            throw IllegalArgumentException("Asset index is not valid JSON", error)
        }
        require(root.isJsonObject) { "Asset-index root must be an object" }
        val obj = root.asJsonObject
        val objectsElement = obj.get("objects")
        require(objectsElement != null && objectsElement.isJsonObject) {
            "Asset index is missing the objects map"
        }
        val objectMap = objectsElement.asJsonObject
        require(objectMap.size() in 1..MAX_OBJECTS) { "Asset-index object count is outside the supported range" }
        var total = 0L
        val assets = objectMap.entrySet().map { (name, element) ->
            require(name.isNotBlank() && name.length <= MAX_NAME_LENGTH) { "Asset object name is invalid" }
            require(element.isJsonObject) { "Asset descriptor for " + name + " is not an object" }
            val descriptor = element.asJsonObject
            val hashElement = descriptor.get("hash")
            require(hashElement != null && hashElement.isJsonPrimitive &&
                hashElement.asJsonPrimitive.isString) { "Asset hash is missing for " + name }
            val hash = hashElement.asString.lowercase()
            require(SHA1_PATTERN.matches(hash)) { "Invalid asset hash for " + name }
            val sizeElement = descriptor.get("size")
            require(sizeElement != null && sizeElement.isJsonPrimitive &&
                sizeElement.asJsonPrimitive.isNumber) { "Asset size is missing for " + name }
            val size = try {
                sizeElement.asLong
            } catch (error: Exception) {
                throw IllegalArgumentException("Invalid asset size for " + name, error)
            }
            require(size >= 0L) { "Negative asset size for " + name }
            if (Long.MAX_VALUE - total < size) throw IllegalArgumentException("Asset size sum overflow")
            total += size
            MinecraftAssetObject(name, hash, size)
        }
        val virtual = optionalBoolean(obj, "virtual")
        val mapToResources = optionalBoolean(obj, "map_to_resources")
        return MinecraftAssetIndexContents(assets, total, virtual, mapToResources)
    }

    private fun optionalBoolean(obj: com.google.gson.JsonObject, key: String): Boolean {
        val element = obj.get(key) ?: return false
        require(element.isJsonPrimitive && element.asJsonPrimitive.isBoolean) {
            "Invalid boolean field in asset index: " + key
        }
        return element.asBoolean
    }

    companion object {
        private const val MAX_JSON_CHARS = 5 * 1024 * 1024
        private const val MAX_OBJECTS = 250_000
        private const val MAX_NAME_LENGTH = 2048
        private val SHA1_PATTERN = Regex("[0-9a-fA-F]{40}")
    }
}

data class MinecraftPlannedDownload(
    val kind: String,
    val destinationRelativePath: String,
    val url: String,
    val expectedSha1: String?,
    val expectedBytes: Long?,
    val extractExcludes: List<String> = emptyList(),
    val logicalName: String? = null
)

data class MinecraftInstallPlan(
    val versionId: String,
    val javaMajorVersion: Int?,
    val mainClass: String?,
    val downloads: List<MinecraftPlannedDownload>,
    val warnings: List<String>,
    /** Version directory that owns the resolved client JAR when it is inherited. */
    val clientArtifactVersionId: String? = null
) {
    val isFullyResolved: Boolean
        get() = warnings.isEmpty()
}

/**
 * Creates a deterministic download manifest. This is not the installer itself:
 * callers must stage downloads safely, verify artifacts, and perform native
 * extraction only after downloads pass integrity checks.
 */
class MinecraftInstallPlanner(
    private val platform: MinecraftPlatformContext,
    private val ruleEvaluator: MinecraftLibraryRuleEvaluator = MinecraftLibraryRuleEvaluator()
) {
    fun createPlan(
        versionEntry: MinecraftVersionEntry,
        details: MinecraftVersionDetails,
        assetIndexContents: MinecraftAssetIndexContents? = null
    ): MinecraftInstallPlan {
        require(versionEntry.id == details.id) { "Version entry and detail IDs do not match" }
        val downloads = LinkedHashMap<String, MinecraftPlannedDownload>()
        val warnings = mutableListOf<String>()

        add(downloads, MinecraftPlannedDownload(
            kind = KIND_VERSION_METADATA,
            destinationRelativePath = "versions/" + details.id + "/" + details.id + ".json",
            url = MojangUrlPolicy.validate(versionEntry.metadataUrl).toString(),
            expectedSha1 = versionEntry.sha1,
            expectedBytes = null,
            logicalName = details.id
        ))

        val client = details.clientDownload
        val clientVersionId = client?.let { details.clientDownloadVersionId ?: details.id }
        if (client == null) {
            warnings.add("Version metadata does not include a client download")
        } else {
            add(downloads, MinecraftPlannedDownload(
                kind = KIND_CLIENT,
                destinationRelativePath = "versions/" + clientVersionId + "/" + clientVersionId + ".jar",
                url = MojangUrlPolicy.validate(client.url).toString(),
                expectedSha1 = client.sha1,
                expectedBytes = client.sizeBytes,
                logicalName = clientVersionId
            ))
        }

        val assetDescriptor = details.assetIndex
        if (assetDescriptor == null) {
            warnings.add("Version metadata does not include an asset index")
        } else {
            add(downloads, MinecraftPlannedDownload(
                kind = KIND_ASSET_INDEX,
                destinationRelativePath = "assets/indexes/" + assetDescriptor.id + ".json",
                url = MojangUrlPolicy.validate(assetDescriptor.url).toString(),
                expectedSha1 = assetDescriptor.sha1,
                expectedBytes = assetDescriptor.sizeBytes,
                logicalName = assetDescriptor.id
            ))
            if (assetIndexContents == null) {
                warnings.add("Asset index contents have not been parsed; asset objects are not yet enumerated")
            } else {
                assetIndexContents.objects.forEach { asset ->
                    add(downloads, MinecraftPlannedDownload(
                        kind = KIND_ASSET_OBJECT,
                        destinationRelativePath = asset.relativeObjectPath,
                        url = MojangUrlPolicy.validate(asset.url).toString(),
                        expectedSha1 = asset.hash,
                        expectedBytes = asset.sizeBytes,
                        logicalName = asset.name
                    ))
                }
            }
        }

        if (details.inheritsFrom != null) {
            warnings.add("Version inherits from " + details.inheritsFrom + "; parent metadata must be resolved before launch")
        }

        details.libraries.forEach { library ->
            if (!ruleEvaluator.isAllowed(library.rules, platform)) return@forEach
            val libraryDownloads = library.downloads
            val artifact = libraryDownloads?.artifact
            if (artifact != null) {
                add(downloads, plannedLibraryDownload(artifact, KIND_LIBRARY, library.name, emptyList()))
            } else if (libraryDownloads == null ||
                (libraryDownloads.classifiers.isEmpty() && library.natives.isEmpty())) {
                val path = MinecraftMavenPath.derive(library.name)
                val base = library.urlBase ?: DEFAULT_LIBRARY_BASE
                val url = resolveBaseUrl(base, path)
                add(downloads, MinecraftPlannedDownload(
                    kind = KIND_LIBRARY,
                    destinationRelativePath = "libraries/" + path,
                    url = url,
                    expectedSha1 = null,
                    expectedBytes = null,
                    logicalName = library.name
                ))
            }

            val nativeTemplate = library.natives[platform.osName]
            if (nativeTemplate != null) {
                val nativeClassifier = nativeTemplate.replace("$" + "{arch}", nativeArchBits(platform.architecture))
                val nativeArtifact = libraryDownloads?.classifiers?.get(nativeClassifier)
                if (nativeArtifact == null) {
                    warnings.add("Missing native classifier " + nativeClassifier + " for " + library.name)
                } else {
                    add(downloads, plannedLibraryDownload(
                        nativeArtifact,
                        KIND_NATIVE_LIBRARY,
                        library.name + ":" + nativeClassifier,
                        library.extractExcludes
                    ))
                }
            }
        }

        return MinecraftInstallPlan(
            versionId = details.id,
            javaMajorVersion = details.javaMajorVersion,
            mainClass = details.mainClass,
            downloads = downloads.values.toList(),
            warnings = warnings.distinct(),
            clientArtifactVersionId = clientVersionId
        )
    }

    private fun plannedLibraryDownload(
        artifact: MinecraftLibraryArtifact,
        kind: String,
        logicalName: String,
        excludes: List<String>
    ): MinecraftPlannedDownload = MinecraftPlannedDownload(
        kind = kind,
        destinationRelativePath = "libraries/" + MinecraftMavenPath.validateRelativePath(artifact.path),
        url = MojangUrlPolicy.validate(artifact.url).toString(),
        expectedSha1 = artifact.sha1,
        expectedBytes = artifact.sizeBytes,
        extractExcludes = excludes,
        logicalName = logicalName
    )

    private fun add(
        target: LinkedHashMap<String, MinecraftPlannedDownload>,
        download: MinecraftPlannedDownload
    ) {
        val old = target[download.destinationRelativePath]
        if (old == null) {
            target[download.destinationRelativePath] = download
        } else {
            require(old.url == download.url && old.expectedSha1 == download.expectedSha1 &&
                old.expectedBytes == download.expectedBytes) {
                "Conflicting artifacts target " + download.destinationRelativePath
            }
        }
    }

    private fun resolveBaseUrl(base: String, path: String): String {
        val normalized = if (base.endsWith("/")) base else base + "/"
        val url = normalized + path
        return MojangUrlPolicy.validate(url).toString()
    }

    private fun nativeArchBits(architecture: String): String {
        val value = architecture.lowercase()
        return if (value.contains("64")) "64" else "32"
    }

    companion object {
        private const val DEFAULT_LIBRARY_BASE = "https://libraries.minecraft.net/"
        const val KIND_VERSION_METADATA = "version_metadata"
        const val KIND_CLIENT = "client"
        const val KIND_ASSET_INDEX = "asset_index"
        const val KIND_ASSET_OBJECT = "asset_object"
        const val KIND_LIBRARY = "library"
        const val KIND_NATIVE_LIBRARY = "native_library"
    }
}
