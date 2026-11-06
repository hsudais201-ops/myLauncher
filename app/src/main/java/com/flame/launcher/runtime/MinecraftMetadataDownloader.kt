package com.flame.launcher.runtime

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

/**
 * Fetches Mojang's version manifest and version JSON with bounded memory use.
 *
 * Methods perform blocking network I/O and must be called from a worker thread,
 * never from the Android main thread. Version JSON bytes are checked against the
 * SHA-1 published by the version manifest before being returned to the parser.
 */
class MinecraftMetadataDownloader(
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val connectTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val manifestMaxBytes: Int = DEFAULT_MANIFEST_MAX_BYTES,
    private val detailsMaxBytes: Int = DEFAULT_DETAILS_MAX_BYTES
) {
    init {
        require(connectTimeoutMs in 1..MAX_TIMEOUT_MS)
        require(readTimeoutMs in 1..MAX_TIMEOUT_MS)
        require(manifestMaxBytes in 1..MAX_METADATA_BYTES)
        require(detailsMaxBytes in 1..MAX_METADATA_BYTES)
    }

    fun downloadManifest(): String =
        download(MANIFEST_URL, TRUSTED_METADATA_HOSTS, manifestMaxBytes, expectedSha1 = null)

    fun downloadVersionDetails(entry: MinecraftVersionEntry): String {
        require(entry.sha1.matches(SHA1_PATTERN)) { "Minecraft version entry has an invalid SHA-1 digest." }
        return download(entry.metadataUrl, TRUSTED_METADATA_HOSTS, detailsMaxBytes, entry.sha1)
    }

    private fun download(
        address: String,
        allowedHosts: Set<String>,
        maxBytes: Int,
        expectedSha1: String?
    ): String {
        val uri = try {
            URI(address)
        } catch (failure: Exception) {
            throw IllegalArgumentException("Minecraft metadata URL is invalid.", failure)
        }
        require(
            uri.scheme == "https" &&
                uri.host?.lowercase() in allowedHosts &&
                uri.userInfo == null &&
                uri.port in setOf(-1, 443)
        ) { "Minecraft metadata URL is not trusted." }

        val connection = connectionFactory(URL(address))
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            require(status == HttpURLConnection.HTTP_OK) {
                "Minecraft metadata server returned HTTP $status."
            }
            val declaredLength = connection.contentLengthLong
            require(declaredLength == -1L || declaredLength in 1..maxBytes.toLong()) {
                "Minecraft metadata response exceeds the allowed size."
            }

            val output = ByteArrayOutputStream(
                if (declaredLength in 1..maxBytes.toLong()) declaredLength.toInt() else minOf(8192, maxBytes)
            )
            connection.inputStream.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    total += count
                    require(total <= maxBytes) { "Minecraft metadata response exceeds the allowed size." }
                    output.write(buffer, 0, count)
                }
            }
            val bytes = output.toByteArray()
            require(bytes.isNotEmpty()) { "Minecraft metadata response is empty." }
            if (expectedSha1 != null) {
                val actual = MessageDigest.getInstance("SHA-1")
                    .digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                require(actual.equals(expectedSha1, ignoreCase = true)) {
                    "Minecraft version metadata SHA-1 verification failed."
                }
            }
            return bytes.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        private const val DEFAULT_TIMEOUT_MS = 15_000
        private const val MAX_TIMEOUT_MS = 120_000
        private const val DEFAULT_MANIFEST_MAX_BYTES = 4 * 1024 * 1024
        private const val DEFAULT_DETAILS_MAX_BYTES = 8 * 1024 * 1024
        private const val MAX_METADATA_BYTES = 16 * 1024 * 1024
        private val SHA1_PATTERN = Regex("[a-fA-F0-9]{40}")
        private val TRUSTED_METADATA_HOSTS = setOf("piston-meta.mojang.com", "launchermeta.mojang.com")
    }
}
