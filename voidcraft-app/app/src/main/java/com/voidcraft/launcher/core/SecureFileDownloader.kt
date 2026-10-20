package com.voidcraft.launcher.core

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

data class FileDownloadRequest(
    val url: String,
    val destination: File,
    val expectedSha1: String? = null,
    val expectedSha256: String? = null,
    val expectedBytes: Long? = null,
    val maxBytes: Long = DEFAULT_MAX_BYTES,
    val maxRetries: Int = 2,
    val connectTimeoutMillis: Int = 15_000,
    val readTimeoutMillis: Int = 30_000
) {
    init {
        require(url.isNotBlank()) { "Download URL is required" }
        require(expectedSha1 == null || SHA1_PATTERN.matches(expectedSha1)) {
            "Expected SHA-1 must contain 40 hexadecimal characters"
        }
        require(expectedSha256 == null || SHA256_PATTERN.matches(expectedSha256)) {
            "Expected SHA-256 must contain 64 hexadecimal characters"
        }
        require(expectedBytes == null || expectedBytes >= 0L) { "Expected size must not be negative" }
        require(maxBytes in 1..ABSOLUTE_MAX_BYTES) { "Download size limit is outside the supported range" }
        require(maxRetries in 0..5) { "Retry count must be between 0 and 5" }
        require(connectTimeoutMillis > 0 && readTimeoutMillis > 0) { "Timeouts must be positive" }
    }

    companion object {
        const val DEFAULT_MAX_BYTES = 2_000_000_000L
        private const val ABSOLUTE_MAX_BYTES = 8_000_000_000L
        private val SHA1_PATTERN = Regex("[0-9a-fA-F]{40}")
        private val SHA256_PATTERN = Regex("[0-9a-fA-F]{64}")
    }
}

data class FileDownloadProgress(val bytesDownloaded: Long, val totalBytes: Long?)

data class FileDownloadReceipt(
    val file: File,
    val bytesDownloaded: Long,
    val sha1: String,
    val sha256: String,
    val attempts: Int
)

class DownloadCancelledException : IOException("Download was cancelled")
class DownloadIntegrityException(message: String) : IOException(message)

private class HttpStatusException(val statusCode: Int) :
    IOException("Download request failed with HTTP " + statusCode)

private data class OpenResponse(val connection: HttpURLConnection, val status: Int)

fun interface DownloadConnectionFactory {
    fun open(url: URL): HttpURLConnection
}

/**
 * Independently authored HTTPS downloader for public metadata/artifact URLs.
 *
 * Each attempt writes to a unique temporary file in the destination directory.
 * Size and supplied hashes are checked before the destination is replaced.
 * Cancellation and failures preserve any pre-existing destination file.
 *
 * This implementation retries transient failures from the beginning; it does
 * not yet resume a partial download across attempts or app restarts.
 */
class SecureFileDownloader(
    private val connectionFactory: DownloadConnectionFactory =
        DownloadConnectionFactory { it.openConnection() as HttpURLConnection }
) {
    fun download(
        request: FileDownloadRequest,
        isCancelled: () -> Boolean = { false },
        onProgress: (FileDownloadProgress) -> Unit = {}
    ): FileDownloadReceipt {
        val initialUrl = validateHttpsUrl(request.url)
        val destination = request.destination.absoluteFile
        require(!destination.isDirectory) { "Download destination is a directory" }

        val parent = destination.parentFile ?: throw IOException("Download destination has no parent directory")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create download directory")
        if (!parent.isDirectory) throw IOException("Download parent is not a directory")

        val temporary = try {
            Files.createTempFile(parent.toPath(), ".voidcraft-", ".part").toFile()
        } catch (error: Exception) {
            throw IOException("Could not create temporary download file", error)
        }

        var promoted = false
        var attempt = 0
        var lastError: IOException? = null
        try {
            while (attempt <= request.maxRetries) {
                checkCancelled(isCancelled)
                attempt += 1
                try {
                    val receipt = downloadAttempt(
                        request = request,
                        initialUrl = initialUrl,
                        temporary = temporary,
                        attempt = attempt,
                        isCancelled = isCancelled,
                        onProgress = onProgress
                    )
                    promote(temporary, destination)
                    promoted = true
                    return receipt.copy(file = destination)
                } catch (error: DownloadCancelledException) {
                    throw error
                } catch (error: DownloadIntegrityException) {
                    // Deterministic validation failures should not overwrite or be retried indefinitely.
                    throw error
                } catch (error: HttpStatusException) {
                    if (!isTransientStatus(error.statusCode) || attempt > request.maxRetries) throw error
                    lastError = error
                } catch (error: IOException) {
                    if (attempt > request.maxRetries) throw error
                    lastError = error
                }
                temporary.outputStream().use { /* truncate any partial bytes before retry */ }
            }
            throw IOException("Download failed after retries", lastError)
        } finally {
            if (!promoted) temporary.delete()
        }
    }

    private fun downloadAttempt(
        request: FileDownloadRequest,
        initialUrl: URL,
        temporary: File,
        attempt: Int,
        isCancelled: () -> Boolean,
        onProgress: (FileDownloadProgress) -> Unit
    ): FileDownloadReceipt {
        val response = openResponse(initialUrl, request.connectTimeoutMillis, request.readTimeoutMillis)
        val connection = response.connection
        try {
            if (response.status !in 200..299) throw HttpStatusException(response.status)
            val declaredLength = connection.contentLengthLong.takeIf { it >= 0L }
            if (declaredLength != null && declaredLength > request.maxBytes) {
                throw DownloadIntegrityException("Server response exceeds the configured size limit")
            }

            val sha1 = MessageDigest.getInstance("SHA-1")
            val sha256 = MessageDigest.getInstance("SHA-256")
            var total = 0L
            FileOutputStream(temporary, false).use { fileOutput ->
                val output = BufferedOutputStream(fileOutput)
                try {
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            checkCancelled(isCancelled)
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > request.maxBytes) {
                                throw DownloadIntegrityException("Download exceeded the configured size limit")
                            }
                            sha1.update(buffer, 0, count)
                            sha256.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                            onProgress(FileDownloadProgress(total, declaredLength ?: request.expectedBytes))
                        }
                    }
                    checkCancelled(isCancelled)
                    output.flush()
                    fileOutput.fd.sync()
                } finally {
                    output.close()
                }
            }

            if (request.expectedBytes != null && total != request.expectedBytes) {
                throw DownloadIntegrityException(
                    "Downloaded size " + total + " bytes does not match expected " + request.expectedBytes
                )
            }
            val sha1Hex = sha1.digest().toHex()
            val sha256Hex = sha256.digest().toHex()
            if (request.expectedSha1 != null && !sha1Hex.equals(request.expectedSha1, ignoreCase = true)) {
                throw DownloadIntegrityException("Downloaded file failed SHA-1 verification")
            }
            if (request.expectedSha256 != null && !sha256Hex.equals(request.expectedSha256, ignoreCase = true)) {
                throw DownloadIntegrityException("Downloaded file failed SHA-256 verification")
            }

            if (declaredLength != null && total != declaredLength) {
                throw DownloadIntegrityException(
                    "Downloaded size " + total + " bytes does not match server Content-Length " + declaredLength
                )
            }
            return FileDownloadReceipt(temporary, total, sha1Hex, sha256Hex, attempt)
        } finally {
            connection.disconnect()
        }
    }

    private fun openResponse(url: URL, connectTimeoutMillis: Int, readTimeoutMillis: Int): OpenResponse {
        var current = validateHttpsUrl(url.toString())
        var redirects = 0
        while (true) {
            val connection = connectionFactory.open(current)
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = connectTimeoutMillis
                connection.readTimeout = readTimeoutMillis
                val status = connection.responseCode
                if (status in 300..399) {
                    if (redirects >= MAX_REDIRECTS) throw IOException("Too many download redirects")
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("Download redirect is missing Location")
                    val target = try {
                        URL(current, location).toString()
                    } catch (error: Exception) {
                        throw IOException("Invalid download redirect URL", error)
                    }
                    connection.disconnect()
                    current = try {
                        validateHttpsUrl(target)
                    } catch (error: IllegalArgumentException) {
                        throw IOException("Redirect target violates the HTTPS URL policy", error)
                    }
                    redirects += 1
                    continue
                }
                return OpenResponse(connection, status)
            } catch (error: Exception) {
                connection.disconnect()
                if (error is IOException) throw error
                throw IOException("Could not open download connection", error)
            }
        }
    }

    private fun validateHttpsUrl(urlText: String): URL {
        val url = try {
            URL(urlText)
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid download URL", error)
        }
        require(url.protocol.equals("https", ignoreCase = true)) { "Downloads must use HTTPS" }
        require(url.host.isNotBlank()) { "Download URL has no host" }
        require(url.userInfo == null && url.ref == null) {
            "Download URL must not contain credentials or a fragment"
        }
        require(url.port == -1 || url.port == 443) { "Non-standard HTTPS ports are not allowed" }
        return url
    }

    private fun checkCancelled(isCancelled: () -> Boolean) {
        if (isCancelled()) throw DownloadCancelledException()
    }

    private fun isTransientStatus(status: Int): Boolean =
        status == 408 || status == 429 || status in 500..599

    private fun promote(temporary: File, destination: File) {
        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (error: IOException) {
            throw IOException("Verified download could not replace the destination", error)
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }

    companion object {
        private const val BUFFER_SIZE = 32 * 1024
        private const val MAX_REDIRECTS = 5
    }
}
