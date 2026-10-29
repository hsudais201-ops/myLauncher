package dev.mylauncher

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest

data class AssetInstallResult(
    val indexId: String,
    val downloaded: Int,
    val reused: Int,
    val bytesDownloaded: Long,
    val virtualFilesMaterialized: Int,
    val resourceFilesMaterialized: Int
)

/** Downloads hash-addressed assets and creates legacy virtual/resource layouts when requested by Mojang metadata. */
object MinecraftAssetInstaller {
    suspend fun install(context: Context, version: MinecraftVersion): AssetInstallResult =
        withContext(Dispatchers.IO) {
            require(version.id.matches(Regex("[A-Za-z0-9._-]{1,100}")))
            require(version.url.startsWith("https://"))
            val metadataText = fetchText(version.url)
            val metadata = JSONObject(metadataText)
            require(metadata.optString("id") == version.id) { "Version metadata ID mismatch." }

            val index = metadata.getJSONObject("assetIndex")
            val indexId = index.getString("id")
            require(indexId.matches(Regex("[A-Za-z0-9._-]{1,100}")))
            val indexUrl = index.getString("url")
            val indexHash = index.getString("sha1").lowercase()
            require(indexUrl.startsWith("https://"))
            require(indexHash.matches(Regex("[a-f0-9]{40}")))
            val indexBytes = downloadVerified(indexUrl, indexHash)
            val assetsRoot = File(context.filesDir, "minecraft/assets")
            val indexesDir = File(assetsRoot, "indexes")
            check(indexesDir.mkdirs() || indexesDir.isDirectory)
            writeAtomically(File(indexesDir, "$indexId.json"), indexBytes)

            val indexJson = JSONObject(String(indexBytes, Charsets.UTF_8))
            val objects = indexJson.getJSONObject("objects")
            val isVirtual = indexJson.optBoolean("virtual", false)
            val mapToResources = indexJson.optBoolean("map_to_resources", false)
            val objectsRoot = File(assetsRoot, "objects")
            var downloaded = 0
            var reused = 0
            var totalBytes = 0L
            var virtualCount = 0
            var resourcesCount = 0
            val iterator = objects.keys()
            while (iterator.hasNext()) {
                val objectName = iterator.next()
                require(isSafeAssetName(objectName)) { "Unsafe asset name in official index." }
                val entry = objects.getJSONObject(objectName)
                val hash = entry.getString("hash").lowercase()
                require(hash.matches(Regex("[a-f0-9]{40}")))
                val folder = File(objectsRoot, hash.substring(0, 2))
                check(folder.mkdirs() || folder.isDirectory)
                val target = File(folder, hash)
                if (target.isFile && sha1(target) == hash) {
                    reused++
                } else {
                    val part = File(folder, "$hash.part")
                    try {
                        val url = "https://resources.download.minecraft.net/${hash.substring(0, 2)}/$hash"
                        val bytes = downloadVerified(url, hash)
                        part.writeBytes(bytes)
                        if (target.exists() && !target.delete()) error("Could not replace asset object.")
                        check(part.renameTo(target)) { "Could not finalize asset object." }
                        downloaded++
                        totalBytes += bytes.size
                    } finally {
                        if (part.exists()) part.delete()
                    }
                }

                if (isVirtual) {
                    val virtualRoot = File(assetsRoot, "virtual/$indexId")
                    val virtualFile = safeChild(virtualRoot, objectName)
                    if (!virtualFile.isFile || sha1(virtualFile) != hash) {
                        copyAtomically(target, virtualFile)
                        virtualCount++
                    }
                }
                if (mapToResources) {
                    val resourceFile = File(assetsRoot, "resources/$hash")
                    if (!resourceFile.isFile || sha1(resourceFile) != hash) {
                        copyAtomically(target, resourceFile)
                        resourcesCount++
                    }
                }
            }
            AssetInstallResult(indexId, downloaded, reused, totalBytes, virtualCount, resourcesCount)
        }

    private fun isSafeAssetName(name: String): Boolean =
        name.isNotBlank() &&
            !name.startsWith("/") &&
            !name.startsWith("\\\\") &&
            !name.contains("\\\\") &&
            name.split("/").all { it.isNotBlank() && it != "." && it != ".." } &&
            name.matches(Regex("[A-Za-z0-9._+/-]{1,1000}"))

    private fun safeChild(root: File, relativePath: String): File {
        val child = File(root, relativePath)
        require(child.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())) {
            "Asset path escapes storage directory."
        }
        return child
    }

    private fun copyAtomically(source: File, destination: File) {
        val parent = destination.parentFile ?: error("Invalid asset destination.")
        check(parent.mkdirs() || parent.isDirectory)
        val canonicalRoot = parent.canonicalFile
        require(destination.canonicalFile.toPath().startsWith(canonicalRoot.toPath()))
        val part = File(parent, destination.name + ".part")
        try {
            source.inputStream().use { input -> part.outputStream().use { output -> input.copyTo(output) } }
            if (destination.exists() && !destination.delete()) error("Could not replace materialized asset.")
            check(part.renameTo(destination)) { "Could not finalize materialized asset." }
        } finally {
            if (part.exists()) part.delete()
        }
    }

    private fun writeAtomically(destination: File, bytes: ByteArray) {
        val parent = destination.parentFile ?: error("Invalid asset index destination.")
        check(parent.mkdirs() || parent.isDirectory)
        val part = File(parent, destination.name + ".part")
        try {
            part.writeBytes(bytes)
            if (destination.exists() && !destination.delete()) error("Could not replace asset index.")
            check(part.renameTo(destination)) { "Could not finalize asset index." }
        } finally {
            if (part.exists()) part.delete()
        }
    }

    private fun fetchText(url: String): String = String(fetch(url), Charsets.UTF_8)

    private fun downloadVerified(url: String, expectedHash: String): ByteArray {
        require(url.startsWith("https://"))
        require(expectedHash.matches(Regex("[a-f0-9]{40}")))
        val bytes = fetch(url)
        val actual = MessageDigest.getInstance("SHA-1").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        if (actual != expectedHash) throw SecurityException("SHA-1 verification failed.")
        return bytes
    }

    private fun fetch(url: String): ByteArray {
        require(url.startsWith("https://")) { "Refusing non-HTTPS URL." }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 60000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MyLauncher/0.1.0")
        }
        try {
            val status = connection.responseCode
            if (connection.url.protocol != "https") throw SecurityException("Redirected away from HTTPS.")
            if (status !in 200..299) error("Download returned HTTP $status")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            DigestInputStream(input, digest).use { it.readBytes() }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
