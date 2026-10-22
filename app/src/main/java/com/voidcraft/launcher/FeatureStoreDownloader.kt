package com.voidcraft.launcher

import android.content.Context
import java.io.File
import java.net.URI
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

private const val MAX_FEATURE_PACKAGE_BYTES = 500L * 1024L * 1024L
private const val FEATURE_DOWNLOAD_TIMEOUT_MS = 20_000
private const val MAX_FEATURE_REDIRECTS = 5

/**
 * Downloads an optional Feature Store package without accepting insecure redirects
 * or allowing an unbounded response to fill the device's storage.
 *
 * This stores a package only; it does not execute it or claim that a downloaded
 * package has been integrated into Minecraft.
 */
internal fun downloadFeaturePackage(context: Context, feature: StoreFeature): File {
    val source = feature.packageUrl
        ?.trim()
        ?.takeIf { it.startsWith("https://", ignoreCase = true) }
        ?: error("This listing has no verified HTTPS package URL.")
    val expectedSha256 = feature.packageSha256
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
        ?: error("This listing has no valid, pinned SHA-256 package digest.")

    var current = runCatching { URI(source) }
        .getOrElse { error("The package URL is invalid.") }
    var redirects = 0

    while (true) {
        if (!current.scheme.equals("https", ignoreCase = true) ||
            current.host.isNullOrBlank() ||
            current.userInfo != null
        ) {
            error("Only HTTPS package URLs without embedded credentials are allowed.")
        }

        val connection = (current.toURL().openConnection() as HttpsURLConnection).apply {
            connectTimeout = FEATURE_DOWNLOAD_TIMEOUT_MS
            readTimeout = FEATURE_DOWNLOAD_TIMEOUT_MS
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "Voidcraft-FeatureStore/1.0")
        }

        val responseCode = try {
            connection.responseCode
        } catch (error: Exception) {
            connection.disconnect()
            throw error
        }

        val isRedirect = responseCode == 301 ||
            responseCode == 302 ||
            responseCode == 303 ||
            responseCode == 307 ||
            responseCode == 308

        if (isRedirect) {
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (redirects >= MAX_FEATURE_REDIRECTS) {
                error("The package server redirected too many times.")
            }
            if (location.isNullOrBlank()) {
                error("The package server returned a redirect without a destination.")
            }
            val next = runCatching { current.resolve(location) }
                .getOrElse { error("The package server returned an invalid redirect.") }
            if (!next.scheme.equals("https", ignoreCase = true) ||
                next.host.isNullOrBlank() ||
                next.userInfo != null
            ) {
                error("Blocked an insecure package redirect.")
            }
            current = next
            redirects++
            continue
        }

        try {
            if (responseCode !in 200..299) {
                error("Package download failed: HTTP $responseCode.")
            }

            val expectedLength = connection.contentLengthLong
            if (expectedLength > MAX_FEATURE_PACKAGE_BYTES) {
                error("Package exceeds the 500 MiB safety limit.")
            }

            val directory = File(context.filesDir, "feature-store")
            if (!directory.isDirectory && !directory.mkdirs()) {
                error("Could not create the Feature Store download directory.")
            }

            val safeId = feature.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val destination = File(directory, "$safeId.package")
            val temporary = File(directory, "$safeId.${System.nanoTime()}.part")
            var total = 0L
            val digest = MessageDigest.getInstance("SHA-256")

            try {
                connection.inputStream.use { input ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_FEATURE_PACKAGE_BYTES) {
                                error("Package exceeded the 500 MiB safety limit while downloading.")
                            }
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                    }
                }

                if (expectedLength >= 0 && total != expectedLength) {
                    error("Downloaded package size did not match the server response.")
                }
                val actualSha256 = digest.digest().joinToString("") { byte ->
                    "%02x".format(byte.toInt() and 0xff)
                }
                if (actualSha256 != expectedSha256) {
                    error("Downloaded package SHA-256 does not match the catalog digest.")
                }

                val backup = if (destination.exists()) {
                    File(directory, "$safeId.previous").also { previous ->
                        if (previous.exists() && !previous.delete()) {
                            error("Could not prepare a safe package update.")
                        }
                        if (!destination.renameTo(previous)) {
                            error("Could not preserve the previously downloaded package.")
                        }
                    }
                } else {
                    null
                }

                if (!temporary.renameTo(destination)) {
                    backup?.renameTo(destination)
                    error("Could not finalize the downloaded package.")
                }
                backup?.delete()
                return destination
            } finally {
                temporary.delete()
            }
        } finally {
            connection.disconnect()
        }
    }
}
