package com.mflegend.flamelauncher.catalog

import android.net.Uri
import android.os.Build
import com.mflegend.flamelauncher.storage.SafGameStorage
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CancellationException

data class LibraryDownloadResult(
    val downloaded: Int,
    val alreadyPresent: Int,
    val skippedByRules: Int,
    val totalBytes: Long
)

/**
 * Downloads only unconditional Maven artifacts from a verified version JSON.
 * OS-rule-dependent artifacts and native classifiers are deliberately deferred:
 * Android is not interchangeable with desktop Linux.
 */
class MojangLibraryDownloader {
    fun download(
        metadataBytes: ByteArray,
        storage: SafGameStorage,
        gameRoot: Uri,
        control: DownloadControl = DownloadControl()
    ): LibraryDownloadResult {
        val metadata = JSONObject(MojangMetadataText.decode(metadataBytes))
        val libraries = metadata.optJSONArray("libraries")
            ?: return LibraryDownloadResult(0, 0, 0, 0)
        require(libraries.length() <= MAX_LIBRARY_COUNT) { "Version metadata contains too many libraries." }

        val libraryRoot = findOrCreateDirectory(storage, gameRoot, "libraries")
        var downloaded = 0
        var present = 0
        var skipped = 0
        var totalBytes = 0L
        val artifactTracker = LibraryArtifactTracker(MAX_ARTIFACT_BYTES, MAX_TOTAL_BYTES)
        val ruleContext = MojangLibraryRuleContext(
            platformName = "android",
            osVersion = Build.VERSION.RELEASE.orEmpty(),
            osArchitecture = System.getProperty("os.arch")
                ?: Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        )
        for (index in 0 until libraries.length()) {
            control.checkpoint()
            val library = libraries.optJSONObject(index) ?: continue
            if (!MojangLibraryRulePolicy.isAllowed(library, ruleContext)) {
                skipped++
                continue
            }
            val artifact = library.optJSONObject("downloads")?.optJSONObject("artifact") ?: continue
            val relativePath = artifact.optString("path")
            val segments = LibraryArtifactPathPolicy.segments(relativePath)
            val url = URL(artifact.optString("url"))
            require(MojangLibraryEndpointPolicy.isApproved(url)) {
                "Library artifact uses an unsupported repository host: ${url.host}"
            }
            val sha1 = artifact.optString("sha1")
            require(sha1.matches(Regex("[a-fA-F0-9]{40}"))) {
                "Library artifact has no valid SHA-1 checksum: $relativePath"
            }
            val expectedSize = artifact.optLong("size", -1L)
            require(expectedSize in 1..MAX_ARTIFACT_BYTES) {
                "Library artifact size is missing or exceeds the safety limit: $relativePath"
            }
            if (!artifactTracker.register(relativePath, expectedSize, sha1.lowercase())) continue

            var parent = libraryRoot
            for (segment in segments.dropLast(1)) {
                parent = findOrCreateDirectory(storage, parent, segment)
            }
            val fileName = segments.last()
            val existing = storage.findFile(parent, fileName)
            if (existing != null && isVerified(storage, existing.uri, expectedSize, sha1)) {
                present++
                continue
            }
            require(totalBytes <= MAX_TOTAL_BYTES - expectedSize) {
                "Library set exceeds the total download safety limit."
            }
            val fileUri = storage.createUniqueTemporaryFile(parent, "application/java-archive")
            try {
                downloadOne(url, storage, fileUri, expectedSize, sha1, control)
                require(DownloadedFileVerifier.matches(storage.openInput(fileUri), expectedSize, sha1, control)) {
                    "Stored library artifact verification failed: $relativePath"
                }
                control.checkpoint()
                storage.replaceWithVerifiedFile(fileUri, existing?.uri, fileName)
                totalBytes += expectedSize
                downloaded++
            } catch (error: Exception) {
                storage.deleteTemporaryAfterFailure(fileUri, error)
                throw error
            }
        }
        return LibraryDownloadResult(downloaded, present, skipped, totalBytes)
    }

    private fun downloadOne(
        url: URL,
        storage: SafGameStorage,
        destination: Uri,
        expectedSize: Long,
        expectedSha1: String,
        control: DownloadControl
    ) {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "FlameLauncher-Android/0.1.0")
        }
        try {
            val status = connection.responseCode
            require(status in 200..299) { "Library download failed (HTTP $status) for ${url.host}." }
            val digest = MessageDigest.getInstance("SHA-1")
            var countTotal = 0L
            connection.inputStream.use { input ->
                storage.openOutput(destination).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        control.checkpoint()
                        val count = input.read(buffer)
                        if (count < 0) break
                        countTotal += count
                        require(countTotal <= expectedSize) { "Library artifact exceeded its declared size." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        control.progress("Libraries", countTotal, expectedSize)
                    }
                    output.flush()
                }
            }
            require(countTotal == expectedSize) { "Library artifact size verification failed." }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            require(actual.equals(expectedSha1, ignoreCase = true)) { "Library artifact SHA-1 verification failed." }
        } finally {
            connection.disconnect()
        }
    }

    private fun isVerified(
        storage: SafGameStorage,
        uri: Uri,
        expectedSize: Long,
        expectedSha1: String
    ): Boolean = runCatching {
        val digest = MessageDigest.getInstance("SHA-1")
        var count = 0L
        storage.openInput(uri).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                count += read
                if (count > expectedSize) return@runCatching false
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        count == expectedSize && actual.equals(expectedSha1, ignoreCase = true)
    }.getOrDefault(false)

    private fun findOrCreateDirectory(storage: SafGameStorage, parent: Uri, name: String): Uri {
        return storage.findDirectory(parent, name)?.uri ?: storage.createDirectory(parent, name)
    }

    private companion object {
        const val MAX_LIBRARY_COUNT = 1000
        const val MAX_ARTIFACT_BYTES = 512L * 1024L * 1024L
        const val MAX_TOTAL_BYTES = 4L * 1024L * 1024L * 1024L
    }
}
