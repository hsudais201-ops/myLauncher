package com.flame.launcher.runtime

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

/**
 * Downloads hash-addressed Mojang assets into an instance's game directory.
 *
 * Only ASSET plan entries are accepted. The URL and destination are recomputed
 * from the validated SHA-1, preventing a caller from using this downloader as
 * an arbitrary-file or arbitrary-host fetcher. Blocking I/O: call off the UI
 * thread. Thread interruption cancels the stream and removes its temporary file.
 */
class MinecraftAssetDownloader(
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
    private val connectTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val maxAssetBytes: Long = DEFAULT_MAX_ASSET_BYTES
) {
    init {
        require(connectTimeoutMs in 1..MAX_TIMEOUT_MS)
        require(readTimeoutMs in 1..MAX_TIMEOUT_MS)
        require(maxAssetBytes in 1..ABSOLUTE_MAX_ASSET_BYTES)
    }

    fun download(
        planned: MinecraftPlannedDownload,
        gameDirectory: File,
        onProgress: (receivedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> }
    ): File {
        require(planned.kind == MinecraftPlannedDownloadKind.ASSET) {
            "Minecraft asset downloader only accepts asset objects."
        }
        val hash = planned.descriptor.sha1.lowercase()
        require(SHA1_PATTERN.matches(hash)) { "Minecraft asset SHA-1 is invalid." }
        val expectedSize = planned.descriptor.size
        require(expectedSize in 0..maxAssetBytes) {
            "Minecraft asset size is invalid or exceeds the configured download limit."
        }
        val relativePath = "assets/objects/${hash.take(2)}/$hash"
        val expectedUrl = "https://resources.download.minecraft.net/${hash.take(2)}/$hash"
        require(planned.relativePath == relativePath &&
            planned.descriptor.path == relativePath &&
            planned.descriptor.url == expectedUrl
        ) { "Minecraft asset descriptor does not match its content hash." }

        val uri = URI(planned.descriptor.url)
        require(uri.scheme == "https" &&
            uri.host == TRUSTED_ASSET_HOST &&
            uri.userInfo == null &&
            uri.port in setOf(-1, 443)
        ) { "Minecraft asset URL is not trusted." }

        if (!gameDirectory.exists()) {
            require(gameDirectory.mkdirs()) { "Unable to create the Minecraft game directory." }
        }
        require(gameDirectory.isDirectory) { "Minecraft game directory is invalid." }
        val root = gameDirectory.canonicalFile
        val requestedTarget = File(root, relativePath).absoluteFile
        val parent = requestedTarget.parentFile
            ?: throw IllegalArgumentException("Minecraft asset destination has no parent.")
        if (!parent.exists()) {
            require(parent.mkdirs()) { "Unable to create the Minecraft asset directory." }
        }
        val canonicalParent = parent.canonicalFile
        require(
            parent.isDirectory &&
                canonicalParent == parent.absoluteFile &&
                canonicalParent.path.startsWith(root.path + File.separator)
        ) { "Minecraft asset directory is invalid or traverses a symbolic link." }
        val target = File(canonicalParent, requestedTarget.name)
        require(target.canonicalFile == target.absoluteFile) {
            "Minecraft asset destination traverses a symbolic link."
        }

        val temporary = File(parent, ".${target.name}.download-${UUID.randomUUID()}")
        val connection = connectionFactory(URL(planned.descriptor.url))
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/octet-stream")
            val status = connection.responseCode
            require(status == HttpURLConnection.HTTP_OK) {
                "Minecraft asset server returned HTTP $status."
            }
            val declaredLength = connection.contentLengthLong
            require(declaredLength == -1L || declaredLength == expectedSize) {
                "Minecraft asset declared size does not match its index."
            }
            val digest = MessageDigest.getInstance("SHA-1")
            var total = 0L
            var lastReported = 0L
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) {
                            throw InterruptedException("Minecraft asset download was cancelled.")
                        }
                        val count = input.read(buffer)
                        if (count == -1) break
                        require(count > 0) { "Minecraft asset stream returned an invalid read length." }
                        require(total <= expectedSize - count) {
                            "Minecraft asset exceeds the size declared in its index."
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        total += count
                        if (total - lastReported >= PROGRESS_INTERVAL_BYTES) {
                            onProgress(total, expectedSize)
                            lastReported = total
                        }
                    }
                }
            }
            require(total == expectedSize) {
                "Downloaded Minecraft asset size does not match its index."
            }
            if (lastReported != total) onProgress(total, expectedSize)
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            require(actualHash == hash) { "Minecraft asset SHA-1 verification failed." }

            val backup = if (target.exists()) {
                File(parent, ".${target.name}.backup-${UUID.randomUUID()}").also {
                    check(target.renameTo(it)) { "Unable to stage the previous Minecraft asset." }
                }
            } else null
            try {
                check(temporary.renameTo(target)) { "Unable to promote the verified Minecraft asset." }
                backup?.delete()
            } catch (failure: Exception) {
                if (backup != null && backup.exists() && !backup.renameTo(target)) {
                    failure.addSuppressed(IllegalStateException("The previous Minecraft asset could not be restored."))
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
        private const val TRUSTED_ASSET_HOST = "resources.download.minecraft.net"
        private const val DEFAULT_TIMEOUT_MS = 20_000
        private const val MAX_TIMEOUT_MS = 120_000
        const val DEFAULT_MAX_ASSET_BYTES = 512L * 1024 * 1024
        private const val ABSOLUTE_MAX_ASSET_BYTES = 1024L * 1024 * 1024
        private const val BUFFER_SIZE = 16 * 1024
        private const val PROGRESS_INTERVAL_BYTES = 256 * 1024
        private val SHA1_PATTERN = Regex("[a-f0-9]{40}")
    }
}
