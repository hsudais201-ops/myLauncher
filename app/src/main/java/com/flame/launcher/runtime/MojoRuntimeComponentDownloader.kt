package com.flame.launcher.runtime

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.util.UUID
import java.util.concurrent.CancellationException

/**
 * Downloads one Mojo runtime .tar.xz component and authenticates the exact bytes
 * with the provider's pinned RSA/SHA-256 signature before exposing the file.
 *
 * This deliberately does not extract archives. Universal and ABI-specific
 * components must both be authenticated before the separate safe TAR installer
 * is allowed to consume them.
 */
class MojoRuntimeComponentDownloader(
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
    private val connectTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val maxArchiveBytes: Long = DEFAULT_MAX_ARCHIVE_BYTES
) {
    init {
        require(connectTimeoutMs in 1..MAX_TIMEOUT_MS)
        require(readTimeoutMs in 1..MAX_TIMEOUT_MS)
        require(maxArchiveBytes in 1..ABSOLUTE_MAX_ARCHIVE_BYTES)
    }

    fun download(
        address: String,
        expectedSignature: ByteArray,
        publicKey: PublicKey,
        destination: File,
        onProgress: (receivedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): VerifiedMojoRuntimeComponent {
        require(expectedSignature.size == RSA_4096_SIGNATURE_BYTES) {
            "Runtime component signature must be RSA-4096."
        }
        val uri = try {
            URI(address)
        } catch (failure: Exception) {
            throw IllegalArgumentException("Runtime component URL is invalid.", failure)
        }
        require(
            uri.scheme == "https" &&
                uri.host?.lowercase() == TRUSTED_HOST &&
                uri.userInfo == null &&
                uri.port in setOf(-1, 443) &&
                uri.query == null &&
                uri.fragment == null &&
                COMPONENT_PATH.matches(uri.path.orEmpty())
        ) { "Runtime component URL is not trusted." }

        val target = destination.canonicalFile
        val parent = target.parentFile
            ?: throw IllegalArgumentException("Runtime component destination has no parent.")
        if (!parent.exists()) {
            require(parent.mkdirs()) { "Unable to create runtime component directory." }
        }
        require(parent.isDirectory && target.parentFile.canonicalFile == parent) {
            "Runtime component destination is invalid."
        }
        val temporary = File(parent, ".${target.name}.verified-${UUID.randomUUID()}")
        val connection = connectionFactory(URL(address))
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/octet-stream")
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Runtime provider returned HTTP ${connection.responseCode}."
            }
            val declaredLength = connection.contentLengthLong
            require(declaredLength == -1L || declaredLength in 1..maxArchiveBytes) {
                "Runtime component exceeds the configured download limit."
            }

            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(publicKey)
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            var lastReported = 0L
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (isCancelled()) throw CancellationException("Runtime component download cancelled.")
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(count > 0 && received <= maxArchiveBytes - count) {
                            "Runtime component exceeds the configured download limit."
                        }
                        verifier.update(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        received += count
                        if (received - lastReported >= PROGRESS_INTERVAL_BYTES ||
                            (declaredLength > 0L && received == declaredLength)
                        ) {
                            onProgress(received, declaredLength.takeIf { it > 0L })
                            lastReported = received
                        }
                    }
                }
            }
            require(received > 0L) { "Runtime component download is empty." }
            require(declaredLength <= 0L || received == declaredLength) {
                "Runtime component length does not match the server declaration."
            }
            if (lastReported != received) onProgress(received, declaredLength.takeIf { it > 0L })
            if (isCancelled()) throw CancellationException("Runtime component verification cancelled.")
            require(verifier.verify(expectedSignature)) {
                "Runtime component RSA/SHA-256 signature verification failed."
            }

            val backup = if (target.exists()) {
                File(parent, ".${target.name}.backup-${UUID.randomUUID()}").also {
                    check(target.renameTo(it)) { "Unable to stage the previous runtime component." }
                }
            } else null
            try {
                check(temporary.renameTo(target)) {
                    "Unable to promote the authenticated runtime component."
                }
                backup?.delete()
            } catch (failure: Exception) {
                if (backup != null && backup.exists() && !backup.renameTo(target)) {
                    failure.addSuppressed(
                        IllegalStateException("The previous runtime component could not be restored.")
                    )
                }
                throw failure
            }
            return VerifiedMojoRuntimeComponent(
                file = target,
                sizeBytes = received,
                sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            )
        } finally {
            connection.disconnect()
            temporary.delete()
        }
    }

    companion object {
        private const val TRUSTED_HOST = "mojolauncher.github.io"
        private const val DEFAULT_TIMEOUT_MS = 20_000
        private const val MAX_TIMEOUT_MS = 120_000
        const val DEFAULT_MAX_ARCHIVE_BYTES = 512L * 1024 * 1024
        private const val ABSOLUTE_MAX_ARCHIVE_BYTES = 1024L * 1024 * 1024
        private const val RSA_4096_SIGNATURE_BYTES = 512
        private const val BUFFER_SIZE = 16 * 1024
        private const val PROGRESS_INTERVAL_BYTES = 256 * 1024
        private val COMPONENT_PATH = Regex(
            "/jre-download/components/(jre-new|jre-21|jre-25)/(universal|bin-(arm|arm64|x86|x86_64))\\.tar\\.xz"
        )
    }
}

data class VerifiedMojoRuntimeComponent(
    val file: File,
    val sizeBytes: Long,
    val sha256: String
)
