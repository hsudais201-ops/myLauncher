package com.mflegend.flamelauncher.catalog

import android.net.Uri
import com.mflegend.flamelauncher.storage.SafGameStorage
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CancellationException

data class ClientJarDownloadResult(val fileUri: Uri, val byteCount: Long)

/**
 * Downloads the official vanilla client JAR described by a verified version JSON.
 * Writes directly to SAF instead of buffering large game archives in memory.
 */
class MojangClientJarDownloader {
    fun download(
        versionId: String,
        metadataBytes: ByteArray,
        storage: SafGameStorage,
        versionDirectory: Uri,
        control: DownloadControl = DownloadControl()
    ): ClientJarDownloadResult {
        require(versionId.matches(Regex("[A-Za-z0-9._-]{1,100}"))) { "Invalid version identifier." }
        val metadata = JSONObject(metadataBytes.toString(Charsets.UTF_8))
        require(metadata.optString("id") == versionId) { "Version metadata ID does not match the selected version." }
        val client = metadata.optJSONObject("downloads")?.optJSONObject("client")
            ?: error("This version metadata does not provide a vanilla client JAR.")
        val downloadUrl = URL(client.optString("url"))
        require(MojangHttpsEndpointPolicy.isApproved(downloadUrl) && downloadUrl.host.lowercase() in CLIENT_JAR_HOSTS) {
            "Client JAR URL is not an approved Mojang HTTPS endpoint."
        }
        val expectedSha1 = client.optString("sha1")
        require(expectedSha1.matches(Regex("[a-fA-F0-9]{40}"))) { "Client JAR metadata has no valid SHA-1 checksum." }
        val expectedSize = client.optLong("size", -1L)
        require(expectedSize in 1..MAX_CLIENT_JAR_BYTES) { "Client JAR size is missing or exceeds the safety limit." }

        val fileName = "$versionId.jar"
        val oldFile = storage.listChildren(versionDirectory).firstOrNull { !it.isDirectory && it.name == fileName }
        val temporaryName = ".$fileName.download"
        storage.listChildren(versionDirectory).firstOrNull { !it.isDirectory && it.name == temporaryName }?.let { storage.delete(it.uri) }
        val fileUri = storage.createFile(versionDirectory, temporaryName, "application/java-archive")
        val connection = (downloadUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "FlameLauncher-Android/0.1.0")
        }
        try {
            val status = connection.responseCode
            require(status in 200..299) { "Client JAR download failed (HTTP $status)." }
            val digest = MessageDigest.getInstance("SHA-1")
            var total = 0L
            connection.inputStream.use { input ->
                storage.openOutput(fileUri).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        control.checkpoint()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= expectedSize && total <= MAX_CLIENT_JAR_BYTES) {
                            "Client JAR exceeded its declared size."
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        control.progress("Client JAR", total, expectedSize)
                    }
                    output.flush()
                }
            }
            require(total == expectedSize) { "Client JAR size verification failed: expected $expectedSize bytes, received $total." }
            val actualSha1 = digest.digest().joinToString("") { "%02x".format(it) }
            require(actualSha1.equals(expectedSha1, ignoreCase = true)) { "Client JAR SHA-1 verification failed." }
            require(DownloadedFileVerifier.matches(storage.openInput(fileUri), expectedSize, expectedSha1, control)) {
                "Stored client JAR verification failed."
            }
            control.checkpoint()
            val finalUri = storage.replaceWithVerifiedFile(fileUri, oldFile?.uri, fileName)
                ?: error("The storage provider could not finalize the verified client JAR.")
            return ClientJarDownloadResult(finalUri, total)
        } catch (error: Exception) {
            storage.deleteTemporaryAfterFailure(fileUri, error)
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        val CLIENT_JAR_HOSTS = setOf("launcher.mojang.com", "piston-data.mojang.com")
        const val MAX_CLIENT_JAR_BYTES = 1_500_000_000L
    }
}
