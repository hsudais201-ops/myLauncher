
package com.voidcraft.launcher

import android.content.Context
import android.os.StatFs
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private const val PIPELINE_USER_AGENT = "Voidcraft-Android-Launcher/0.1"
private const val MAX_LIBRARY_FILE_BYTES = 250L * 1024L * 1024L
private const val MAX_LIBRARY_TOTAL_BYTES = 1024L * 1024L * 1024L
private const val MAX_ASSET_INDEX_BYTES = 25L * 1024L * 1024L
private const val MAX_ASSET_TOTAL_BYTES = 3L * 1024L * 1024L * 1024L
private const val MINIMUM_FREE_SPACE_BYTES = 16L * 1024L * 1024L

private val trustedMetadataHosts = setOf("piston-meta.mojang.com", "launchermeta.mojang.com")
private val trustedLibraryHosts = setOf(
    "libraries.minecraft.net",
    "repo.maven.apache.org",
    "repo1.maven.org",
    "maven.fabricmc.net",
    "maven.minecraftforge.net",
    "maven.neoforged.net",
    "maven.quiltmc.org",
    "maven.terraformersmc.com",
    "maven.parchmentmc.org",
    "maven.legacyfabric.net"
)
private val trustedAssetHosts = setOf("resources.download.minecraft.net")

private const val MAX_TRUSTED_DOWNLOAD_REDIRECTS = 5

/**
 * Opens a GET connection only after validating the starting URL and each redirect destination.
 * Redirects never downgrade to HTTP and never contact a host outside the supplied allowlist.
 */
internal fun openTrustedGetConnection(
    startUrl: URL,
    allowedHosts: Set<String>,
    connectTimeoutMs: Int,
    readTimeoutMs: Int,
    acceptHeader: String = "*/*"
): HttpURLConnection {
    var current = startUrl
    var redirects = 0
    while (true) {
        require(isApprovedHttpsDownloadUrl(current, allowedHosts)) {
            "Download URL must use HTTPS on an approved host without credentials or a custom port"
        }
        val connection = (current.openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("User-Agent", PIPELINE_USER_AGENT)
            setRequestProperty("Accept", acceptHeader)
            setRequestProperty("Accept-Encoding", "identity")
        }
        val responseCode = try {
            connection.responseCode
        } catch (failure: Exception) {
            connection.disconnect()
            throw failure
        }
        val isRedirect = responseCode == 301 ||
            responseCode == 302 ||
            responseCode == 303 ||
            responseCode == 307 ||
            responseCode == 308
        if (!isRedirect) {
            if (responseCode !in 200..299) {
                connection.disconnect()
                error("Download returned HTTP $responseCode")
            }
            return connection
        }

        val location = connection.getHeaderField("Location")
        connection.disconnect()
        if (redirects >= MAX_TRUSTED_DOWNLOAD_REDIRECTS) {
            error("Download redirected too many times")
        }
        if (location.isNullOrBlank()) error("Download redirect has no destination")
        current = resolveTrustedDownloadRedirect(current, location, allowedHosts)
        redirects++
    }
}

/**
 * Downloads metadata-listed library JARs only. Desktop native classifiers are intentionally
 * excluded because Android requires a mobile LWJGL/native compatibility layer.
 */
internal fun downloadMinecraftLibraries(
    context: Context,
    versionMetadataUrl: String,
    expectedVersionMetadataSha1: String,
    onProgress: (String) -> Unit
): Int {
    val version = fetchVersionMetadata(versionMetadataUrl, expectedVersionMetadataSha1)
    val rows = version.optJSONArray("libraries") ?: error("Version JSON has no libraries list")
    val artifacts = buildList {
        for (index in 0 until rows.length()) {
            val library = rows.optJSONObject(index) ?: continue
            if (!libraryAllowedOnAndroid(library)) continue
            val artifact = library.optJSONObject("downloads")?.optJSONObject("artifact") ?: continue
            val path = artifact.optString("path").trim()
            if (!path.endsWith(".jar", ignoreCase = true) ||
                path.substringAfterLast('/').contains("natives-", ignoreCase = true)
            ) continue
            val url = artifact.optString("url").trim()
            val sha1 = artifact.optString("sha1").trim().lowercase()
            val size = artifact.optLong("size", -1L)
            if (path.isBlank() || url.isBlank() ||
                !sha1.matches(Regex("^[0-9a-f]{40}$")) ||
                size !in 1L..MAX_LIBRARY_FILE_BYTES
            ) continue
            add(LibraryArtifact(path, url, sha1, size))
        }
    }.distinctBy { it.path }

    require(artifacts.isNotEmpty()) { "No supported library JAR artifacts found in this version metadata" }
    val totalBytes = artifacts.sumOf { it.size }
    require(totalBytes <= MAX_LIBRARY_TOTAL_BYTES) { "Libraries exceed Voidcraft's 1 GiB safety limit" }
    val destinationRoot = resolveEngineStorageDirectory(context, "DIR_HOME_LIBRARY", "minecraft/libraries")
    require(destinationRoot.isDirectory || destinationRoot.mkdirs()) { "Could not create libraries folder" }
    require(availableSpace(destinationRoot) >= totalBytes + MINIMUM_FREE_SPACE_BYTES) {
        "Not enough free storage in the configured game directory for libraries"
    }
    val canonicalRoot = destinationRoot.canonicalFile.path + File.separator
    var completed = 0

    artifacts.forEachIndexed { index, artifact ->
        val relative = artifact.path
        require(!relative.startsWith("/") &&
            !relative.contains(92.toChar()) &&
            !relative.contains(':') &&
            relative.split('/').none { it.isBlank() || it == "." || it == ".." }
        ) { "Unsafe library path in version metadata" }

        val destination = File(destinationRoot, relative)
        require(destination.canonicalPath.startsWith(canonicalRoot)) { "Library path escapes private storage" }
        val cached = destination.isFile && destination.length() == artifact.size &&
            sha1OfFile(destination).equals(artifact.sha1, ignoreCase = true)
        if (!cached) {
            onProgress("Downloading library ${index + 1}/${artifacts.size}: ${destination.name}")
            downloadVerified(
                artifact.url, destination, artifact.sha1, artifact.size,
                MAX_LIBRARY_FILE_BYTES, trustedLibraryHosts
            )
        } else {
            onProgress("Library ${index + 1}/${artifacts.size} already verified: ${destination.name}")
        }
        completed++
    }
    return completed
}

/**
 * Fetches the asset index, then downloads and verifies every referenced object.
 * The UI should clearly warn users that this can require hundreds of megabytes.
 */
internal fun downloadMinecraftAssets(
    context: Context,
    versionMetadataUrl: String,
    expectedVersionMetadataSha1: String,
    onProgress: (String) -> Unit
): Int {
    val version = fetchVersionMetadata(versionMetadataUrl, expectedVersionMetadataSha1)
    val assetIndex = version.optJSONObject("assetIndex")
        ?: error("Version JSON has no asset index metadata")
    val indexId = assetIndex.optString("id").trim()
    val indexUrl = assetIndex.optString("url").trim()
    val indexSha1 = assetIndex.optString("sha1").trim().lowercase()
    val indexSize = assetIndex.optLong("size", -1L)
    require(indexId.matches(Regex("^[A-Za-z0-9._-]{1,100}$"))) { "Invalid asset index ID" }
    require(indexSha1.matches(Regex("^[0-9a-f]{40}$"))) { "Invalid asset index SHA-1" }
    require(indexSize in 1L..MAX_ASSET_INDEX_BYTES) { "Asset index size exceeds the safety limit" }
    val indexUrlObject = URL(indexUrl)
    require(isApprovedMinecraftVersionMetadataUrl(indexUrlObject)) {
        "Asset index URL is not hosted on an approved Mojang metadata host"
    }

    val assetsRoot = resolveEngineStorageDirectory(context, "ASSETS_PATH", "minecraft/assets")
    val indexesRoot = File(assetsRoot, "indexes")
    val indexFile = File(indexesRoot, "$indexId.json")
    val indexCached = indexFile.isFile && indexFile.length() == indexSize &&
        sha1OfFile(indexFile).equals(indexSha1, ignoreCase = true)
    if (!indexCached) {
        onProgress("Downloading and verifying asset index $indexId…")
        downloadVerified(indexUrl, indexFile, indexSha1, indexSize, MAX_ASSET_INDEX_BYTES, trustedMetadataHosts)
    }

    val index = JSONObject(indexFile.readText())
    val objects = index.optJSONObject("objects") ?: error("Asset index has no objects map")
    val assets = buildList {
        val iterator = objects.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val item = objects.optJSONObject(key) ?: continue
            val hash = item.optString("hash").lowercase()
            val size = item.optLong("size", -1L)
            if (hash.matches(Regex("^[0-9a-f]{40}$")) && size in 1L..MAX_LIBRARY_FILE_BYTES) {
                add(AssetObject(hash, size))
            }
        }
    }.distinctBy { it.hash }

    require(assets.isNotEmpty()) { "Asset index does not list any valid asset objects" }
    val totalBytes = assets.sumOf { it.size }
    require(totalBytes <= MAX_ASSET_TOTAL_BYTES) { "Assets exceed Voidcraft's 3 GiB safety limit" }
    require(assetsRoot.isDirectory || assetsRoot.mkdirs()) { "Could not create the configured assets directory" }
    require(availableSpace(assetsRoot) >= totalBytes + indexSize + MINIMUM_FREE_SPACE_BYTES) {
        "Not enough free storage in the configured game directory for all game assets (about ${totalBytes / (1024L * 1024L)} MiB needed)"
    }

    val objectRoot = File(assetsRoot, "objects")
    require(objectRoot.isDirectory || objectRoot.mkdirs()) { "Could not create asset objects folder" }
    val canonicalRoot = objectRoot.canonicalFile.path + File.separator
    val nextIndex = AtomicInteger(0)
    val completed = AtomicInteger(0)
    val firstFailure = AtomicReference<Throwable?>(null)
    val workerCount = minOf(4, assets.size)
    val executor = Executors.newFixedThreadPool(workerCount)
    val futures = (0 until workerCount).map {
        executor.submit {
            while (firstFailure.get() == null) {
                val itemIndex = nextIndex.getAndIncrement()
                if (itemIndex >= assets.size) break
                val asset = assets[itemIndex]
                try {
                    val prefix = asset.hash.substring(0, 2)
                    val destination = File(objectRoot, "$prefix/${asset.hash}")
                    require(destination.canonicalPath.startsWith(canonicalRoot)) { "Asset path escapes private storage" }
                    val cached = destination.isFile && destination.length() == asset.size &&
                        sha1OfFile(destination).equals(asset.hash, ignoreCase = true)
                    if (!cached) {
                        val url = "https://resources.download.minecraft.net/$prefix/${asset.hash}"
                        downloadVerified(url, destination, asset.hash, asset.size, MAX_LIBRARY_FILE_BYTES, trustedAssetHosts)
                    }
                    val done = completed.incrementAndGet()
                    if (done == assets.size || done % 10 == 0) {
                        onProgress("Verified game assets: ${done}/${assets.size}")
                    }
                } catch (failure: Throwable) {
                    firstFailure.compareAndSet(null, failure)
                    throw failure
                }
            }
        }
    }
    try {
        futures.forEach { it.get() }
    } catch (failure: ExecutionException) {
        val cause = failure.cause ?: failure
        if (cause is Exception) throw cause
        throw RuntimeException(cause)
    } finally {
        executor.shutdownNow()
    }
    firstFailure.get()?.let { failure ->
        if (failure is Exception) throw failure
        throw RuntimeException(failure)
    }
    return completed.get()
}

private data class LibraryArtifact(val path: String, val url: String, val sha1: String, val size: Long)
private data class AssetObject(val hash: String, val size: Long)

private fun fetchVersionMetadata(urlText: String, expectedSha1: String): JSONObject {
    require(expectedSha1.matches(Regex("^[0-9a-fA-F]{40}$"))) {
        "Official version manifest did not provide a valid version JSON SHA-1"
    }
    val url = URL(urlText)
    require(isApprovedMinecraftVersionMetadataUrl(url)) {
        "Version metadata must come from Mojang's official HTTPS metadata hosts"
    }
    val connection = openTrustedGetConnection(url, trustedMetadataHosts, 8000, 15000)
    try {
        if (connection.responseCode !in 200..299) error("Mojang returned HTTP ${connection.responseCode}")
        val bodyBytes = connection.inputStream.use { input ->
            val buffer = ByteArray(8192)
            val output = ByteArrayOutputStream()
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= 10 * 1024 * 1024) { "Version metadata exceeds the 10 MiB safety limit" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val actualSha1 = MessageDigest.getInstance("SHA-1").digest(bodyBytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        require(actualSha1.equals(expectedSha1, ignoreCase = true)) {
            "Version JSON SHA-1 does not match Mojang's official manifest"
        }
        return JSONObject(bodyBytes.toString(Charsets.UTF_8))
    } finally {
        connection.disconnect()
    }
}

private fun libraryAllowedOnAndroid(library: JSONObject): Boolean {
    val rules = library.optJSONArray("rules") ?: return true
    var allowed = false
    val abi = (android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: System.getProperty("os.arch").orEmpty())
        .lowercase()
    for (index in 0 until rules.length()) {
        val rule = rules.optJSONObject(index) ?: continue
        var matches = true
        val os = rule.optJSONObject("os")
        if (os != null) {
            val name = os.optString("name")
            if (name.isNotBlank() && name != "linux") matches = false
            val arch = os.optString("arch")
            if (arch.isNotBlank()) {
                val archMatches = runCatching { Regex(arch).containsMatchIn(abi) }.getOrDefault(false)
                if (!archMatches) matches = false
            }
            if (os.optString("version").isNotBlank()) matches = false
        }
        val features = rule.optJSONObject("features")
        if (features != null && features.length() > 0) matches = false
        if (matches) allowed = rule.optString("action") == "allow"
    }
    return allowed
}

private fun downloadVerified(
    sourceUrl: String,
    destination: File,
    expectedSha1: String,
    expectedSize: Long,
    maxBytes: Long,
    allowedHosts: Set<String>
) {
    require(expectedSize in 1L..maxBytes) { "File size is outside the allowed download range" }
    val url = URL(sourceUrl)
    require(isApprovedHttpsDownloadUrl(url, allowedHosts)) {
        "Download URL host is not on the trusted HTTPS allowlist"
    }
    val parent = destination.parentFile ?: error("Download destination has no parent")
    require(parent.isDirectory || parent.mkdirs()) { "Could not create private download directory" }
    require(availableSpace(parent) >= expectedSize) { "Not enough free app storage for ${destination.name}" }
    val temporary = File(parent, destination.name + ".part")
    temporary.delete()
    val connection = openTrustedGetConnection(url, allowedHosts, 10000, 30000)
    try {
        if (connection.responseCode !in 200..299) error("Download returned HTTP ${connection.responseCode}")
        require(isApprovedHttpsDownloadUrl(connection.url, allowedHosts)) {
            "Download redirected outside the trusted HTTPS allowlist"
        }
        val responseLength = connection.getHeaderFieldLong("Content-Length", -1L)
        if (responseLength >= 0L && responseLength != expectedSize) error("Content length does not match metadata")
        val digest = MessageDigest.getInstance("SHA-1")
        var total = 0L
        connection.inputStream.use { input ->
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= expectedSize && total <= maxBytes) { "Download exceeded the expected file size" }
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
        require(total == expectedSize) { "Downloaded byte count does not match metadata" }
        val sha1 = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        require(sha1.equals(expectedSha1, ignoreCase = true)) { "SHA-1 verification failed" }
        if (destination.exists()) require(destination.delete()) { "Could not replace an invalid cached file" }
        require(temporary.renameTo(destination)) { "Could not finalize verified file" }
    } catch (failure: Exception) {
        temporary.delete()
        throw failure
    } finally {
        connection.disconnect()
    }
}

private fun sha1OfFile(file: File): String {
    val digest = MessageDigest.getInstance("SHA-1")
    FileInputStream(file).use { input ->
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private fun availableSpace(file: File): Long =
    runCatching { StatFs(file.absolutePath).availableBytes }.getOrDefault(0L)

/**
 * In the integrated build, write prepared game files into the same directories the Android
 * engine uses. In the standalone prototype, use app-private fallback storage instead.
 */
internal fun resolveEngineStorageDirectory(
    context: Context,
    engineFieldName: String,
    fallbackRelativePath: String
): File {
    val enginePath = runCatching {
        val toolsClass = Class.forName("net.kdt.pojavlaunch.Tools")
        toolsClass.getField(engineFieldName).get(null) as? String
    }.getOrNull()?.trim()

    if (!enginePath.isNullOrBlank() && enginePath != "null") {
        val candidate = File(enginePath)
        if (candidate.isAbsolute) return candidate
    }
    return File(context.filesDir, fallbackRelativePath)
}

