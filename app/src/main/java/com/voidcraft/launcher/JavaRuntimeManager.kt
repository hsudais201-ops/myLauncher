package com.voidcraft.launcher

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
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
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private const val MAX_RUNTIME_DOWNLOAD_BYTES = 100L * 1024L * 1024L
private const val MAX_RUNTIME_EXTRACTED_BYTES = 700L * 1024L * 1024L
private const val MAX_RUNTIME_ARCHIVE_ENTRIES = 250_000
private const val RUNTIME_DOWNLOAD_BASE =
    "https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download"

private data class JavaRuntimeAsset(
    val majorVersion: Int,
    val architecture: String,
    val fileName: String,
    val downloadUrl: String,
    val expectedBytes: Long,
    val sha256: String
)

private data class JavaRuntimeCheck(
    val success: Boolean,
    val message: String
)

private fun packageAsset(
    majorVersion: Int,
    architecture: String,
    releaseTag: String,
    expectedBytes: Long,
    sha256: String
): JavaRuntimeAsset {
    val fileName = "jre${majorVersion}-android-${architecture}.tar.xz"
    return JavaRuntimeAsset(
        majorVersion = majorVersion,
        architecture = architecture,
        fileName = fileName,
        downloadUrl = "$RUNTIME_DOWNLOAD_BASE/$releaseTag/$fileName",
        expectedBytes = expectedBytes,
        sha256 = sha256
    )
}

private val runtimePackages = listOf(
    packageAsset(8, "arm", "download_jre8", 27035664L, "9dbee3b09af5f170e2ed9dd596bc81d0e573c88f8bf760c59c8fadbb9073d1e7"),
    packageAsset(8, "arm64", "download_jre8", 28026736L, "9a59124d9791957d55c68be664ab76831f336cf2e1e1cd4414220c6fdbf0e06d"),
    packageAsset(8, "x86", "download_jre8", 27473340L, "b96ce49fab52b28688dccc1a7d85dfc6d0f4048636aac826e1967524b28f75af"),
    packageAsset(8, "x86_64", "download_jre8", 29132884L, "b1fbcef4965c17925894febe8216d089c6dd47b37950b5f945a89616443c1d0e"),

    packageAsset(17, "arm", "download", 25001168L, "4a9134f1ebf6340dd855805d351712462cddab6f3c2684da7e7da10ccf06648d"),
    packageAsset(17, "arm64", "download", 26900804L, "e162c860fe05ee4a4e4af7606437419879f6c748386a7b09fa77d10db6a64091"),
    packageAsset(17, "x86", "download", 26951040L, "223a2d54606a9eb853c8451cf1d6bda1a8f09cb357f40b790140e57d45731ed7"),
    packageAsset(17, "x86_64", "download", 27780012L, "893e27d2aed8b40407f29fe939e2a0f193e5d55f72892a303db634b0808a2b61"),

    packageAsset(21, "arm", "download_jre21", 26586292L, "96c297487def64666e379a9a363d9955c05b1a0b091b0cf24af88359a66f394a"),
    packageAsset(21, "arm64", "download_jre21", 28675516L, "8d41ec401ee59f7722df60ed991f81ad146e130452804bfdd8a05d3436f7bbfe"),
    packageAsset(21, "x86", "download_jre21", 28689112L, "9b8c7d10c5f751acb3b33506593da44ece52a0fd03e0b3c283ba08a7f285a40f"),
    packageAsset(21, "x86_64", "download_jre21", 29662988L, "cb88723961f5f9ad63afa1f212eb199816c27cabfd7dc66567bde1d8fb69713b"),

    packageAsset(25, "arm", "download_jre25", 28252088L, "9ae13aee9cba7b2d2d8f40965061667e876d7380d866f37a992db3eff296ffb5"),
    packageAsset(25, "arm64", "download_jre25", 38031580L, "d3eb7afe2240c26728a1bb440502c5f18ac3883e932d202dd7f0c9bcbbce4c37"),
    packageAsset(25, "x86_64", "download_jre25", 39061384L, "7fca862ee1b2d5fe23cd9c9c3d9b7ad3c241947ad1a6cc9464ef2e674867105d")
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

private fun runtimeInstallDirectory(context: Context, asset: JavaRuntimeAsset): File =
    File(File(context.filesDir, "java-runtimes"), "java${asset.majorVersion}-${asset.architecture}")

private fun runtimeLabel(majorVersion: Int): String = when (majorVersion) {
    8 -> "Legacy Minecraft (generally 1.16.5 and older)"
    17 -> "Minecraft 1.17–1.20.4"
    21 -> "Modern Minecraft 1.20.5+"
    else -> "Advanced / test builds only — confirm game and mod compatibility"
}

@Composable
fun JavaRuntimeManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val architecture = remember { runtimeArchitecture() }
    val assets = remember(architecture) {
        runtimePackages.filter { it.architecture == architecture }.sortedBy { it.majorVersion }
    }

    var installedVersions by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var statusByVersion by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var outputByVersion by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var busyVersion by remember { mutableStateOf<Int?>(null) }
    var generalStatus by remember { mutableStateOf("Checking installed runtimes…") }

    LaunchedEffect(architecture) {
        if (architecture == null) {
            generalStatus = "This device's CPU architecture is not supported."
        } else {
            val checks = withContext(Dispatchers.IO) {
                assets.associate { asset ->
                    asset.majorVersion to verifyInstalledRuntime(runtimeInstallDirectory(context, asset), context)
                }
            }
            installedVersions = checks.filterValues { it.success }.keys
            statusByVersion = checks.mapValues { it.value.message }
            outputByVersion = checks.filterValues { it.success }.mapValues { it.value.message }
            generalStatus = "Detected architecture: ${Build.SUPPORTED_ABIS.firstOrNull() ?: architecture}."
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = onBack, enabled = busyVersion == null) { Text("← BACK") }
            Column(Modifier.weight(1f)) {
                Text("Java Runtime Manager", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                Text("Android OpenJDK • multiple versions", color = VoidMuted, fontSize = 12.sp)
            }
        }

        Button(
            onClick = {
                try {
                    context.startActivity(Intent().setClassName(context, "net.kdt.pojavlaunch.LauncherActivity"))
                } catch (_: ActivityNotFoundException) {
                    generalStatus = "The integrated game engine is not included in this standalone build. These verified runtimes remain in a separate test directory."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("OPEN GAME ENGINE & RUNTIME SETTINGS") }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = VoidSurface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("DEVICE COMPATIBILITY", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Android ABI: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}", fontWeight = FontWeight.SemiBold)
                Text(
                    if (architecture == null) "No compatible runtime package is listed for this architecture."
                    else "Packages are downloaded on demand, stored in Voidcraft's private app directory, checked against the upstream SHA-256 digest, safely extracted, and started with java -version.",
                    color = VoidMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                Text(generalStatus, color = VoidMuted, fontSize = 11.sp)
            }
        }

        assets.forEach { asset ->
            val isInstalled = asset.majorVersion in installedVersions
            val isBusy = busyVersion == asset.majorVersion
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = VoidSurface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Java ${asset.majorVersion}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                            Text(runtimeLabel(asset.majorVersion), color = VoidMuted, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                        Text(
                            if (isInstalled) "INSTALLED" else "NOT INSTALLED",
                            color = if (isInstalled) MaterialTheme.colorScheme.primary else VoidMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "${asset.expectedBytes / (1024 * 1024)} MB download • ${asset.architecture} • SHA-256 verified",
                        color = VoidMuted,
                        fontSize = 10.sp
                    )
                    Button(
                        onClick = {
                            busyVersion = asset.majorVersion
                            statusByVersion = statusByVersion + (asset.majorVersion to "Downloading and verifying Java ${asset.majorVersion}… Keep Voidcraft open.")
                            outputByVersion = outputByVersion - asset.majorVersion
                            scope.launch {
                                val (result, previousCheck) = withContext(Dispatchers.IO) {
                                    val attempt = runCatching { installJavaRuntime(context, asset) }
                                    val previous = if (attempt.isFailure) {
                                        verifyInstalledRuntime(runtimeInstallDirectory(context, asset), context)
                                    } else {
                                        null
                                    }
                                    Pair(attempt, previous)
                                }
                                result.onSuccess { output ->
                                    installedVersions = installedVersions + asset.majorVersion
                                    statusByVersion = statusByVersion + (asset.majorVersion to "Installed and verified successfully.")
                                    outputByVersion = outputByVersion + (asset.majorVersion to output)
                                }.onFailure { error ->
                                    val previous = previousCheck
                                        ?: JavaRuntimeCheck(false, "No previously installed runtime could be verified.")
                                    installedVersions = if (previous.success) {
                                        installedVersions + asset.majorVersion
                                    } else {
                                        installedVersions - asset.majorVersion
                                    }
                                    outputByVersion = if (previous.success) {
                                        outputByVersion + (asset.majorVersion to previous.message)
                                    } else {
                                        outputByVersion - asset.majorVersion
                                    }
                                    val errorMessage = error.message ?: "unknown error"
                                    statusByVersion = statusByVersion + (
                                        asset.majorVersion to if (previous.success) {
                                            "Update failed, but the previous Java ${asset.majorVersion} runtime is still working. $errorMessage"
                                        } else {
                                            "Install failed: $errorMessage"
                                        }
                                    )
                                }
                                busyVersion = null
                            }
                        },
                        enabled = busyVersion == null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isBusy) CircularProgressIndicator(strokeWidth = 2.dp)
                        else Text(if (isInstalled) "REINSTALL / REPAIR" else "INSTALL JAVA ${asset.majorVersion}")
                    }

                    if (isInstalled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    busyVersion = asset.majorVersion
                                    scope.launch {
                                        val check = withContext(Dispatchers.IO) {
                                            verifyInstalledRuntime(runtimeInstallDirectory(context, asset), context)
                                        }
                                        statusByVersion = statusByVersion + (asset.majorVersion to check.message)
                                        outputByVersion = if (check.success) outputByVersion + (asset.majorVersion to check.message)
                                            else outputByVersion - asset.majorVersion
                                        installedVersions = if (check.success) installedVersions + asset.majorVersion
                                            else installedVersions - asset.majorVersion
                                        busyVersion = null
                                    }
                                },
                                enabled = busyVersion == null,
                                modifier = Modifier.weight(1f)
                            ) { Text("VERIFY") }

                            OutlinedButton(
                                onClick = {
                                    busyVersion = asset.majorVersion
                                    scope.launch {
                                        val removed = withContext(Dispatchers.IO) {
                                            runtimeInstallDirectory(context, asset).deleteRecursively()
                                        }
                                        installedVersions = installedVersions - asset.majorVersion
                                        outputByVersion = outputByVersion - asset.majorVersion
                                        statusByVersion = statusByVersion + (asset.majorVersion to
                                            if (removed) "Java ${asset.majorVersion} removed from private storage."
                                            else "Could not remove Java ${asset.majorVersion}.")
                                        busyVersion = null
                                    }
                                },
                                enabled = busyVersion == null,
                                modifier = Modifier.weight(1f)
                            ) { Text("UNINSTALL") }
                        }
                    }

                    val runtimeStatus = statusByVersion[asset.majorVersion]
                    if (!runtimeStatus.isNullOrBlank()) {
                        Text(runtimeStatus, color = VoidMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    val runtimeOutput = outputByVersion[asset.majorVersion]
                    if (!runtimeOutput.isNullOrBlank()) {
                        Text(runtimeOutput.take(500), color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, lineHeight = 15.sp)
                    }
                }
            }
        }

        if (architecture == "x86") {
            Text(
                "Java 25 is not available from this Android runtime source for 32-bit x86. Java 21 remains available for compatible modern versions.",
                color = VoidMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }

        Text(
            "Java version choice depends on Minecraft and mod-loader requirements. Java 21 is the normal target for Minecraft 1.20.5 and newer; Java 25 is an advanced option and is not a safe default for every game or mod. Installing Java does not itself install Minecraft or make the Play button launch the game.",
            color = VoidMuted,
            fontSize = 11.sp,
            lineHeight = 17.sp
        )
    }
}

private fun installJavaRuntime(context: Context, asset: JavaRuntimeAsset): String {
    val connection = (URL(asset.downloadUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        requestMethod = "GET"
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Voidcraft-Android-Launcher/0.1")
        setRequestProperty("Accept", "application/octet-stream")
    }
    val archive = File(context.cacheDir, asset.fileName)
    val baseDirectory = File(context.filesDir, "java-runtimes").apply {
        check(mkdirs() || isDirectory) { "Could not create the Java runtime folder." }
    }
    val stagingDirectory = File(
        baseDirectory,
        ".staging-java${asset.majorVersion}-${asset.architecture}-${System.currentTimeMillis()}"
    )

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

        val calculatedDigest = sha256File(archive)
        check(calculatedDigest.equals(asset.sha256, ignoreCase = true)) {
            "SHA-256 verification failed; the archive was not installed."
        }

        check(stagingDirectory.mkdirs() || stagingDirectory.isDirectory) { "Could not create a temporary runtime folder." }
        extractSafeRuntimeArchive(archive, stagingDirectory)
        val stagedJava = findJavaExecutable(stagingDirectory)
            ?: error("Archive extracted, but bin/java was not found.")
        stagedJava.setExecutable(true, false)
        val stagedHome = stagedJava.parentFile?.parentFile
            ?: error("Could not identify the Java runtime home.")
        val stagedCheck = runJavaVersion(stagedJava, stagedHome, context)
        check(stagedCheck.success) { "The downloaded runtime could not start: ${stagedCheck.message}" }

        val finalDirectory = runtimeInstallDirectory(context, asset)
        val backupDirectory = File(
            baseDirectory,
            ".backup-java${asset.majorVersion}-${asset.architecture}-${System.currentTimeMillis()}"
        )
        var backedUpPreviousInstall = false
        if (finalDirectory.exists()) {
            check(finalDirectory.renameTo(backupDirectory)) {
                "Could not safely move the previous runtime aside."
            }
            backedUpPreviousInstall = true
        }
        if (!stagingDirectory.renameTo(finalDirectory)) {
            if (backedUpPreviousInstall) backupDirectory.renameTo(finalDirectory)
            error("Could not move the verified runtime into place; previous runtime was restored when possible.")
        }

        val finalJava = findJavaExecutable(finalDirectory)
        val finalHome = finalJava?.parentFile?.parentFile
        val finalCheck = if (finalJava != null && finalHome != null) {
            finalJava.setExecutable(true, false)
            runJavaVersion(finalJava, finalHome, context)
        } else {
            JavaRuntimeCheck(false, "Installed runtime is missing bin/java.")
        }
        if (!finalCheck.success) {
            finalDirectory.deleteRecursively()
            if (backedUpPreviousInstall) backupDirectory.renameTo(finalDirectory)
            error("Installed runtime failed its final check: ${finalCheck.message}")
        }
        if (backedUpPreviousInstall) backupDirectory.deleteRecursively()
        return finalCheck.message
    } finally {
        connection.disconnect()
        archive.delete()
        stagingDirectory.deleteRecursively()
    }
}

private fun sha256File(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xff)
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
                        check(output.parentFile?.let { it.mkdirs() || it.isDirectory } == true) {
                            "Could not create symbolic-link parent directory."
                        }
                        check(!output.exists()) { "Archive contains a duplicate path." }
                        Os.symlink(target, output.absolutePath)
                    }
                    entry.isLink -> {
                        val targetName = entry.linkName
                        check(
                            targetName.isNotBlank() &&
                                !targetName.startsWith("/") &&
                                !targetName.startsWith("\\") &&
                                !Regex("^[A-Za-z]:").containsMatchIn(targetName) &&
                                !targetName.contains('\\')
                        ) { "Archive contains an unsafe hard-link target." }
                        val target = File(root, targetName).canonicalFile
                        check(target.path.startsWith(root.path + File.separator)) {
                            "Archive hard link escapes the runtime directory."
                        }
                        check(target.isFile) {
                            "Archive hard link target has not been extracted as a regular file."
                        }
                        check(output.parentFile?.let { it.mkdirs() || it.isDirectory } == true) {
                            "Could not create hard-link parent directory."
                        }
                        check(!output.exists()) { "Archive contains a duplicate hard-link path." }
                        Os.link(target.absolutePath, output.absolutePath)
                    }
                    entry.isFile -> {
                        check(entry.size >= 0L && entry.size <= MAX_RUNTIME_EXTRACTED_BYTES) {
                            "Runtime archive contains an invalid file size."
                        }
                        extractedBytes += entry.size
                        check(extractedBytes <= MAX_RUNTIME_EXTRACTED_BYTES) { "Runtime archive expands beyond the safety limit." }
                        check(output.parentFile?.let { it.mkdirs() || it.isDirectory } == true) {
                            "Could not create runtime file directory."
                        }
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
    if (!directory.isDirectory) return JavaRuntimeCheck(false, "Java runtime is not installed.")
    val java = findJavaExecutable(directory)
        ?: return JavaRuntimeCheck(false, "Java runtime files are incomplete. Reinstall this runtime.")
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
