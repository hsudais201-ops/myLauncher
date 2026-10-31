package com.hyperlauncher.mobile

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/**
 * Downloads only catalogued Android JRE archives and verifies their exact
 * byte length and SHA-256 before publishing the archive in the cache.
 *
 * Call from a worker thread. Partial downloads remain hidden as .part files
 * and are removed on failure or cancellation.
 */
object VerifiedAndroidRuntimeDownloader {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_REDIRECTS = 5
    private const val BUFFER_SIZE = 64 * 1024

    private val trustedHosts = setOf(
        "github.com",
        "release-assets.githubusercontent.com",
        "objects.githubusercontent.com"
    )

    fun download(
        runtimePackage: AndroidRuntimePackage,
        cacheDirectory: File,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null,
        shouldCancel: (() -> Boolean)? = null
    ): File {
        require(
            AndroidRuntimePackageCatalog.exact(
                runtimePackage.javaMajor,
                runtimePackage.abi
            ) == runtimePackage
        ) { "Android runtime package is not in the verified package catalog" }
        require(runtimePackage.sizeBytes > 0L) { "Runtime archive size is invalid" }
        require(Regex("^[a-fA-F0-9]{64}$").matches(runtimePackage.sha256)) {
            "Runtime archive SHA-256 is invalid"
        }

        val root = cacheDirectory.canonicalFile
        if (!root.exists() && !root.mkdirs()) error("Could not create runtime cache directory")
        require(root.isDirectory) { "Runtime cache path is not a directory" }
        val target = File(root, runtimePackage.archiveFileName).canonicalFile
        require(target.parentFile == root) { "Runtime archive path escapes cache directory" }
        val temporary = File(root, runtimePackage.archiveFileName + ".part")
        temporary.delete()

        try {
            if (shouldCancel?.invoke() == true) {
                throw CancellationException("Android runtime download cancelled")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            val connection = openResponse(runtimePackage.downloadUrl)
            try {
                val response = connection.responseCode
                require(response == HttpURLConnection.HTTP_OK) {
                    "Runtime download server returned HTTP $response"
                }
                val responseLength = connection.contentLengthLong
                require(responseLength < 0L || responseLength == runtimePackage.sizeBytes) {
                    "Runtime archive response size did not match the catalog"
                }

                connection.inputStream.use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            if (shouldCancel?.invoke() == true) {
                                throw CancellationException("Android runtime download cancelled")
                            }
                            val count = input.read(buffer)
                            if (count < 0) break
                            downloaded = Math.addExact(downloaded, count.toLong())
                            require(downloaded <= runtimePackage.sizeBytes) {
                                "Runtime archive exceeded the expected file size"
                            }
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                            onProgress?.invoke(downloaded, runtimePackage.sizeBytes)
                        }
                        if (shouldCancel?.invoke() == true) {
                            throw CancellationException("Android runtime download cancelled")
                        }
                        output.fd.sync()
                    }
                }
            } finally {
                connection.disconnect()
            }

            require(downloaded == runtimePackage.sizeBytes) {
                "Runtime archive ended before the expected file size"
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            require(actualHash.equals(runtimePackage.sha256, ignoreCase = true)) {
                "Android runtime archive failed SHA-256 verification"
            }

            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            return target
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    private fun openResponse(initialUrl: String): HttpURLConnection {
        var current = initialUrl
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            require(isTrustedUrl(current)) { "Runtime archive URL is not trusted" }
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "HyperLauncher/0.1.0 (Android)")
                setRequestProperty("Accept", "application/octet-stream")
            }
            val code = try {
                connection.responseCode
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                require(!location.isNullOrBlank() && redirectCount < MAX_REDIRECTS) {
                    "Runtime archive redirect limit exceeded"
                }
                current = URL(URL(current), location).toString()
            } else {
                return connection
            }
        }
        throw IllegalStateException("Runtime archive redirect limit exceeded")
    }

    internal fun isTrustedUrl(value: String): Boolean = try {
        val url = URL(value)
        url.protocol == "https" &&
            url.host.lowercase() in trustedHosts &&
            url.userInfo == null &&
            url.port in listOf(-1, 443)
    } catch (_: Exception) {
        false
    }
}
