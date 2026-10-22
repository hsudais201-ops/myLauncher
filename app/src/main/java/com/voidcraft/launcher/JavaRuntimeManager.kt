package com.voidcraft.launcher

import android.content.Context
import android.os.Build
import android.system.Os
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

private const val RUNTIME_DOWNLOAD_BASE =
    "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/jre17-ec28559/"
private const val MAX_RUNTIME_DOWNLOAD_BYTES = 100L * 1024L * 1024L
private const val MAX_RUNTIME_EXTRACTED_BYTES = 700L * 1024L * 1024L
private const val MAX_RUNTIME_ARCHIVE_ENTRIES = 250_000

private data class JavaRuntimeAsset(
    val architecture: String,
    val fileName: String,
    val expectedBytes: Long
)

private data class JavaRuntimeCheck(
    val success: Boolean,
    val message: String
)

private val java17Assets = mapOf(
    "arm" to JavaRuntimeAsset("arm", "jre17-arm-20210914-release.tar.xz", 37_378_360L),
    "arm64" to JavaRuntimeAsset("arm64", "jre17-arm64-20210825-release.tar.xz", 38_065_004L),
    "x86" to JavaRuntimeAsset("x86", "jre17-x86-20220225-release.tar.xz", 37_930_912L),
    "x86_64" to JavaRuntimeAsset("x86_64", "jre17-x86_64-20210825-release.tar.xz", 39_230_848L)
)

private fun runtimeArchitecture(): String? =
    Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi ->
        when (abi) {
            "arm64-v8a" -> "arm64"
            "armeabi-v7a", "armeabi" -> "arm"
            "x86" -> "x86"
            "x86_64" -> "x86_64"
            else -> null
        }
    }

private fun java17InstallDirectory(context: Context, architecture: String): File =
    File(File(context.filesDir, "java-runtimes"), "java17-$architecture")

@Composable
fun JavaRuntimeManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val architecture = remember { runtimeArchitecture() }
    val asset = architecture?.let { java17Assets[it] }
    val installDirectory = remember(architecture) {
        architecture?.let { java17InstallDirectory(context, it) }
    }

    var installed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf("Java runtime not verified yet. Install it to test this device's Java environment.")
    }
    var verifiedOutput by remember { mutableStateOf("") }

    LaunchedEffect(installDirectory?.absolutePath) {
        val directory = installDirectory ?: return@LaunchedEffect
        val check = withContext(Dispatchers.IO) { verifyInstalledRuntime(directory, context) }
        installed = check.success
        status = check.message
        verifiedOutput = if (check.success) check.message else ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = onBack, enabled = !busy) { Text("← BACK") }
            Column(Modifier.weight(1f)) {
                Text("Java Runtime", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                Text("Android OpenJDK • Java 17", color = VoidMuted, fontSize = 12.sp)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = VoidSurface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("DEVICE COMPATIBILITY", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Android ABI: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}", fontWeight = FontWeight.SemiBold)
                Text(
                    if (asset == null) "This device's CPU architecture is not supported by the current Java 17 package."
                    else "Matching runtime package: ${asset.fileName} (about ${asset.expectedBytes / (1024 * 1024)} MB).",
                    color = VoidMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                Button(
                    onClick = {
                        val selectedAsset = asset ?: return@Button
                        busy = true
                        status = "Downloading the Android Java 17 runtime… Keep Voidcraft open."
                        verifiedOutput = ""
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { installJava17Runtime(context, selectedAsset) }
                            }
                            result.onSuccess {
                                installed = true
                                status = "Java runtime installed and passed a java -version check."
                                verifiedOutput = it
                            }.onFailure { error ->
                                installed = false
                                status = "Runtime installation failed: ${error.message ?: "unknown error"}"
                            }
                            busy = false
                        }
                    },
                    enabled = asset != null && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (busy) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    } else {
                        Text(if (installed) "REINSTALL / REPAIR JAVA 17" else "INSTALL JAVA 17 RUNTIME")
                    }
                }
                if (installed && installDirectory != null) {
                    OutlinedButton(
                        onClick = {
                            busy = true
                            status = "Checking the installed Java runtime…"
                            scope.launch {
                                val check = withContext(Dispatchers.IO) {
                                    verifyInstalledRuntime(installDirectory, context)
                                }
                                installed = check.success
                                status = check.message
                                verifiedOutput = if (check.success) check.message else ""
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("VERIFY JAVA") }
                    OutlinedButton(
                        onClick = {
                            val removed = installDirectory.deleteRecursively()
                            installed = false
                            verifiedOutput = ""
                            status = if (removed) "Java 17 runtime removed from Voidcraft's private storage."
                            else "Could not remove the runtime directory."
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("UNINSTALL JAVA RUNTIME") }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = VoidSurface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(if (installed) "RUNTIME CHECK PASSED" else "STATUS", fontWeight = FontWeight.Bold)
                Text(status, color = VoidMuted, fontSize = 12.sp, lineHeight = 18.sp)
                if (verifiedOutput.isNotBlank()) {
                    Text(verifiedOutput.take(700), color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }

        Text(
            "Important: this is an experimental legacy Java 17 Android build from the PojavLauncher team's archived OpenJDK package release. Voidcraft checks the download size, safely extracts the archive, and runs java -version. The upstream asset has no published SHA-256 digest, so this is not a cryptographic signature check.",
            color = VoidMuted,
            fontSize = 11.sp,
            lineHeight = 17.sp
        )
        Text(
            "A Java runtime alone is not the Minecraft engine. This does not yet download Minecraft libraries/assets, provide graphics/native backends, authenticate a Microsoft account, or launch a world. Minecraft versions requiring Java 21 are not supported by this Java 17 package.",
            color = VoidMuted,
            fontSize = 11.sp,
            lineHeight = 17.sp
        )
    }
}

private fun installJava17Runtime(context: Context, asset: JavaRuntimeAsset): String {
    val connection = (URL(RUNTIME_DOWNLOAD_BASE + asset.fileName).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        requestMethod = "GET"
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Voidcraft-Android-Launcher/0.1")
        setRequestProperty("Accept", "application/octet-stream")
    }
    val archive = File(context.cacheDir, asset.fileName)
    val baseDirectory = File(context.filesDir, "java-runtimes").apply { mkdirs() }
    val stagingDirectory = File(baseDirectory, ".staging-${asset.architecture}-${System.currentTimeMillis()}")
    try {
        check(connection.responseCode in 200..299) {
            "Upstream returned HTTP ${connection.responseCode}"
        }
        val reportedLength = connection.contentLengthLong
        if (reportedLength > 0L) {
            check(reportedLength == asset.expectedBytes) { "Upstream package size changed; refusing an unreviewed runtime archive." }
        }
        connection.inputStream.use { input ->
            FileOutputStream(archive).use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= MAX_RUNTIME_DOWNLOAD_BYTES) { "Runtime download exceeded the size limit." }
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
                check(total == asset.expectedBytes) {
                    "Downloaded ${total} bytes; expected ${asset.expectedBytes}. Please retry."
                }
            }
        }

        stagingDirectory.mkdirs()
        extractSafeRuntimeArchive(archive, stagingDirectory)
        val stagedJava = findJavaExecutable(stagingDirectory)
            ?: error("Archive extracted, but bin/java was not found.")
        stagedJava.setExecutable(true, false)
        val stagedHome = stagedJava.parentFile?.parentFile
            ?: error("Could not identify the Java runtime home.")
        val stagedCheck = runJavaVersion(stagedJava, stagedHome, context)
        check(stagedCheck.success) { "The downloaded runtime could not start: ${stagedCheck.message}" }

        val finalDirectory = File(baseDirectory, "java17-${asset.architecture}")
        if (finalDirectory.exists()) {
            check(finalDirectory.deleteRecursively()) { "Could not replace the previously installed runtime." }
        }
        check(stagingDirectory.renameTo(finalDirectory)) { "Could not move the verified runtime into place." }

        val finalJava = findJavaExecutable(finalDirectory)
            ?: error("Installed runtime is missing bin/java.")
        finalJava.setExecutable(true, false)
        val finalHome = finalJava.parentFile?.parentFile
            ?: error("Installed Java home could not be located.")
        val finalCheck = runJavaVersion(finalJava, finalHome, context)
        check(finalCheck.success) { "Installed runtime failed its final check: ${finalCheck.message}" }
        return finalCheck.message
    } finally {
        connection.disconnect()
        archive.delete()
        stagingDirectory.deleteRecursively()
    }
}

private fun extractSafeRuntimeArchive(archive: File, destination: File) {
    val root = destination.canonicalFile
    var entryCount = 0
    var extractedBytes = 0L
    XZCompressorInputStream(BufferedInputStream(FileInputStream(archive))).use { xz ->
        TarArchiveInputStream(xz).use { tar ->
            var entry = tar.nextTarEntry
            while (entry != null) {
                entryCount++
                check(entryCount <= MAX_RUNTIME_ARCHIVE_ENTRIES) { "Archive contains too many entries." }
                val name = entry.name
                check(
                    name.isNotBlank() &&
                        !name.startsWith("/") &&
                        !name.startsWith("\\") &&
                        !Regex("^[A-Za-z]:").containsMatchIn(name) &&
                        !name.contains('\\')
                ) { "Archive contains an unsafe path." }

                val output = File(root, name).canonicalFile
                check(output.path == root.path || output.path.startsWith(root.path + File.separator)) {
                    "Archive attempted to write outside the runtime directory."
                }

                when {
                    entry.isDirectory -> {
                        check(output.mkdirs() || output.isDirectory) { "Could not create runtime directory." }
                        output.setReadable(true, false)
                        output.setExecutable(true, false)
                    }
                    entry.isSymbolicLink -> {
                        val target = entry.linkName
                        check(target.isNotBlank() && !target.startsWith("/") && !Regex("^[A-Za-z]:").containsMatchIn(target)) {
                            "Archive contains an absolute symbolic link."
                        }
                        val targetFile = File(output.parentFile, target).canonicalFile
                        check(targetFile.path == root.path || targetFile.path.startsWith(root.path + File.separator)) {
                            "Archive symbolic link escapes the runtime directory."
                        }
                        check(output.parentFile?.mkdirs() != false) { "Could not create symbolic-link parent directory." }
                        check(!output.exists()) { "Archive contains a duplicate path." }
                        Os.symlink(target, output.absolutePath)
                    }
                    entry.isLink -> error("Runtime archive contains an unsupported hard link.")
                    entry.isFile -> {
                        check(entry.size >= 0L && entry.size <= MAX_RUNTIME_EXTRACTED_BYTES) {
                            "Runtime archive contains an invalid file size."
                        }
                        extractedBytes += entry.size
                        check(extractedBytes <= MAX_RUNTIME_EXTRACTED_BYTES) { "Runtime archive expands beyond the safety limit." }
                        check(output.parentFile?.mkdirs() != false) { "Could not create runtime file directory." }
                        check(!output.exists()) { "Archive contains a duplicate file path." }
                        FileOutputStream(output).use { fileOutput ->
                            val buffer = ByteArray(64 * 1024)
                            var fileBytes = 0L
                            while (true) {
                                val count = tar.read(buffer)
                                if (count < 0) break
                                fileBytes += count
                                check(fileBytes <= entry.size) { "Archive entry exceeded its declared size." }
                                fileOutput.write(buffer, 0, count)
                            }
                            check(fileBytes == entry.size) { "Archive entry was truncated." }
                        }
                        output.setReadable(true, false)
                        if ((entry.mode and 0b001001001) != 0) output.setExecutable(true, false)
                    }
                    else -> error("Runtime archive contains an unsupported special file.")
                }
                entry = tar.nextTarEntry
            }
        }
    }
}

private fun findJavaExecutable(root: File): File? =
    root.walkTopDown()
        .maxDepth(8)
        .firstOrNull { it.isFile && it.name == "java" && it.parentFile?.name == "bin" }

private fun verifyInstalledRuntime(directory: File, context: Context): JavaRuntimeCheck {
    if (!directory.isDirectory) return JavaRuntimeCheck(false, "Java 17 runtime is not installed.")
    val java = findJavaExecutable(directory)
        ?: return JavaRuntimeCheck(false, "Java runtime files are incomplete. Reinstall Java 17.")
    java.setExecutable(true, false)
    val home = java.parentFile?.parentFile
        ?: return JavaRuntimeCheck(false, "Could not determine Java home.")
    return runJavaVersion(java, home, context)
}

private fun runJavaVersion(java: File, javaHome: File, context: Context): JavaRuntimeCheck {
    return try {
        val command = ProcessBuilder(java.absolutePath, "-version").redirectErrorStream(true)
        val environment = command.environment()
        val libraryDirectories = listOf(
            File(javaHome, "lib"),
            File(javaHome, "lib/server"),
            File(javaHome, "lib/client"),
            File(javaHome, "lib/jli"),
            File(javaHome, "lib/aarch64"),
            File(javaHome, "lib/aarch64/server"),
            File(javaHome, "lib/amd64"),
            File(javaHome, "lib/amd64/server")
        ).filter { it.isDirectory }.map { it.absolutePath }
        environment["JAVA_HOME"] = javaHome.absolutePath
        environment["HOME"] = context.filesDir.absolutePath
        environment["TMPDIR"] = context.cacheDir.absolutePath
        environment["LD_LIBRARY_PATH"] = libraryDirectories.joinToString(File.pathSeparator)
        environment["PATH"] = File(javaHome, "bin").absolutePath + File.pathSeparator +
            (environment["PATH"] ?: "/system/bin")
        val process = command.start()
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return JavaRuntimeCheck(false, "Java did not finish its startup check in 20 seconds.")
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        if (process.exitValue() == 0 && output.contains("version", ignoreCase = true)) {
            JavaRuntimeCheck(true, output.ifBlank { "java -version completed successfully." })
        } else {
            JavaRuntimeCheck(false, output.ifBlank { "java -version exited with code ${process.exitValue()}." })
        }
    } catch (error: Exception) {
        JavaRuntimeCheck(false, error.message ?: error.javaClass.simpleName)
    }
}
