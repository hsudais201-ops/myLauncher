package dev.mylauncher

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class ClientDownloadResult(
    val versionId: String,
    val file: File,
    val bytes: Long
)

/**
 * Downloads the official client JAR for a version and verifies Mojang's SHA-1
 * checksum before keeping it. This is only one component of installation:
 * libraries, assets, Java runtime and launch arguments are not handled here.
 */
object MinecraftVersionInstaller {
    suspend fun downloadClientJar(context: Context, version: MinecraftVersion): ClientDownloadResult =
        withContext(Dispatchers.IO) {
            val manifestConnection = (URL(version.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "MyLauncher/0.1.0")
            }

            val metadataText = try {
                if (manifestConnection.responseCode !in 200..299) {
                    throw IllegalStateException("Version metadata returned HTTP ${manifestConnection.responseCode}")
                }
                manifestConnection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                manifestConnection.disconnect()
            }

            val client = JSONObject(metadataText)
                .getJSONObject("downloads")
                .getJSONObject("client")
            val downloadUrl = client.getString("url")
            val expectedSha1 = client.getString("sha1").lowercase()
            require(downloadUrl.startsWith("https://")) { "Refusing a non-HTTPS client download URL." }
            require(expectedSha1.matches(Regex("[a-f0-9]{40}"))) { "The official client checksum is invalid." }

            val versionDir = File(context.filesDir, "minecraft/versions/${version.id}")
            check(versionDir.mkdirs() || versionDir.isDirectory) { "Could not create the version directory." }
            val destination = File(versionDir, "${version.id}.jar")
            val temporary = File(versionDir, "${version.id}.jar.part")
            val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "MyLauncher/0.1.0")
            }

            try {
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("Client download returned HTTP ${connection.responseCode}")
                }
                connection.inputStream.use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }

                val actualSha1 = MessageDigest.getInstance("SHA-1")
                    .digest(temporary.readBytes())
                    .joinToString("") { "%02x".format(it) }
                if (actualSha1 != expectedSha1) {
                    temporary.delete()
                    throw SecurityException("Client JAR checksum mismatch. The file was rejected.")
                }

                if (destination.exists() && !destination.delete()) {
                    temporary.delete()
                    throw IllegalStateException("Could not replace the previous client JAR.")
                }
                if (!temporary.renameTo(destination)) {
                    temporary.delete()
                    throw IllegalStateException("Could not finalize the verified client JAR.")
                }

                ClientDownloadResult(version.id, destination, destination.length())
            } finally {
                connection.disconnect()
                if (temporary.exists()) temporary.delete()
            }
        }
}
