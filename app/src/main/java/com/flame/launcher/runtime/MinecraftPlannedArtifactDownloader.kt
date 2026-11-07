package com.flame.launcher.runtime

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

/**
 * Downloads client JARs, asset indexes, and Maven library artifacts from
 * validated Mojang metadata plans. Files are size/hash checked before atomic
 * promotion into the instance game directory.
 *
 * Blocking I/O: call from a worker thread. Native JARs are downloaded here but
 * deliberately not extracted; native extraction is a separate guarded stage.
 */
class MinecraftPlannedArtifactDownloader(
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
    private val connectTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val maxArtifactBytes: Long = DEFAULT_MAX_ARTIFACT_BYTES
) {
    init {
        require(connectTimeoutMs in 1..MAX_TIMEOUT_MS)
        require(readTimeoutMs in 1..MAX_TIMEOUT_MS)
        require(maxArtifactBytes in 1..ABSOLUTE_MAX_ARTIFACT_BYTES)
    }

    fun download(
        planned: MinecraftPlannedDownload,
        gameDirectory: File,
        onProgress: (receivedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> }
    ): File {
        require(planned.kind in SUPPORTED_KINDS) {
            "This downloader only accepts client, asset-index, library, or native artifacts."
        }
        val descriptor = planned.descriptor
        val hash = descriptor.sha1.lowercase()
        require(SHA1.matches(hash)) { "Minecraft artifact SHA-1 is invalid." }
        val expectedSize = descriptor.size
        require(expectedSize in 1..maxArtifactBytes) {
            "Minecraft artifact size is invalid or exceeds the configured limit."
        }
        validateRelativePath(planned.relativePath)
        when (planned.kind) {
            MinecraftPlannedDownloadKind.CLIENT -> {
                require(planned.relativePath.matches(Regex("versions/[A-Za-z0-9._-]{1,64}/[A-Za-z0-9._-]{1,64}\\.jar"))) {
                    "Minecraft client installation path is invalid."
                }
            }
            MinecraftPlannedDownloadKind.ASSET_INDEX -> {
                require(planned.relativePath.matches(Regex("assets/indexes/[A-Za-z0-9._-]{1,64}\\.json"))) {
                    "Minecraft asset-index installation path is invalid."
                }
            }
            MinecraftPlannedDownloadKind.LIBRARY,
            MinecraftPlannedDownloadKind.NATIVE -> {
                require(descriptor.path != null && planned.relativePath == "libraries/${descriptor.path}") {
                    "Minecraft library installation path does not match its metadata."
                }
            }
            else -> error("Unreachable artifact kind.")
        }
        val uri = URI(descriptor.url)
        require(
            uri.scheme == "https" &&
                uri.host?.lowercase() in TRUSTED_HOSTS &&
                uri.userInfo == null &&
                uri.port in setOf(-1, 443) &&
                uri.rawQuery == null &&
                uri.rawFragment == null &&
                uri.rawPath?.contains("/../") != true
        ) { "Minecraft artifact URL is not trusted." }

        if (!gameDirectory.exists()) {
            require(gameDirectory.mkdirs()) { "Unable to create the Minecraft game directory." }
        }
        require(gameDirectory.isDirectory) { "Minecraft game directory is invalid." }
        val root = gameDirectory.canonicalFile
        val target = resolveSafeTarget(root, planned.relativePath)
        val parent = target.parentFile
            ?: throw IllegalArgumentException("Minecraft artifact destination has no parent.")
        val temporary = File(parent, ".${target.name}.download-${UUID.randomUUID()}")
        val connection = connectionFactory(URL(descriptor.url))
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/octet-stream")
            val status = connection.responseCode
            require(status == HttpURLConnection.HTTP_OK) {
                "Minecraft artifact server returned HTTP $status."
            }
            val declaredLength = connection.contentLengthLong
            require(declaredLength == -1L || declaredLength == expectedSize) {
                "Minecraft artifact declared size does not match its metadata."
            }

            val digest = MessageDigest.getInstance("SHA-1")
            var total = 0L
            var lastReported = 0L
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) {
                            throw InterruptedException("Minecraft artifact download was cancelled.")
                        }
                        val count = input.read(buffer)
                        if (count == -1) break
                        require(count > 0) { "Minecraft artifact stream returned an invalid read length." }
                        require(total <= expectedSize - count) {
                            "Minecraft artifact exceeds the size declared in its metadata."
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
                "Downloaded Minecraft artifact size does not match its metadata."
            }
            if (lastReported != total) onProgress(total, expectedSize)
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            require(actualHash == hash) { "Minecraft artifact SHA-1 verification failed." }

            val backup = if (target.exists()) {
                File(parent, ".${target.name}.backup-${UUID.randomUUID()}").also {
                    check(target.renameTo(it)) { "Unable to stage the previous Minecraft artifact." }
                }
            } else null
            try {
                check(temporary.renameTo(target)) { "Unable to promote the verified Minecraft artifact." }
                backup?.delete()
            } catch (failure: Exception) {
                if (backup != null && backup.exists() && !backup.renameTo(target)) {
                    failure.addSuppressed(
                        IllegalStateException("The previous Minecraft artifact could not be restored.")
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

    private fun resolveSafeTarget(root: File, relativePath: String): File {
        var cursor = root
        val segments = relativePath.split('/')
        for (segment in segments.dropLast(1)) {
            cursor = File(cursor, segment)
            if (!cursor.exists()) {
                require(cursor.mkdir()) { "Unable to create Minecraft artifact directory." }
            }
            require(!java.nio.file.Files.isSymbolicLink(cursor.toPath()) && cursor.isDirectory) {
                "Minecraft artifact path traverses a symbolic link or non-directory."
            }
            require(cursor.canonicalFile == cursor.absoluteFile && cursor.canonicalFile.toPath().startsWith(root.toPath())) {
                "Minecraft artifact directory escapes the game directory."
            }
        }
        val target = File(cursor, segments.last())
        require(!java.nio.file.Files.isSymbolicLink(target.toPath())) {
            "Minecraft artifact destination cannot be a symbolic link."
        }
        require(!target.exists() || target.isFile) {
            "Minecraft artifact destination is not a regular file."
        }
        require(target.canonicalFile == target.absoluteFile) {
            "Minecraft artifact destination resolves outside the game directory."
        }
        return target
    }

    private fun validateRelativePath(path: String) {
        require(path.isNotBlank() && path.length <= MAX_PATH_LENGTH &&
            !path.startsWith('/') && '\\' !in path && ':' !in path &&
            path.none { it.code < 0x20 } &&
            path.split('/').all { it.isNotBlank() && it != "." && it != ".." }
        ) { "Minecraft artifact installation path is unsafe." }
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 20_000
        private const val MAX_TIMEOUT_MS = 120_000
        private const val DEFAULT_MAX_ARTIFACT_BYTES = 1024L * 1024 * 1024
        private const val ABSOLUTE_MAX_ARTIFACT_BYTES = 2L * 1024 * 1024 * 1024
        private const val BUFFER_SIZE = 32 * 1024
        private const val PROGRESS_INTERVAL_BYTES = 256 * 1024
        private const val MAX_PATH_LENGTH = 2048
        private val SHA1 = Regex("[a-f0-9]{40}")
        private val SUPPORTED_KINDS = setOf(
            MinecraftPlannedDownloadKind.CLIENT,
            MinecraftPlannedDownloadKind.ASSET_INDEX,
            MinecraftPlannedDownloadKind.LIBRARY,
            MinecraftPlannedDownloadKind.NATIVE
        )
        private val TRUSTED_HOSTS = setOf(
            "piston-data.mojang.com",
            "piston-meta.mojang.com",
            "launcher.mojang.com",
            "libraries.minecraft.net",
            "repo1.maven.org",
            "repo.maven.apache.org",
            "maven.fabricmc.net",
            "maven.minecraftforge.net",
            "maven.neoforged.net",
            "maven.quiltmc.org"
        )
    }
}
