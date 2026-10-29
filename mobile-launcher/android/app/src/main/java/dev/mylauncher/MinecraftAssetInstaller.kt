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
    val bytesDownloaded: Long
)

/** Downloads the official asset index and hash-addressed objects into app-private storage. */
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
            File(indexesDir, "$indexId.json").writeBytes(indexBytes)

            val objects = JSONObject(String(indexBytes, Charsets.UTF_8)).getJSONObject("objects")
            val objectsRoot = File(assetsRoot, "objects")
            var downloaded = 0
            var reused = 0
            var totalBytes = 0L
            val iterator = objects.keys()
            while (iterator.hasNext()) {
                val entry = objects.getJSONObject(iterator.next())
                val hash = entry.getString("hash").lowercase()
                require(hash.matches(Regex("[a-f0-9]{40}")))
                val folder = File(objectsRoot, hash.substring(0, 2))
                check(folder.mkdirs() || folder.isDirectory)
                val target = File(folder, hash)
                if (target.isFile && sha1(target) == hash) {
                    reused++
                    continue
                }
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
            AssetInstallResult(indexId, downloaded, reused, totalBytes)
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
