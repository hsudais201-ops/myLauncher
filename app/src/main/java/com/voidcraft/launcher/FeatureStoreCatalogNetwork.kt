package com.voidcraft.launcher

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

private const val MAX_FEATURE_CATALOG_BYTES = 1024L * 1024L
private const val FEATURE_CATALOG_TIMEOUT_MS = 5_000
private const val MAX_FEATURE_CATALOG_REDIRECTS = 4

private val approvedFeatureCatalogHosts = setOf(
    "raw.githubusercontent.com",
    "github.com",
    "raw.github.com"
)

/** A catalog is small metadata, not an arbitrary download: cap it at 1 MiB. */
internal fun featureCatalogContentLengthAllowed(contentLength: Long): Boolean =
    contentLength == -1L || contentLength in 1L..MAX_FEATURE_CATALOG_BYTES

/** Only GitHub catalog hosts over ordinary HTTPS are accepted, including on every redirect hop. */
internal fun isApprovedFeatureCatalogUri(uri: URI): Boolean =
    uri.scheme.equals("https", ignoreCase = true) &&
        uri.host?.lowercase()?.let { it in approvedFeatureCatalogHosts } == true &&
        (uri.port == -1 || uri.port == 443) &&
        uri.rawUserInfo == null

/**
 * Reads a catalog with a hard streaming cap, even if the server omits Content-Length.
 * The input remains owned by the caller so the HTTP connection can always be closed.
 */
internal fun readBoundedFeatureCatalog(input: InputStream, declaredLength: Long): ByteArray {
    if (!featureCatalogContentLengthAllowed(declaredLength)) {
        error("The Feature Store catalog exceeds the 1 MiB safety limit or is empty.")
    }

    val body = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) continue
        total += count
        if (total > MAX_FEATURE_CATALOG_BYTES) {
            error("The Feature Store catalog exceeded the 1 MiB safety limit.")
        }
        body.write(buffer, 0, count)
    }

    if (body.size() == 0) error("The Feature Store catalog response was empty.")
    if (declaredLength >= 0L && body.size().toLong() != declaredLength) {
        error("The Feature Store catalog response length did not match its headers.")
    }
    return body.toByteArray()
}

/**
 * Fetches a small Feature Store catalog with HTTPS-only redirects and a hard streamed-byte limit.
 * The caller validates the JSON schema and falls back to the bundled catalog on any failure.
 */
internal fun fetchFeatureCatalogJson(source: String): String {
    var current = try {
        URI(source)
    } catch (_: URISyntaxException) {
        error("The Feature Store catalog URL is invalid.")
    }
    var redirects = 0

    while (true) {
        if (!isApprovedFeatureCatalogUri(current)) {
            error("The Feature Store catalog must use an approved GitHub HTTPS URL.")
        }

        val connection = (current.toURL().openConnection() as HttpsURLConnection).apply {
            connectTimeout = FEATURE_CATALOG_TIMEOUT_MS
            readTimeout = FEATURE_CATALOG_TIMEOUT_MS
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "Voidcraft-FeatureStore/1.0")
        }

        val responseCode = try {
            connection.responseCode
        } catch (error: Exception) {
            connection.disconnect()
            throw error
        }

        val redirect = responseCode == 301 ||
            responseCode == 302 ||
            responseCode == 303 ||
            responseCode == 307 ||
            responseCode == 308

        if (redirect) {
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (redirects >= MAX_FEATURE_CATALOG_REDIRECTS) {
                error("The Feature Store catalog redirected too many times.")
            }
            if (location.isNullOrBlank()) {
                error("The Feature Store catalog redirect has no destination.")
            }
            current = try {
                current.resolve(location)
            } catch (_: IllegalArgumentException) {
                error("The Feature Store catalog redirect URL is invalid.")
            }
            if (!isApprovedFeatureCatalogUri(current)) {
                error("Blocked a catalog redirect outside approved GitHub HTTPS hosts.")
            }
            redirects++
            continue
        }

        try {
            if (responseCode !in 200..299) {
                error("Feature Store catalog request failed: HTTP $responseCode.")
            }
            val declaredLength = connection.contentLengthLong
            val body = connection.inputStream.use { input ->
                readBoundedFeatureCatalog(input, declaredLength)
            }
            return String(body, StandardCharsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }
}
