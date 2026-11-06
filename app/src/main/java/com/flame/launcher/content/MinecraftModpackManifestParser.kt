package com.flame.launcher.content

import java.net.URI
import java.util.Locale

/**
 * Strict, dependency-free parser for the Modrinth modpack index (format version 1).
 * The JSON reader rejects duplicate keys, malformed escapes, trailing data and
 * excessive nesting rather than trying to repair ambiguous manifests.
 */
class MinecraftModpackManifestParser {
    data class RemoteFile(
        val path: String,
        val downloads: List<String>,
        val sha1: String?,
        val sha512: String?,
        val fileSizeBytes: Long,
        val clientEnvironment: String,
        val serverEnvironment: String
    )

    data class Manifest(
        val name: String,
        val versionId: String,
        val minecraftVersion: String,
        val loaderDependencies: Map<String, String>,
        val files: List<RemoteFile>
    )

    sealed interface Result {
        data class Parsed(val manifest: Manifest) : Result
        data class Rejected(val reason: String) : Result
    }

    fun parse(json: String): Result {
        if (json.length > MAX_MANIFEST_CHARS) return Result.Rejected("Modpack manifest is too large")
        return try {
            val root = JsonReader(json).read() as? Map<*, *>
                ?: return Result.Rejected("Manifest root must be a JSON object")
            if (root["formatVersion"] != 1L) return Result.Rejected("Unsupported Modrinth modpack format version")
            if (root["game"] != "minecraft") return Result.Rejected("Modpack game must be minecraft")

            val name = root.string("name")?.takeIf { it.isNotBlank() }
                ?: return Result.Rejected("Manifest name is missing")
            val versionId = root.string("versionId")?.takeIf { it.isNotBlank() }
                ?: return Result.Rejected("Manifest versionId is missing")
            val dependencies = root["dependencies"] as? Map<*, *>
                ?: return Result.Rejected("Manifest dependencies must be an object")
            val minecraftVersion = dependencies["minecraft"] as? String
                ?: return Result.Rejected("Manifest Minecraft version is missing")
            if (!MINECRAFT_VERSION.matches(minecraftVersion)) {
                return Result.Rejected("Manifest Minecraft version is invalid")
            }

            val loaders = linkedMapOf<String, String>()
            for ((key, value) in dependencies) {
                if (key == "minecraft") continue
                if (key !is String || !LOADER_KEYS.contains(key)) {
                    return Result.Rejected("Manifest contains an unsupported loader dependency")
                }
                if (value !is String || value.isBlank()) {
                    return Result.Rejected("Manifest loader dependency is invalid")
                }
                loaders[key] = value
            }

            val rawFiles = root["files"] as? List<*>
                ?: return Result.Rejected("Manifest files must be an array")
            if (rawFiles.size > MAX_FILES) return Result.Rejected("Manifest contains too many files")
            val seenPaths = HashSet<String>()
            val files = mutableListOf<RemoteFile>()
            for (rawFile in rawFiles) {
                val item = rawFile as? Map<*, *>
                    ?: return Result.Rejected("Manifest file entry must be an object")
                val path = item.string("path")?.let(::normalizePath)
                    ?: return Result.Rejected("Manifest contains an unsafe file path")
                if (!seenPaths.add(path.lowercase(Locale.ROOT))) {
                    return Result.Rejected("Manifest contains duplicate file paths")
                }

                val hashes = item["hashes"] as? Map<*, *>
                    ?: return Result.Rejected("Manifest file hashes are missing")
                val sha1 = hashes["sha1"] as? String
                val sha512 = hashes["sha512"] as? String
                if (sha1 == null && sha512 == null) {
                    return Result.Rejected("Each modpack file must provide SHA-1 or SHA-512")
                }
                if (sha1 != null && !SHA1.matches(sha1)) {
                    return Result.Rejected("Manifest contains an invalid SHA-1 hash")
                }
                if (sha512 != null && !SHA512.matches(sha512)) {
                    return Result.Rejected("Manifest contains an invalid SHA-512 hash")
                }

                val rawDownloads = item["downloads"] as? List<*>
                    ?: return Result.Rejected("Manifest downloads must be an array")
                if (rawDownloads.any { it !is String }) {
                    return Result.Rejected("Manifest download URLs must be strings")
                }
                val downloads = rawDownloads.filterIsInstance<String>()
                if (downloads.isEmpty() || downloads.size > 8 || downloads.any { !isTrustedDownload(it) }) {
                    return Result.Rejected("Manifest contains an untrusted or missing download URL")
                }

                val size = item["fileSize"] as? Long
                    ?: return Result.Rejected("Manifest file size is missing or invalid")
                if (size <= 0L || size > MAX_FILE_BYTES) {
                    return Result.Rejected("Manifest file size is outside the supported range")
                }
                val env = item["env"] as? Map<*, *> ?: emptyMap<Any, Any>()
                val client = env["client"] as? String ?: "required"
                val server = env["server"] as? String ?: "required"
                if (client !in ENVIRONMENTS || server !in ENVIRONMENTS) {
                    return Result.Rejected("Manifest contains an invalid environment value")
                }

                files += RemoteFile(path, downloads, sha1?.lowercase(Locale.ROOT),
                    sha512?.lowercase(Locale.ROOT), size, client, server)
            }

            Result.Parsed(Manifest(name, versionId, minecraftVersion, loaders, files))
        } catch (error: Exception) {
            Result.Rejected("Could not parse modpack manifest: ${error.message ?: "invalid JSON"}")
        }
    }

    private fun Map<*, *>.string(key: String): String? = this[key] as? String

    private fun normalizePath(raw: String): String? {
        if (raw.isBlank() || raw.length > 1024 || raw.indexOf('\\') >= 0 ||
            raw.indexOf('\u0000') >= 0 || raw.startsWith('/') ||
            (raw.length >= 2 && raw[1] == ':')
        ) return null
        val segments = raw.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) return null
        return segments.joinToString("/")
    }

    private fun isTrustedDownload(raw: String): Boolean = try {
        val uri = URI(raw)
        uri.scheme.equals("https", true) &&
            uri.host?.lowercase(Locale.ROOT) == "cdn.modrinth.com" &&
            uri.userInfo == null && uri.fragment == null &&
            (uri.port == -1 || uri.port == 443)
    } catch (_: Exception) {
        false
    }

    private class JsonReader(private val source: String) {
        private var index = 0
        private var depth = 0

        fun read(): Any? {
            val value = value()
            whitespace()
            require(index == source.length) { "Trailing JSON data" }
            return value
        }

        private fun value(): Any? {
            whitespace()
            require(index < source.length) { "Unexpected end of JSON" }
            require(depth++ < 64) { "JSON nesting is too deep" }
            val result = when (source[index]) {
                '{' -> objectValue()
                '[' -> arrayValue()
                '"' -> stringValue()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                '-', in '0'..'9' -> numberValue()
                else -> error("Unexpected JSON token")
            }
            depth--
            return result
        }

        private fun objectValue(): Map<String, Any?> {
            index++
            whitespace()
            val result = linkedMapOf<String, Any?>()
            if (take('}')) return result
            while (true) {
                whitespace()
                require(index < source.length && source[index] == '"') { "Object key must be a string" }
                val key = stringValue()
                require(!result.containsKey(key)) { "Duplicate JSON object key" }
                whitespace()
                require(take(':')) { "Missing colon after object key" }
                result[key] = value()
                whitespace()
                if (take('}')) return result
                require(take(',')) { "Missing comma in object" }
            }
        }

        private fun arrayValue(): List<Any?> {
            index++
            whitespace()
            val result = mutableListOf<Any?>()
            if (take(']')) return result
            while (true) {
                result += value()
                whitespace()
                if (take(']')) return result
                require(take(',')) { "Missing comma in array" }
            }
        }

        private fun stringValue(): String {
            require(take('"'))
            val out = StringBuilder()
            while (index < source.length) {
                val char = source[index++]
                when {
                    char == '"' -> return out.toString()
                    char == '\\' -> {
                        require(index < source.length) { "Incomplete string escape" }
                        when (val escaped = source[index++]) {
                            '"', '\\', '/' -> out.append(escaped)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000c')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                require(index + 4 <= source.length) { "Incomplete Unicode escape" }
                                val code = source.substring(index, index + 4).toInt(16)
                                out.append(code.toChar())
                                index += 4
                            }
                            else -> error("Invalid string escape")
                        }
                    }
                    char.code < 0x20 -> error("Control character in JSON string")
                    else -> out.append(char)
                }
            }
            error("Unterminated JSON string")
        }

        private fun numberValue(): Any {
            val start = index
            if (take('-')) Unit
            require(index < source.length) { "Invalid JSON number" }
            if (take('0')) {
                require(index >= source.length || source[index] !in '0'..'9') { "Leading zero in JSON number" }
            } else {
                require(source[index] in '1'..'9') { "Invalid JSON number" }
                while (index < source.length && source[index] in '0'..'9') index++
            }
            var decimal = false
            if (take('.')) {
                decimal = true
                require(index < source.length && source[index] in '0'..'9') { "Invalid JSON fraction" }
                while (index < source.length && source[index] in '0'..'9') index++
            }
            if (index < source.length && (source[index] == 'e' || source[index] == 'E')) {
                decimal = true
                index++
                if (index < source.length && (source[index] == '+' || source[index] == '-')) index++
                require(index < source.length && source[index] in '0'..'9') { "Invalid JSON exponent" }
                while (index < source.length && source[index] in '0'..'9') index++
            }
            val raw = source.substring(start, index)
            return if (decimal) raw.toDouble() else raw.toLong()
        }

        private fun <T> literal(token: String, result: T): T {
            require(source.startsWith(token, index)) { "Invalid JSON literal" }
            index += token.length
            return result
        }

        private fun whitespace() {
            while (index < source.length && source[index] in listOf(' ', '\n', '\r', '\t')) index++
        }

        private fun take(char: Char): Boolean {
            if (index < source.length && source[index] == char) {
                index++
                return true
            }
            return false
        }
    }

    private companion object {
        const val MAX_MANIFEST_CHARS = 4 * 1024 * 1024
        const val MAX_FILES = 20_000
        const val MAX_FILE_BYTES = 1L shl 30
        val SHA1 = Regex("^[a-fA-F0-9]{40}$")
        val SHA512 = Regex("^[a-fA-F0-9]{128}$")
        val MINECRAFT_VERSION = Regex("^[0-9]+\\.[0-9]+(?:\\.[0-9]+)?(?:-[A-Za-z0-9.-]+)?$")
        val LOADER_KEYS = setOf("fabric-loader", "forge", "neoforge", "quilt-loader")
        val ENVIRONMENTS = setOf("required", "optional", "unsupported")
    }
}
