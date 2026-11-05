package com.flame.launcher.content

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CancellationException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

sealed interface MinecraftStagedDownloadResult {
    data class Downloaded(
        val install: MinecraftPlannedInstall,
        val stagedFile: File,
        val bytesWritten: Long
    ) : MinecraftStagedDownloadResult

    data class Failed(val reason: String) : MinecraftStagedDownloadResult
}

fun interface MinecraftDownloadProgressListener {
    fun onProgress(bytesDownloaded: Long, expectedBytes: Long?)
}

/**
 * Downloads one already-planned item into app-private staging.
 *
 * The caller must pass an app-private directory (for example Context.cacheDir or
 * filesDir). This class never writes to the Minecraft instance destination.
 */
class MinecraftContentStagedDownloader(
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) {
    companion object {
        private const val MAX_FILE_SIZE_BYTES = 1L shl 30
        private const val BUFFER_SIZE = 32 * 1024
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000

        private val trustedHosts = mapOf(
            ContentProviderId.MODRINTH to setOf("cdn.modrinth.com"),
            ContentProviderId.CURSEFORGE to setOf("mediafilez.forgecdn.net", "edge.forgecdn.net")
        )
    }

    fun download(
        install: MinecraftPlannedInstall,
        stagingDirectory: File,
        progress: MinecraftDownloadProgressListener = MinecraftDownloadProgressListener { _, _ -> }
    ): MinecraftStagedDownloadResult {
        val root = try {
            if (!stagingDirectory.exists() && !stagingDirectory.mkdirs()) {
                return MinecraftStagedDownloadResult.Failed("Could not create staging directory")
            }
            stagingDirectory.canonicalFile.also {
                if (!it.isDirectory || !it.canWrite()) {
                    return MinecraftStagedDownloadResult.Failed("Staging location is not a writable directory")
                }
            }
        } catch (_: Exception) {
            return MinecraftStagedDownloadResult.Failed("Invalid staging directory")
        }

        val uri = try {
            URL(install.downloadUrl)
        } catch (_: Exception) {
            return MinecraftStagedDownloadResult.Failed("Invalid download URL")
        }
        if (!uri.protocol.equals("https", ignoreCase = true) ||
            uri.toURI().userInfo != null ||
            uri.ref != null ||
            (uri.port != -1 && uri.port != 443) ||
            uri.host.lowercase(Locale.ROOT) !in trustedHosts.getValue(install.provider)
        ) {
            return MinecraftStagedDownloadResult.Failed("Download URL is not trusted for this provider")
        }
        if (!isSafeLeaf(install.fileName)) {
            return MinecraftStagedDownloadResult.Failed("Unsafe download file name")
        }
        if (install.expectedSizeBytes != null &&
            (install.expectedSizeBytes <= 0 || install.expectedSizeBytes > MAX_FILE_SIZE_BYTES)
        ) {
            return MinecraftStagedDownloadResult.Failed("Expected file size is outside the supported range")
        }
        if (install.sha1 != null && !install.sha1.matches(Regex("^[a-fA-F0-9]{40}$"))) {
            return MinecraftStagedDownloadResult.Failed("Invalid SHA-1 checksum")
        }
        if (install.sha512 != null && !install.sha512.matches(Regex("^[a-fA-F0-9]{128}$"))) {
            return MinecraftStagedDownloadResult.Failed("Invalid SHA-512 checksum")
        }

        val suffix = "${install.projectId.hashCode().toUInt().toString(16)}-${install.versionId.hashCode().toUInt().toString(16)}"
        val finalFile = File(root, ".flame-$suffix-${install.fileName}")
        if (finalFile.exists()) {
            return MinecraftStagedDownloadResult.Failed("This version is already present in staging")
        }

        val temporaryFile = try {
            File.createTempFile(".flame-download-", ".part", root)
        } catch (_: Exception) {
            return MinecraftStagedDownloadResult.Failed("Could not create temporary download file")
        }

        var connection: HttpURLConnection? = null
        try {
            if (Thread.currentThread().isInterrupted) throw CancellationException("Download cancelled")
            connection = connectionFactory(uri).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", "FlameLauncher/0.1.0 (content-installer)")
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException(
                    if (status in 300..399) "Download redirect rejected" else "Download server returned HTTP $status"
                )
            }

            val responseLength = connection.contentLengthLong
            if (responseLength > MAX_FILE_SIZE_BYTES) {
                throw IllegalStateException("Download exceeds the 1 GiB safety limit")
            }
            if (install.expectedSizeBytes != null && responseLength >= 0 &&
                responseLength != install.expectedSizeBytes
            ) {
                throw IllegalStateException("Server file size does not match the selected version")
            }

            val sha1Digest = install.sha1?.let { MessageDigest.getInstance("SHA-1") }
            val sha512Digest = install.sha512?.let { MessageDigest.getInstance("SHA-512") }
            var written = 0L
            connection.inputStream.use { input ->
                FileOutputStream(temporaryFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) {
                            throw CancellationException("Download cancelled")
                        }
                        val count = input.read(buffer)
                        if (count < 0) break
                        written += count
                        if (written > MAX_FILE_SIZE_BYTES ||
                            (install.expectedSizeBytes != null && written > install.expectedSizeBytes)
                        ) {
                            throw IllegalStateException("Download exceeded its expected size")
                        }
                        output.write(buffer, 0, count)
                        sha1Digest?.update(buffer, 0, count)
                        sha512Digest?.update(buffer, 0, count)
                        progress.onProgress(written, install.expectedSizeBytes ?: responseLength.takeIf { it >= 0 })
                    }
                    output.fd.sync()
                }
            }

            if (written == 0L) throw IllegalStateException("Downloaded file is empty")
            if (install.expectedSizeBytes != null && written != install.expectedSizeBytes) {
                throw IllegalStateException("Downloaded file size does not match the selected version")
            }
            if (install.sha1 != null && sha1Digest!!.digest().toHex() != install.sha1.lowercase(Locale.ROOT)) {
                throw IllegalStateException("SHA-1 verification failed")
            }
            if (install.sha512 != null && sha512Digest!!.digest().toHex() != install.sha512.lowercase(Locale.ROOT)) {
                throw IllegalStateException("SHA-512 verification failed")
            }

            try {
                Files.move(temporaryFile.toPath(), finalFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporaryFile.toPath(), finalFile.toPath())
            }
            return MinecraftStagedDownloadResult.Downloaded(install, finalFile, written)
        } catch (cancelled: CancellationException) {
            return MinecraftStagedDownloadResult.Failed(cancelled.message ?: "Download cancelled")
        } catch (error: Exception) {
            return MinecraftStagedDownloadResult.Failed(
                error.message?.take(240) ?: "Download failed"
            )
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {
                // Best-effort connection cleanup.
            }
            if (temporaryFile.exists()) temporaryFile.delete()
        }
    }

    private fun isSafeLeaf(value: String): Boolean =
        value.isNotBlank() && value == value.trim() && value != "." && value != ".." &&
            value.none { it == '/' || it == '\\' || it == ':' || it.code < 32 || it.code == 127 }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
