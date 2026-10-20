package com.voidcraft.launcher.core

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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

data class MinecraftLibraryArtifact(
    val path: String,
    val url: String,
    val sha1: String?,
    val sizeBytes: Long?
)

data class MinecraftLibraryDownloads(
    val artifact: MinecraftLibraryArtifact?,
    val classifiers: Map<String, MinecraftLibraryArtifact>
)

data class MinecraftLibraryRule(
    val action: String,
    val osName: String?,
    val osVersionRegex: String?,
    val osArchitectureRegex: String?,
    val features: Map<String, Boolean>
)

data class MinecraftLibrary(
    val name: String,
    val urlBase: String?,
    val downloads: MinecraftLibraryDownloads?,
    val rules: List<MinecraftLibraryRule>,
    val natives: Map<String, String>,
    val extractExcludes: List<String>
)

object MinecraftMavenPath {
    fun derive(coordinate: String, classifierOverride: String? = null): String {
        val pieces = coordinate.split("@", limit = 2)
        require(pieces.size <= 2) { "Invalid Maven coordinate: " + coordinate }
        val values = pieces[0].split(":")
        require(values.size in 3..4 && values.all { it.isNotBlank() }) {
            "Invalid Maven coordinate: " + coordinate
        }
        val group = values[0]
        val artifact = values[1]
        val version = values[2]
        val classifier = classifierOverride ?: values.getOrNull(3)
        val extension = pieces.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "jar"
        require(Regex("[A-Za-z0-9._+-]{1,128}").matches(artifact)) {
            "Invalid Maven artifact name: " + artifact
        }
        require(Regex("[A-Za-z0-9._+-]{1,128}").matches(version)) {
            "Invalid Maven version: " + version
        }
        require(Regex("[A-Za-z0-9._+-]{1,128}").matches(extension)) {
            "Invalid Maven extension: " + extension
        }
        if (classifier != null) {
            require(Regex("[A-Za-z0-9._+-]{1,128}").matches(classifier)) {
                "Invalid Maven classifier: " + classifier
            }
        }
        val groupPath = group.split(".").joinToString("/")
        val fileName = artifact + "-" + version + (classifier?.let { "-" + it } ?: "") + "." + extension
        return validateRelativePath(groupPath + "/" + artifact + "/" + version + "/" + fileName)
    }

    fun validateRelativePath(path: String): String {
        require(path.isNotBlank() && path.length <= 1024) { "Artifact path is empty or too long" }
        require(!path.startsWith("/") && !path.startsWith("\\") && !path.contains("\\")) {
            "Artifact path must be relative and use forward slashes"
        }
        val checked = path.trimEnd('/')
        require(checked.isNotBlank()) { "Artifact path is empty" }
        require(checked.split("/").all { it.isNotBlank() && it != "." && it != ".." }) {
            "Artifact path contains an unsafe segment"
        }
        require(checked.none { it == ':' || it.code < 32 }) { "Artifact path contains an unsafe character" }
        return path
    }
}

data class MinecraftVersionDetails(
    val id: String,
    val type: String,
    val mainClass: String?,
    val inheritsFrom: String?,
    val assets: String?,
    val assetIndex: MinecraftAssetIndex?,
    val clientDownload: MinecraftArtifactDownload?,
    val javaMajorVersion: Int?,
    val libraries: List<MinecraftLibrary> = emptyList(),
    /** Version directory owning the client JAR, which may be an inherited parent version. */
    val clientDownloadVersionId: String? = null,
    val arguments: MinecraftVersionArguments? = null,
    /** Raw legacy format is preserved; it is not tokenized or substituted here. */
    val legacyMinecraftArguments: String? = null
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
        val arguments = obj.get("arguments")?.let { element ->
            if (element.isJsonNull) {
                null
            } else {
                require(element.isJsonObject) { "Version arguments field must be an object" }
                parseVersionArguments(element.asJsonObject)
            }
        }
        val legacyMinecraftArguments = stringField(obj, "minecraftArguments", false)
        val libraries = parseLibraries(obj)
        return MinecraftVersionDetails(
            id = id,
            type = type,
            mainClass = stringField(obj, "mainClass", false),
            inheritsFrom = inherits,
            assets = stringField(obj, "assets", false),
            assetIndex = assetIndex,
            clientDownload = client,
            javaMajorVersion = javaMajor,
            libraries = libraries,
            clientDownloadVersionId = if (client != null) id else null,
            arguments = arguments,
            legacyMinecraftArguments = legacyMinecraftArguments
        )
    }

    private fun parseVersionArguments(argumentObject: JsonObject): MinecraftVersionArguments =
        MinecraftVersionArguments(
            game = parseArgumentList(argumentObject, "game"),
            jvm = parseArgumentList(argumentObject, "jvm")
        )

    private fun parseArgumentList(
        argumentObject: JsonObject,
        key: String
    ): List<MinecraftArgumentValue>? {
        val element = argumentObject.get(key) ?: return null
        require(element.isJsonArray) { "Version argument field " + key + " must be an array" }
        require(element.asJsonArray.size() <= MAX_ARGUMENT_ENTRIES) {
            "Version argument field " + key + " contains too many entries"
        }
        return element.asJsonArray.mapIndexed { index, item ->
            when {
                item.isJsonPrimitive && item.asJsonPrimitive.isString -> {
                    validateArgumentLength(item.asString, key, index)
                    MinecraftArgumentValue(listOf(item.asString))
                }
                item.isJsonObject -> {
                    val argument = item.asJsonObject
                    val valueElement = argument.get("value")
                        ?: throw IllegalArgumentException("Argument object " + index + " in " + key + " has no value")
                    val values = when {
                        valueElement.isJsonPrimitive && valueElement.asJsonPrimitive.isString ->
                            listOf(valueElement.asString)
                        valueElement.isJsonArray -> valueElement.asJsonArray.mapIndexed { valueIndex, value ->
                            require(value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                                "Argument value " + valueIndex + " in " + key + " must be a string"
                            }
                            value.asString
                        }
                        else -> throw IllegalArgumentException(
                            "Argument value in " + key + " must be a string or an array of strings"
                        )
                    }
                    require(values.size <= MAX_ARGUMENT_VALUES_PER_ENTRY) {
                        "Argument object " + index + " in " + key + " contains too many values"
                    }
                    values.forEachIndexed { valueIndex, value ->
                        validateArgumentLength(value, key, valueIndex)
                    }
                    MinecraftArgumentValue(values, parseRuleArray(argument.get("rules"), "Argument"))
                }
                else -> throw IllegalArgumentException(
                    "Argument entry " + index + " in " + key + " must be a string or object"
                )
            }
        }
    }

    private fun validateArgumentLength(value: String, key: String, index: Int) {
        require(value.length <= MAX_ARGUMENT_LENGTH) {
            "Argument " + index + " in " + key + " exceeds the supported size"
        }
    }

    private fun parseLibraries(versionObject: JsonObject): List<MinecraftLibrary> {
        val element = versionObject.get("libraries") ?: return emptyList()
        require(element.isJsonArray) { "Version libraries field must be an array" }
        require(element.asJsonArray.size() <= MAX_LIBRARIES) { "Version has too many library entries" }
        return element.asJsonArray.mapIndexed { index, item ->
            require(item.isJsonObject) { "Library entry " + index + " must be an object" }
            val library = item.asJsonObject
            val name = stringField(library, "name", true)!!
            val urlBase = stringField(library, "url", false)?.let { raw ->
                val normalized = if (raw.endsWith("/")) raw else raw + "/"
                MojangUrlPolicy.validate(normalized)
                normalized
            }
            val downloadsObject = library.getAsJsonObject("downloads")
            val artifact = downloadsObject?.getAsJsonObject("artifact")?.let {
                parseLibraryArtifact(it, name, null)
            }
            val classifiers = LinkedHashMap<String, MinecraftLibraryArtifact>()
            val classifiersObject = downloadsObject?.getAsJsonObject("classifiers")
            if (classifiersObject != null) {
                classifiersObject.entrySet().forEach { (classifier, descriptor) ->
                    require(Regex("[A-Za-z0-9._+-]{1,128}").matches(classifier)) {
                        "Invalid library classifier: " + classifier
                    }
                    require(descriptor.isJsonObject) { "Library classifier must be an object" }
                    classifiers[classifier] = parseLibraryArtifact(descriptor.asJsonObject, name, classifier)
                }
            }
            MinecraftLibrary(
                name = name,
                urlBase = urlBase,
                downloads = if (downloadsObject == null) null else MinecraftLibraryDownloads(artifact, classifiers),
                rules = parseLibraryRules(library),
                natives = parseNativeMap(library),
                extractExcludes = parseExtractExcludes(library)
            )
        }
    }

    private fun parseLibraryArtifact(
        descriptor: JsonObject,
        libraryName: String,
        classifier: String?
    ): MinecraftLibraryArtifact {
        val url = MojangUrlPolicy.validate(stringField(descriptor, "url", true)!!).toString()
        val rawPath = stringField(descriptor, "path", false)
        val path = MinecraftMavenPath.validateRelativePath(rawPath ?: MinecraftMavenPath.derive(libraryName, classifier))
        val sha1 = sha1Field(descriptor, "sha1")
        val size = longField(descriptor, "size")
        return MinecraftLibraryArtifact(path, url, sha1, size)
    }

    private fun parseLibraryRules(libraryObject: JsonObject): List<MinecraftLibraryRule> =
        parseRuleArray(libraryObject.get("rules"), "Library")

    private fun parseRuleArray(element: JsonElement?, owner: String): List<MinecraftLibraryRule> {
        if (element == null) return emptyList()
        require(element.isJsonArray) { owner + " rules field must be an array" }
        require(element.asJsonArray.size() <= MAX_LIBRARY_RULES) {
            owner + " has too many rules"
        }
        return element.asJsonArray.mapIndexed { index, ruleElement ->
            require(ruleElement.isJsonObject) { owner + " rule " + index + " must be an object" }
            val rule = ruleElement.asJsonObject
            val action = stringField(rule, "action", true)!!
            require(action == "allow" || action == "disallow") {
                "Invalid " + owner.lowercase() + " rule action: " + action
            }
            val os = rule.getAsJsonObject("os")
            val osName = os?.let { stringField(it, "name", false) }
            val osVersion = os?.let { stringField(it, "version", false) }
            val osArch = os?.let { stringField(it, "arch", false) }
            osVersion?.let { validateRegex(it, owner + " OS version rule") }
            osArch?.let { validateRegex(it, owner + " OS architecture rule") }
            val features = LinkedHashMap<String, Boolean>()
            val featuresObject = rule.getAsJsonObject("features")
            if (featuresObject != null) {
                featuresObject.entrySet().forEach { (feature, value) ->
                    require(Regex("[A-Za-z0-9._-]{1,128}").matches(feature)) {
                        "Invalid " + owner.lowercase() + " rule feature name"
                    }
                    require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) {
                        owner + " rule features must be boolean values"
                    }
                    features[feature] = value.asBoolean
                }
            }
            MinecraftLibraryRule(action, osName, osVersion, osArch, features)
        }
    }

    private fun parseNativeMap(libraryObject: JsonObject): Map<String, String> {
        val element = libraryObject.get("natives") ?: return emptyMap()
        require(element.isJsonObject) { "Library natives field must be an object" }
        val result = LinkedHashMap<String, String>()
        element.asJsonObject.entrySet().forEach { (osName, classifierElement) ->
            require(Regex("[a-z0-9_-]{1,32}").matches(osName)) { "Invalid native OS key" }
            require(classifierElement.isJsonPrimitive && classifierElement.asJsonPrimitive.isString) {
                "Native classifier template must be a string"
            }
            val classifier = classifierElement.asString
            require(classifier.isNotBlank() && classifier.length <= 128 &&
                classifier.none { it == '/' || it == '\\' }) { "Invalid native classifier template" }
            result[osName] = classifier
        }
        return result
    }

    private fun parseExtractExcludes(libraryObject: JsonObject): List<String> {
        val extract = libraryObject.getAsJsonObject("extract") ?: return emptyList()
        val element = extract.get("exclude") ?: return emptyList()
        require(element.isJsonArray) { "Library extract.exclude must be an array" }
        return element.asJsonArray.map { item ->
            require(item.isJsonPrimitive && item.asJsonPrimitive.isString) {
                "Library extract exclusions must be strings"
            }
            val exclusion = item.asString
            require(exclusion.isNotBlank() && !exclusion.startsWith("/") &&
                !exclusion.contains("\\") &&
                exclusion.trimEnd('/').split("/").none { it == ".." || it == "." }) {
                "Unsafe native extraction exclusion"
            }
            exclusion
        }
    }

    private fun validateRegex(value: String, label: String) {
        try {
            Regex(value)
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid " + label + " regex", error)
        }
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
        private const val MAX_LIBRARIES = 20_000
        private const val MAX_LIBRARY_RULES = 256
        private const val MAX_ARGUMENT_ENTRIES = 100_000
        private const val MAX_ARGUMENT_VALUES_PER_ENTRY = 10_000
        private const val MAX_ARGUMENT_LENGTH = 65_536
        private val TYPE_PATTERN = Regex("[a-z_]{1,32}")
        private val SHA1_PATTERN = Regex("[0-9a-fA-F]{40}")
    }
}

fun interface HttpsTextFetcher {
    @Throws(IOException::class)
    fun fetch(url: String, maxBytes: Int): String
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
        val json = fetcher.fetch(entry.metadataUrl, MAX_METADATA_BYTES)
        val actualSha1 = sha1Hex(json)
        if (!actualSha1.equals(entry.sha1, ignoreCase = true)) {
            throw DownloadIntegrityException("Version metadata failed SHA-1 verification")
        }

        val details = detailsParser.parse(json)
        require(details.id == entry.id) {
            "Downloaded metadata ID " + details.id + " does not match requested version " + entry.id
        }
        return details
    }

    fun fetchAssetIndex(descriptor: MinecraftAssetIndex): MinecraftAssetIndexContents {
        MojangUrlPolicy.validate(descriptor.url)
        val json = fetcher.fetch(descriptor.url, MAX_METADATA_BYTES)
        val actualSha1 = sha1Hex(json)
        if (descriptor.sha1 != null && !actualSha1.equals(descriptor.sha1, ignoreCase = true)) {
            throw DownloadIntegrityException("Asset-index JSON failed SHA-1 verification")
        }
        if (descriptor.sizeBytes != null && json.toByteArray(StandardCharsets.UTF_8).size.toLong() != descriptor.sizeBytes) {
            throw DownloadIntegrityException("Asset-index JSON size does not match version metadata")
        }
        return MinecraftAssetIndexParser().parse(json)
    }

    private fun sha1Hex(value: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object {
        const val VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        private const val MAX_METADATA_BYTES = 5 * 1024 * 1024
    }
}
