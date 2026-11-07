package com.flame.launcher.runtime

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

/**
 * Downloads a Java runtime archive to app-private storage and verifies its SHA-256.
 *
 * The caller must obtain the URL, digest, and host allowlist from a trusted
 * runtime catalog. Redirects are disabled so an allowlisted URL cannot silently
 * transfer trust to another host. Call from a worker thread.
 */
class JavaRuntimePackageDownloader(
    private val trustedHosts: Set<String>,
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
    private val connectTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val maxArchiveBytes: Long = DEFAULT_MAX_ARCHIVE_BYTES
) {
    private val normalizedHosts = trustedHosts.map { it.lowercase() }.toSet()

    init {
        require(normalizedHosts.isNotEmpty() && normalizedHosts.all { HOST_PATTERN.matches(it) }) {
            "At least one valid trusted runtime host is required."
        }
        require(connectTimeoutMs in 1..MAX_TIMEOUT_MS)
        require(readTimeoutMs in 1..MAX_TIMEOUT_MS)
        require(maxArchiveBytes in 1..ABSOLUTE_MAX_ARCHIVE_BYTES)
    }

    fun download(
        address: String,
        expectedSha256: String,
        destination: File,
        expectedSizeBytes: Long? = null,
        onProgress: (receivedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> }
    ): File {
        require(SHA256_PATTERN.matches(expectedSha256)) {
            "Runtime package SHA-256 is invalid."
        }
        require(expectedSizeBytes == null || expectedSizeBytes in 1..maxArchiveBytes) {
            "Expected runtime package size is invalid or exceeds the configured download limit."
        }
        val uri = try {
            URI(address)
        } catch (failure: Exception) {
            throw IllegalArgumentException("Runtime package URL is invalid.", failure)
        }
        val host = uri.host?.lowercase()
        require(
            uri.scheme == "https" &&
                host != null &&
                host in normalizedHosts &&
                uri.userInfo == null &&
                uri.port in setOf(-1, 443)
        ) { "Runtime package URL is not trusted." }

        val target = destination.canonicalFile
        val parent = target.parentFile
            ?: throw IllegalArgumentException("Runtime package destination has no parent.")
        if (!parent.exists()) {
            require(parent.mkdirs()) { "Unable to create the runtime download directory." }
        }
        require(parent.isDirectory && target.parentFile.canonicalFile == parent) {
            "Runtime package destination is invalid."
        }

        val temporary = File(parent, ".${target.name}.download-${UUID.randomUUID()}")
        val connection = connectionFactory(URL(address))
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/zip, application/octet-stream")
            val status = connection.responseCode
            require(status == HttpURLConnection.HTTP_OK) {
                "Runtime package server returned HTTP $status."
            }
            val declaredLength = connection.contentLengthLong
            require(declaredLength == -1L || declaredLength in 1..maxArchiveBytes) {
                "Runtime package exceeds the configured download size limit."
            }
            require(expectedSizeBytes == null || declaredLength == -1L || declaredLength == expectedSizeBytes) {
                "Runtime package declared size does not match the trusted catalog."
            }
            val progressTotal = expectedSizeBytes ?: declaredLength.takeIf { it > 0L }

            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            var lastReportedProgress = 0L
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        require(count > 0) { "Runtime package stream returned an invalid read length." }
                        require(total <= maxArchiveBytes - count) {
                            "Runtime package exceeds the configured download size limit."
                        }
                        require(expectedSizeBytes == null || total <= expectedSizeBytes - count) {
                            "Runtime package exceeds the trusted catalog size."
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        total += count
                        if (total - lastReportedProgress >= PROGRESS_INTERVAL_BYTES ||
                            (declaredLength > 0L && total == declaredLength)
                        ) {
                            onProgress(total, progressTotal)
                            lastReportedProgress = total
                        }
                    }
                }
            }
            require(total > 0L) { "Runtime package download is empty." }
            require(expectedSizeBytes == null || total == expectedSizeBytes) {
                "Downloaded runtime size does not match the trusted catalog."
            }
            if (lastReportedProgress != total) {
                onProgress(total, progressTotal)
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            require(actual.equals(expectedSha256, ignoreCase = true)) {
                "Runtime package SHA-256 verification failed."
            }

            val backup = if (target.exists()) {
                File(parent, ".${target.name}.backup-${UUID.randomUUID()}").also {
                    check(target.renameTo(it)) {
                        "Unable to stage the previous runtime package."
                    }
                }
            } else null
            try {
                check(temporary.renameTo(target)) {
                    "Unable to promote the verified runtime package."
                }
                backup?.delete()
            } catch (failure: Exception) {
                if (backup != null && backup.exists() && !backup.renameTo(target)) {
                    failure.addSuppressed(
                        IllegalStateException("The previous runtime package could not be restored.")
                    )
                }
                throw failure
            }
            return target
        } finally {
            connection.disconnect()
            temporary.delete()
        }
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 20_000
        private const val MAX_TIMEOUT_MS = 120_000
        const val DEFAULT_MAX_ARCHIVE_BYTES = 512L * 1024 * 1024
        private const val ABSOLUTE_MAX_ARCHIVE_BYTES = 1024L * 1024 * 1024
        private const val BUFFER_SIZE = 16 * 1024
        private const val PROGRESS_INTERVAL_BYTES = 256 * 1024
        private val SHA256_PATTERN = Regex("[a-fA-F0-9]{64}")
        private val HOST_PATTERN = Regex("[a-z0-9.-]{1,253}")
    }
}
