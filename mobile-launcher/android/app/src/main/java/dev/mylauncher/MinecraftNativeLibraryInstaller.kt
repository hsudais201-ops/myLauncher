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
import java.util.zip.ZipInputStream

data class NativeInstallResult(
    val extracted: Int,
    val reused: Int,
    val files: Int,
    val bytesDownloaded: Long
)

/**
 * Experimental native classifier installer for the Linux ARM64 metadata target.
 * It only extracts classifier archives declared by Mojang metadata and rejects
 * paths that could escape the native output directory.
 */
object MinecraftNativeLibraryInstaller {
    suspend fun install(context: Context, version: MinecraftVersion): NativeInstallResult =
        withContext(Dispatchers.IO) {
            require(version.url.startsWith("https://"))
            val root = JSONObject(fetchText(version.url))
            require(root.optString("id") == version.id) { "Version metadata ID mismatch." }
            val libraries = root.getJSONArray("libraries")
            val nativesRoot = File(context.filesDir, "minecraft/versions/${version.id}/natives")
            check(nativesRoot.mkdirs() || nativesRoot.isDirectory)
            var extracted = 0
            var reused = 0
            var files = 0
            var bytesDownloaded = 0L
            for (i in 0 until libraries.length()) {
                val library = libraries.getJSONObject(i)
                if (!allowedOnLinuxArm64(library)) continue
                val natives = library.optJSONObject("natives") ?: continue
                val template = natives.optString("linux")
                if (template.isBlank()) continue
                val classifiers = library.optJSONObject("downloads")?.optJSONObject("classifiers") ?: continue
                val classifierKey = template.replace("${'$'}{arch}", "64")
                val artifact = classifiers.optJSONObject(classifierKey) ?: continue
                val url = artifact.optString("url")
                val hash = artifact.optString("sha1").lowercase()
                val relativePath = artifact.optString("path")
                if (!url.startsWith("https://") || !hash.matches(Regex("[a-f0-9]{40}"))) continue
                if (!safeRelativePath(relativePath)) continue

                val exclusions = mutableSetOf<String>()
                val excludeArray = library.optJSONObject("extract")?.optJSONArray("exclude")
                if (excludeArray != null) {
                    for (j in 0 until excludeArray.length()) {
                        val value = excludeArray.optString(j)
                        if (safeRelativePath(value)) exclusions += value
                    }
                }

                val targetArchive = File(context.filesDir, "minecraft/libraries/$relativePath")
                if (!targetArchive.canonicalFile.toPath().startsWith(File(context.filesDir, "minecraft/libraries").canonicalFile.toPath())) {
                    throw SecurityException("Native archive path escapes libraries directory.")
                }
                check(targetArchive.parentFile?.mkdirs() == true || targetArchive.parentFile?.isDirectory == true)
                if (!targetArchive.isFile || sha1(targetArchive) != hash) {
                    val bytes = downloadVerified(url, hash)
                    val part = File(targetArchive.parentFile, targetArchive.name + ".part")
                    try {
                        part.writeBytes(bytes)
                        if (targetArchive.exists() && !targetArchive.delete()) error("Could not replace native archive.")
                        check(part.renameTo(targetArchive)) { "Could not finalize native archive." }
                    } finally {
                        if (part.exists()) part.delete()
                    }
                    bytesDownloaded += targetArchive.length()
                } else {
                    reused++
                }
                val extractedCount = extractSafe(targetArchive, nativesRoot, exclusions)
                if (extractedCount > 0) extracted++
                files += extractedCount
            }
            NativeInstallResult(extracted, reused, files, bytesDownloaded)
        }

    private fun allowedOnLinuxArm64(library: JSONObject): Boolean {
        val rules = library.optJSONArray("rules") ?: return true
        var allowed = false
        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue
            val os = rule.optJSONObject("os")
            if (os != null) {
                if (os.has("name") && os.optString("name") != "linux") continue
                if (os.has("arch")) {
                    val pattern = os.optString("arch")
                    val matches = try {
                        Regex(pattern).matches("aarch64") || Regex(pattern).matches("arm64")
                    } catch (_: Exception) { false }
                    if (!matches) continue
                }
            }
            val features = rule.optJSONObject("features")
            if (features != null && features.keys().asSequence().any { features.optBoolean(it) }) continue
            allowed = rule.optString("action") == "allow"
        }
        return allowed
    }

    private fun safeRelativePath(path: String): Boolean =
        path.isNotBlank() && !path.startsWith("/") && !path.startsWith("\\") &&
            !path.contains("\\") &&
            path.split("/").all { it.isNotBlank() && it != "." && it != ".." } &&
            path.matches(Regex("[A-Za-z0-9._+/-]{1,500}"))

    private fun extractSafe(archive: File, root: File, excludes: Set<String>): Int {
        var written = 0
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (!safeRelativePath(name) || excludes.any { name == it || name.startsWith("$it/") }) {
                    zip.closeEntry()
                    continue
                }
                val output = File(root, name)
                if (!output.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())) {
                    throw SecurityException("Native archive entry escapes output directory.")
                }
                if (entry.isDirectory) {
                    check(output.mkdirs() || output.isDirectory)
                } else {
                    check(output.parentFile?.mkdirs() == true || output.parentFile?.isDirectory == true)
                    val part = File(output.parentFile, output.name + ".part")
                    try {
                        part.outputStream().use { out -> zip.copyTo(out) }
                        if (output.exists() && !output.delete()) error("Could not replace native file.")
                        check(part.renameTo(output)) { "Could not finalize native file." }
                        written++
                    } finally {
                        if (part.exists()) part.delete()
                    }
                }
                zip.closeEntry()
            }
        }
        return written
    }

    private fun fetchText(url: String): String = String(fetch(url), Charsets.UTF_8)

    private fun downloadVerified(url: String, expectedHash: String): ByteArray {
        val bytes = fetch(url)
        val actual = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
        if (actual != expectedHash) throw SecurityException("Native archive SHA-1 mismatch.")
        return bytes
    }

    private fun fetch(url: String): ByteArray {
        require(url.startsWith("https://"))
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 60000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MyLauncher/0.1.0")
        }
        try {
            val status = connection.responseCode
            if (connection.url.protocol != "https") throw SecurityException("Native download redirected away from HTTPS.")
            if (status !in 200..299) error("Native download returned HTTP $status")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input -> DigestInputStream(input, digest).use { it.copyTo(java.io.OutputStream.nullOutputStream()) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
