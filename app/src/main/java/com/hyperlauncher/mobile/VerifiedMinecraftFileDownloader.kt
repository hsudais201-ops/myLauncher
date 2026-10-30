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
 * Downloads one metadata-described file into app-private storage.
 *
 * Call from a worker thread. A file is made visible at its final path only
 * after its declared byte count and SHA-1 have both been verified.
 */
object VerifiedMinecraftFileDownloader {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_REDIRECTS = 5
    private const val BUFFER_SIZE = 64 * 1024
    private val allowedHosts = setOf(
        "piston-data.mojang.com",
        "piston-meta.mojang.com",
        "launchermeta.mojang.com",
        "launcher.mojang.com",
        "resources.download.minecraft.net",
        "libraries.minecraft.net"
    )

    fun download(
        descriptor: MinecraftDownload,
        relativePath: String,
        storageRoot: File,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null,
        shouldCancel: (() -> Boolean)? = null
    ): File {
        require(descriptor.size >= 0L) { "Download size must not be negative" }
        require(Regex("^[a-fA-F0-9]{40}$").matches(descriptor.sha1)) {
            "Download SHA-1 is invalid"
        }
        val target = resolveTarget(storageRoot, relativePath)
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, target.name + ".part")
        if (temporary.exists()) temporary.delete()

        try {
            val digest = MessageDigest.getInstance("SHA-1")
            var downloaded = 0L
            val connection = openResponse(descriptor.url)
            try {
                val response = connection.responseCode
                require(response == HttpURLConnection.HTTP_OK) {
                    "Download server returned HTTP $response"
                }
                val responseLength = connection.contentLengthLong
                require(responseLength < 0L || responseLength == descriptor.size) {
                    "Download size did not match version metadata"
                }

                connection.inputStream.use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            if (shouldCancel?.invoke() == true) throw CancellationException("Minecraft download cancelled")
                            val count = input.read(buffer)
                            if (count < 0) break
                            downloaded += count
                            require(downloaded <= descriptor.size) {
                                "Download exceeded the expected file size"
                            }
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                            onProgress?.invoke(downloaded, descriptor.size)
                        }
                        if (shouldCancel?.invoke() == true) throw CancellationException("Minecraft download cancelled")
                        output.fd.sync()
                    }
                }
            } finally {
                connection.disconnect()
            }

            require(downloaded == descriptor.size) {
                "Download ended before the expected file size"
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            require(actualHash.equals(descriptor.sha1, ignoreCase = true)) {
                "Downloaded file failed SHA-1 verification"
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

    internal fun resolveTarget(storageRoot: File, relativePath: String): File {
        require(relativePath.isNotBlank()) { "Download path is empty" }
        val segments = relativePath.replace('\\', '/').split('/')
        require(segments.none { it.isBlank() || it == "." || it == ".." }) {
            "Download path contains an unsafe segment"
        }
        require(!relativePath.startsWith("/") && !Regex("^[A-Za-z]:").containsMatchIn(relativePath)) {
            "Download path must be relative"
        }
        val root = storageRoot.canonicalFile
        val target = File(root, segments.joinToString(File.separator)).canonicalFile
        require(target.path.startsWith(root.path + File.separator)) {
            "Download path escapes launcher storage"
        }
        return target
    }

    private fun openResponse(initialUrl: String): HttpURLConnection {
        var current = initialUrl
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            require(isTrustedUrl(current)) { "Download URL is not trusted" }
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
                    "Download redirect limit exceeded"
                }
                current = URL(URL(current), location).toString()
            } else {
                return connection
            }
        }
        throw IllegalStateException("Download redirect limit exceeded")
    }

    internal fun isTrustedUrl(value: String): Boolean {
        return try {
            val url = URL(value)
            url.protocol == "https" && url.host.lowercase() in allowedHosts &&
                url.userInfo == null && url.port in listOf(-1, 443)
        } catch (_: Exception) {
            false
        }
    }
}
