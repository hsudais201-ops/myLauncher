package dev.mylauncher

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest

data class LibraryInstallResult(val downloaded: Int, val alreadyValid: Int, val bytes: Long)

/**
 * Downloads ordinary library artifacts listed by MinecraftInstallPlanParser.
 * Platform-specific native classifiers and version rule evaluation are not yet
 * implemented, so this is a safe first step rather than a complete classpath.
 */
object MinecraftLibraryInstaller {
    suspend fun install(context: Context, libraries: List<LibraryArtifact>): LibraryInstallResult =
        withContext(Dispatchers.IO) {
            val root = File(context.filesDir, "minecraft/libraries")
            check(root.mkdirs() || root.isDirectory) { "Could not create library directory." }
            var downloaded = 0
            var valid = 0
            var bytesTotal = 0L
            for (library in libraries) {
                require(library.url.startsWith("https://")) { "Refusing non-HTTPS library URL." }
                require(library.sha1.matches(Regex("[a-f0-9]{40}"))) { "Invalid library checksum." }
                val filename = library.url.substringAfterLast('/').substringBefore('?')
                require(filename.isNotBlank() && filename.matches(Regex("[A-Za-z0-9._+-]{1,180}"))) {
                    "Unsafe library filename."
                }
                val target = File(root, "${library.sha1.take(2)}/${library.sha1}/$filename")
                val parent = target.parentFile ?: error("Invalid library path.")
                check(parent.mkdirs() || parent.isDirectory)
                if (target.isFile && sha1(target) == library.sha1) {
                    valid++
                    continue
                }
                val part = File(parent, "$filename.part")
                try {
                    val connection = (URL(library.url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 60000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "MyLauncher/0.1.0")
                    }
                    try {
                        val status = connection.responseCode
                        if (connection.url.protocol != "https") throw SecurityException("Library redirected away from HTTPS.")
                        if (status !in 200..299) error("Library download returned HTTP $status")
                        val digest = MessageDigest.getInstance("SHA-1")
                        connection.inputStream.use { input ->
                            DigestInputStream(input, digest).use { stream ->
                                part.outputStream().use { output -> stream.copyTo(output) }
                            }
                        }
                        val actual = digest.digest().joinToString("") { "%02x".format(it) }
                        if (actual != library.sha1) throw SecurityException("Library checksum mismatch.")
                    } finally {
                        connection.disconnect()
                    }
                    if (target.exists() && !target.delete()) error("Could not replace library file.")
                    check(part.renameTo(target)) { "Could not finalize library file." }
                    downloaded++
                    bytesTotal += target.length()
                } finally {
                    if (part.exists()) part.delete()
                }
            }
            LibraryInstallResult(downloaded, valid, bytesTotal)
        }

    private fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            DigestInputStream(input, digest).use { it.copyTo(java.io.OutputStream.nullOutputStream()) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
