package com.mflegend.flamelauncher.catalog

import android.net.Uri
import com.mflegend.flamelauncher.storage.SafGameStorage
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CancellationException

data class AssetDownloadResult(
    val indexId: String,
    val downloaded: Int,
    val alreadyPresent: Int,
    val totalBytes: Long,
    val virtualAssetsMapped: Int = 0,
    val resourceAssetsMapped: Int = 0
)

private data class AssetMaterialization(
    val pathSegments: List<String>,
    val hash: String,
    val sizeBytes: Long
)

/**
 * Downloads the asset index and content-addressed objects referenced by a
 * Mojang version JSON. All files are written through Android's SAF provider.
 */
class MojangAssetDownloader {
    fun download(
        versionMetadataBytes: ByteArray,
        storage: SafGameStorage,
        gameRoot: Uri,
        control: DownloadControl = DownloadControl()
    ): AssetDownloadResult {
        val version = JSONObject(MojangMetadataText.decode(versionMetadataBytes))
        val assetIndex = version.optJSONObject("assetIndex")
            ?: error("This version does not define an asset index.")
        val indexId = assetIndex.optString("id")
        require(indexId.matches(Regex("[A-Za-z0-9._-]{1,100}"))) { "Asset index ID is invalid." }
        val indexUrl = URL(assetIndex.optString("url"))
        require(MojangHttpsEndpointPolicy.isApproved(indexUrl) &&
            indexUrl.host.equals("piston-meta.mojang.com", ignoreCase = true)) {
            "Asset index URL is not an approved Mojang HTTPS endpoint."
        }
        val expectedIndexSha1 = assetIndex.optString("sha1")
        require(expectedIndexSha1.matches(Regex("[a-fA-F0-9]{40}"))) {
            "Asset index metadata has no valid SHA-1 checksum."
        }

        val indexBytes = fetchIndex(indexUrl, expectedIndexSha1, control)
        val indexJson = JSONObject(MojangMetadataText.decode(indexBytes))
        val objects = indexJson.optJSONObject("objects")
            ?: error("Asset index does not contain an objects map.")
        require(objects.length() <= MAX_ASSET_COUNT) { "Asset index contains too many objects." }
        val isVirtual = readOptionalBoolean(indexJson, "virtual")
        val mapToResources = readOptionalBoolean(indexJson, "map_to_resources")

        val logicalPaths = mutableListOf<List<String>>()
        val validationKeys = objects.keys()
        while (validationKeys.hasNext()) {
            control.checkpoint()
            val logicalName = validationKeys.next()
            require(objects.optJSONObject(logicalName) != null) {
                "Asset index contains a malformed object entry."
            }
            logicalPaths += AssetLogicalPathPolicy.segments(logicalName)
        }
        AssetLogicalPathPolicy.validateUnique(logicalPaths)

        val assetsRoot = findOrCreateDirectory(storage, gameRoot, "assets")
        val indexesDir = findOrCreateDirectory(storage, assetsRoot, "indexes")
        saveIndex(storage, indexesDir, "$indexId.json", indexBytes, expectedIndexSha1)

        val objectsDir = findOrCreateDirectory(storage, assetsRoot, "objects")
        var downloaded = 0
        var present = 0
        var totalBytes = 0L
        val tracker = AssetObjectTracker(MAX_ASSET_BYTES, MAX_TOTAL_BYTES)
        val materializations = mutableListOf<AssetMaterialization>()
        val objectUris = HashMap<String, Uri>()

        val keys = objects.keys()
        while (keys.hasNext()) {
            control.checkpoint()
            val logicalName = keys.next()
            val item = objects.optJSONObject(logicalName)
                ?: error("Asset index contains a malformed object entry.")
            val logicalPath = AssetLogicalPathPolicy.segments(logicalName)
            val hash = item.optString("hash").lowercase()
            val size = item.optLong("size", -1L)
            val isNewObject = tracker.register(hash, size)
            if (isVirtual || mapToResources) {
                materializations += AssetMaterialization(logicalPath, hash, size)
            }
            if (!isNewObject) continue

            val prefix = hash.substring(0, 2)
            val prefixDir = findOrCreateDirectory(storage, objectsDir, prefix)
            val existing = storage.findFile(prefixDir, hash)
            if (existing != null && isVerified(storage, existing.uri, size, hash)) {
                present++
                objectUris[hash] = existing.uri
                continue
            }
            val temporaryName = "." + hash + ".download"
            val fileUri = storage.createTemporaryFile(prefixDir, temporaryName, "application/octet-stream")
            try {
                val objectUrl = URL("https://resources.download.minecraft.net/" + prefix + "/" + hash)
                downloadObject(objectUrl, storage, fileUri, size, hash, control)
                require(DownloadedFileVerifier.matches(storage.openInput(fileUri), size, hash, control)) {
                    "Stored asset object verification failed: $hash"
                }
                control.checkpoint()
                objectUris[hash] = storage.replaceWithVerifiedFile(fileUri, existing?.uri, hash)
                downloaded++
                totalBytes += size
            } catch (error: Exception) {
                storage.deleteTemporaryAfterFailure(fileUri, error)
                throw error
            }
        }

        val layoutCopies = (if (isVirtual) 1 else 0) + (if (mapToResources) 1 else 0)
        if (layoutCopies > 0) {
            require(tracker.totalDeclaredBytes <= MAX_TOTAL_MATERIALIZED_BYTES / layoutCopies) {
                "Asset index materialization exceeds the total storage safety limit."
            }
        }

        var virtualMapped = 0
        if (isVirtual) {
            val virtualRoot = findOrCreateDirectory(storage, assetsRoot, "virtual")
            val virtualIndexRoot = findOrCreateDirectory(storage, virtualRoot, indexId)
            val directoryCache = hashMapOf("" to virtualIndexRoot)
            materializations.forEachIndexed { index, asset ->
                control.checkpoint()
                val objectUri = objectUris[asset.hash]
                    ?: error("Verified asset object is unavailable for virtual path materialization.")
                materializeAsset(
                    storage, objectUri, virtualIndexRoot, directoryCache,
                    asset, "Virtual assets", control
                )
                virtualMapped++
                control.progress("Virtual assets", index + 1L, materializations.size.toLong())
            }
        }

        var resourcesMapped = 0
        if (mapToResources) {
            val resourcesRoot = findOrCreateDirectory(storage, gameRoot, "resources")
            val directoryCache = hashMapOf("" to resourcesRoot)
            materializations.forEachIndexed { index, asset ->
                control.checkpoint()
                val objectUri = objectUris[asset.hash]
                    ?: error("Verified asset object is unavailable for resource path materialization.")
                materializeAsset(
                    storage, objectUri, resourcesRoot, directoryCache,
                    asset, "Resource assets", control
                )
                resourcesMapped++
                control.progress("Resource assets", index + 1L, materializations.size.toLong())
            }
        }

        return AssetDownloadResult(
            indexId = indexId,
            downloaded = downloaded,
            alreadyPresent = present,
            totalBytes = totalBytes,
            virtualAssetsMapped = virtualMapped,
            resourceAssetsMapped = resourcesMapped
        )
    }

    private fun fetchIndex(url: URL, expectedSha1: String, control: DownloadControl): ByteArray {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "FlameLauncher-Android/0.1.0")
        }
        try {
            val status = connection.responseCode
            require(status in 200..299) { "Asset index request failed (HTTP $status)." }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    control.checkpoint()
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_INDEX_BYTES) { "Asset index is larger than the safety limit." }
                    output.write(buffer, 0, count)
                    control.progress("Asset index", total.toLong(), null)
                }
                output.toByteArray()
            }
            require(sha1(bytes).equals(expectedSha1, ignoreCase = true)) {
                "Asset index SHA-1 verification failed."
            }
            return bytes
        } finally {
            connection.disconnect()
        }
    }

    private fun saveIndex(
        storage: SafGameStorage,
        directory: Uri,
        name: String,
        bytes: ByteArray,
        expectedSha1: String
    ) {
        val oldFile = storage.findFile(directory, name)
        val temporaryName = ".$name.download"
        val uri = storage.createTemporaryFile(directory, temporaryName, "application/json")
        try {
            storage.openOutput(uri).use { output ->
                output.write(bytes)
                output.flush()
            }
            val storedSha1 = sha1(storage.openInput(uri).use { it.readBytes() })
            require(storedSha1.equals(expectedSha1, ignoreCase = true)) {
                "Stored asset index SHA-1 verification failed."
            }
            storage.replaceWithVerifiedFile(uri, oldFile?.uri, name)
        } catch (error: Exception) {
            storage.deleteTemporaryAfterFailure(uri, error)
            throw error
        }
    }

    private fun downloadObject(
        url: URL,
        storage: SafGameStorage,
        destination: Uri,
        expectedSize: Long,
        expectedSha1: String,
        control: DownloadControl
    ) {
        require(MojangHttpsEndpointPolicy.isApproved(url) &&
            url.host.equals("resources.download.minecraft.net", ignoreCase = true)) {
            "Asset object URL is not an approved Mojang HTTPS endpoint."
        }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "FlameLauncher-Android/0.1.0")
        }
        try {
            val status = connection.responseCode
            require(status in 200..299) { "Asset download failed (HTTP $status)." }
            val digest = MessageDigest.getInstance("SHA-1")
            var total = 0L
            connection.inputStream.use { input ->
                storage.openOutput(destination).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        control.checkpoint()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= expectedSize && total <= MAX_ASSET_BYTES) {
                            "Asset object exceeded its declared size."
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        control.progress("Assets", total, expectedSize)
                    }
                    output.flush()
                }
            }
            require(total == expectedSize) { "Asset object size verification failed." }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            require(actual.equals(expectedSha1, ignoreCase = true)) { "Asset object SHA-1 verification failed." }
        } finally {
            connection.disconnect()
        }
    }

    private fun isVerified(storage: SafGameStorage, uri: Uri, expectedSize: Long, expectedSha1: String): Boolean =
        runCatching {
            val digest = MessageDigest.getInstance("SHA-1")
            var total = 0L
            storage.openInput(uri).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > expectedSize) return@runCatching false
                    digest.update(buffer, 0, count)
                }
            }
            total == expectedSize &&
                digest.digest().joinToString("") { "%02x".format(it) }.equals(expectedSha1, ignoreCase = true)
        }.getOrDefault(false)

    private fun materializeAsset(
        storage: SafGameStorage,
        sourceObject: Uri,
        targetRoot: Uri,
        directoryCache: MutableMap<String, Uri>,
        asset: AssetMaterialization,
        stage: String,
        control: DownloadControl
    ) {
        val parentSegments = asset.pathSegments.dropLast(1)
        val parent = findOrCreateDirectoryPath(storage, targetRoot, parentSegments, directoryCache)
        val fileName = asset.pathSegments.last()
        val existing = storage.findFile(parent, fileName)
        if (existing != null && isVerified(storage, existing.uri, asset.sizeBytes, asset.hash)) return

        val tempKey = asset.pathSegments.joinToString("/") + ":" + asset.hash
        val temporaryName = "." + sha1(tempKey.toByteArray()).take(20) + ".download"
        val temporaryUri = storage.createTemporaryFile(parent, temporaryName, "application/octet-stream")
        try {
            val digest = MessageDigest.getInstance("SHA-1")
            var copiedBytes = 0L
            storage.openInput(sourceObject).use { input ->
                storage.openOutput(temporaryUri).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        control.checkpoint()
                        val count = input.read(buffer)
                        if (count < 0) break
                        copiedBytes += count
                        require(copiedBytes <= asset.sizeBytes && copiedBytes <= MAX_ASSET_BYTES) {
                            "Materialized asset exceeded its declared size."
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        control.progress(stage, copiedBytes, asset.sizeBytes)
                    }
                    output.flush()
                }
            }
            require(copiedBytes == asset.sizeBytes) { "Materialized asset size verification failed." }
            val copiedHash = digest.digest().joinToString("") { "%02x".format(it) }
            require(copiedHash.equals(asset.hash, ignoreCase = true)) {
                "Materialized asset SHA-1 verification failed."
            }
            require(DownloadedFileVerifier.matches(storage.openInput(temporaryUri), asset.sizeBytes, asset.hash, control)) {
                "Stored materialized asset verification failed."
            }
            control.checkpoint()
            storage.replaceWithVerifiedFile(temporaryUri, existing?.uri, fileName)
        } catch (error: Exception) {
            storage.deleteTemporaryAfterFailure(temporaryUri, error)
            throw error
        }
    }

    private fun findOrCreateDirectoryPath(
        storage: SafGameStorage,
        root: Uri,
        segments: List<String>,
        cache: MutableMap<String, Uri>
    ): Uri {
        var current = root
        var currentPath = ""
        for (segment in segments) {
            currentPath = if (currentPath.isEmpty()) segment else currentPath + "/" + segment
            current = cache[currentPath] ?: run {
                val directory = findOrCreateDirectory(storage, current, segment)
                cache[currentPath] = directory
                directory
            }
        }
        return current
    }

    private fun readOptionalBoolean(json: JSONObject, key: String): Boolean {
        if (!json.has(key)) return false
        val value = json.get(key)
        require(value is Boolean) { "Asset index field '$key' must be a boolean." }
        return value
    }

    private fun findOrCreateDirectory(storage: SafGameStorage, parent: Uri, name: String): Uri =
        storage.findDirectory(parent, name)?.uri ?: storage.createDirectory(parent, name)

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_INDEX_BYTES = 32 * 1024 * 1024
        const val MAX_ASSET_COUNT = 150_000
        const val MAX_ASSET_BYTES = 256L * 1024L * 1024L
        const val MAX_TOTAL_BYTES = 12L * 1024L * 1024L * 1024L
        const val MAX_TOTAL_MATERIALIZED_BYTES = 24L * 1024L * 1024L * 1024L
    }
}
