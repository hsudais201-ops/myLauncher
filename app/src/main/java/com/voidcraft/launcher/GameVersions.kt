package com.voidcraft.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val MOJANG_VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
private const val MAX_VERSION_MANIFEST_BYTES = 10 * 1024 * 1024
private const val MAX_CLIENT_JAR_BYTES = 200L * 1024L * 1024L
private const val MIN_DOWNLOAD_FREE_SPACE_BYTES = 16L * 1024L * 1024L

private data class MinecraftVersionEntry(
    val id: String,
    val type: String,
    val releaseTime: String,
    val metadataUrl: String,
    val metadataSha1: String
)

private data class MinecraftVersionDetails(
    val id: String,
    val metadataUrl: String,
    val metadataSha1: String,
    val javaComponent: String?,
    val javaMajorVersion: Int?,
    val clientJarUrl: String?,
    val clientJarSha1: String?,
    val clientJarSizeBytes: Long?,
    val assetIndexId: String?,
    val assetIndexUrl: String?,
    val libraryCount: Int,
    val releaseTime: String
)

private data class MinecraftVersionManifest(
    val latestRelease: String,
    val latestSnapshot: String,
    val versions: List<MinecraftVersionEntry>
)

@Composable
fun GameVersionsScreen(
    selectedVersion: String,
    onSelectVersion: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var downloadingVersion by remember { mutableStateOf<String?>(null) }
    var downloadMessage by remember { mutableStateOf<String?>(null) }
    var versions by remember { mutableStateOf<List<MinecraftVersionEntry>>(emptyList()) }
    var latestRelease by remember { mutableStateOf("") }
    var latestSnapshot by remember { mutableStateOf("") }
    var showSnapshots by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var inspectingId by remember { mutableStateOf<String?>(null) }
    var inspectedDetails by remember { mutableStateOf<MinecraftVersionDetails?>(null) }
    var inspectionError by remember { mutableStateOf<String?>(null) }
    var pendingAssetDetails by remember { mutableStateOf<MinecraftVersionDetails?>(null) }
    var pendingFullPrepareDetails by remember { mutableStateOf<MinecraftVersionDetails?>(null) }
    var status by remember { mutableStateOf("Connect to the internet to load Mojang's official version manifest.") }
    var engineLaunchStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun openEngineLauncher() {
        try {
            context.startActivity(Intent().setClassName(context, "net.kdt.pojavlaunch.LauncherActivity"))
            engineLaunchStatus = "Opened the integrated engine launcher. Complete its official sign-in, Java/runtime setup, version installation, and launch there."
        } catch (_: ActivityNotFoundException) {
            engineLaunchStatus = "The full game installer is included only in the combined engine APK. This standalone Voidcraft build can inspect/download metadata but cannot launch Minecraft."
        }
    }

    fun refresh() {
        scope.launch {
            loading = true
            status = "Fetching the official Minecraft version catalog…"
            val result = withContext(Dispatchers.IO) {
                runCatching { fetchMinecraftVersions() }
            }
            result.onSuccess { manifest ->
                versions = manifest.versions
                latestRelease = manifest.latestRelease
                latestSnapshot = manifest.latestSnapshot
                status = "Loaded ${manifest.versions.size} versions from Mojang."
            }.onFailure { error ->
                status = "Could not load versions: ${error.message ?: "network error"}. Check your connection and retry."
            }
            loading = false
        }
    }

    fun inspect(entry: MinecraftVersionEntry) {
        scope.launch {
            inspectingId = entry.id
            inspectedDetails = null
            inspectionError = null
            val result = withContext(Dispatchers.IO) {
                runCatching { fetchMinecraftVersionDetails(context, entry) }
            }
            result.onSuccess { inspectedDetails = it }
                .onFailure { error ->
                    inspectionError = "Could not inspect ${entry.id}: ${error.message ?: "metadata error"}."
                }
            inspectingId = null
        }
    }

    fun prepareFullVersion(details: MinecraftVersionDetails) {
        onSelectVersion(details.id)
        scope.launch {
            downloadingVersion = details.id
            downloadMessage = "Preparing ${details.id}: checking official client metadata…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // Each stage validates the official metadata and its expected digest before saving.
                    val client = downloadAndVerifyMinecraftClientJar(context, details)
                    Handler(Looper.getMainLooper()).post {
                        downloadMessage = "Client JAR verified. Preparing libraries…"
                    }
                    val libraries = downloadMinecraftLibraries(
                        context, details.metadataUrl, details.metadataSha1
                    ) { message ->
                        Handler(Looper.getMainLooper()).post { downloadMessage = message }
                    }
                    val assets = downloadMinecraftAssets(
                        context, details.metadataUrl, details.metadataSha1
                    ) { message ->
                        Handler(Looper.getMainLooper()).post { downloadMessage = message }
                    }
                    Triple(client, libraries, assets)
                }
            }
            result.onSuccess { prepared ->
                downloadMessage =
                    "Prepared ${details.id}: client JAR verified, ${prepared.second} library JARs and ${prepared.third} asset objects verified. Open the integrated engine to finish profile/runtime setup and launch."
            }.onFailure { error ->
                downloadMessage =
                    "Preparation stopped safely: ${error.message ?: "unknown download or verification error"}. Successfully verified files were kept; reopen this version and retry."
            }
            downloadingVersion = null
        }
    }

    fun downloadClientJar(details: MinecraftVersionDetails) {
        scope.launch {
            downloadingVersion = details.id
            downloadMessage = "Downloading and verifying ${details.id} client JAR…"
            val result = withContext(Dispatchers.IO) {
                runCatching { downloadAndVerifyMinecraftClientJar(context, details) }
            }
            result.onSuccess { file ->
                downloadMessage = "Client JAR verified and saved in the configured game directory: ${file.absolutePath}"
            }.onFailure { error ->
                downloadMessage = "Client JAR download failed: ${error.message ?: "unknown error"}"
            }
            downloadingVersion = null
        }
    }

    fun downloadLibraries(details: MinecraftVersionDetails) {
        scope.launch {
            downloadingVersion = details.id
            downloadMessage = "Preparing library downloads…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    downloadMinecraftLibraries(context, details.metadataUrl, details.metadataSha1) { message ->
                        Handler(Looper.getMainLooper()).post { downloadMessage = message }
                    }
                }
            }
            result.onSuccess { count -> downloadMessage = "Verified $count library JAR files." }
                .onFailure { error -> downloadMessage = "Library download failed: ${error.message ?: "unknown error"}" }
            downloadingVersion = null
        }
    }

    fun downloadAssets(details: MinecraftVersionDetails) {
        scope.launch {
            downloadingVersion = details.id
            downloadMessage = "Preparing asset index and downloads…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    downloadMinecraftAssets(context, details.metadataUrl, details.metadataSha1) { message ->
                        Handler(Looper.getMainLooper()).post { downloadMessage = message }
                    }
                }
            }
            result.onSuccess { count -> downloadMessage = "Downloaded and verified $count game asset objects." }
                .onFailure { error -> downloadMessage = "Asset download failed: ${error.message ?: "unknown error"}" }
            downloadingVersion = null
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Game versions", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "Browse official Minecraft Java version metadata and save the version you plan to use.",
            color = VoidMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = VoidSurface)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("LATEST OFFICIAL BUILDS", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(if (latestRelease.isBlank()) "Release: not loaded" else "Latest release: $latestRelease", fontWeight = FontWeight.SemiBold)
                Text(if (latestSnapshot.isBlank()) "Snapshot: not loaded" else "Latest snapshot: $latestSnapshot", color = VoidMuted, fontSize = 12.sp)
                Text("Selected profile version: $selectedVersion", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { refresh() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    if (loading) CircularProgressIndicator(strokeWidth = 2.dp)
                    else Text("REFRESH VERSION CATALOG")
                }
                Button(onClick = { openEngineLauncher() }, modifier = Modifier.fillMaxWidth()) {
                    Text("OPEN FULL ENGINE INSTALLER & PLAY")
                }
                engineLaunchStatus?.let {
                    Text(it, color = VoidMuted, fontSize = 11.sp, lineHeight = 16.sp)
                }
                Text(
                    "The integrated engine is responsible for the full install, account authentication, Java registration, native renderer setup, and game launch. The actions below prepare files only.",
                    color = VoidMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        inspectedDetails?.let { details ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = VoidSurface)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text("VERSION INSTALL PREFLIGHT", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(details.id, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Required Java: ${details.javaMajorVersion?.toString() ?: "not specified"}${details.javaComponent?.let { " ($it)" } ?: ""}",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("Client JAR: ${if (details.clientJarUrl.isNullOrBlank()) "not listed" else "available in metadata"}", color = VoidMuted, fontSize = 12.sp)
                    details.clientJarSizeBytes?.let {
                        Text("Client JAR size: %.2f MiB".format(it / (1024.0 * 1024.0)), color = VoidMuted, fontSize = 12.sp)
                    }
                    details.clientJarSha1?.let {
                        Text("Client JAR SHA-1: $it", color = VoidMuted, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                    Text("Asset index: ${details.assetIndexId ?: "not listed"}", color = VoidMuted, fontSize = 12.sp)
                    Text("Libraries listed: ${details.libraryCount} entries", color = VoidMuted, fontSize = 12.sp)
                    Text("Release time: ${details.releaseTime.ifBlank { "not listed" }}", color = VoidMuted, fontSize = 12.sp)
                    details.assetIndexUrl?.let {
                        Text("Asset index URL: $it", color = VoidMuted, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                    Text(
                        "The installer can fetch the client JAR, compatible library JARs, and every verified asset object. This still does not include Android LWJGL natives, Microsoft sign-in, or the game launch pipeline.",
                        color = VoidMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    Button(
                        onClick = { pendingFullPrepareDetails = details },
                        enabled = downloadingVersion == null &&
                            !details.clientJarUrl.isNullOrBlank() &&
                            !details.clientJarSha1.isNullOrBlank() &&
                            details.clientJarSizeBytes != null &&
                            details.assetIndexUrl?.isNotBlank() == true,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (downloadingVersion == details.id) "PREPARING VERSION…" else "PREPARE FULL VERSION FILES")
                    }
                    Button(
                        onClick = { downloadLibraries(details) },
                        enabled = downloadingVersion == null,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("DOWNLOAD & VERIFY LIBRARIES") }
                    Button(
                        onClick = { pendingAssetDetails = details },
                        enabled = downloadingVersion == null && !details.assetIndexUrl.isNullOrBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("DOWNLOAD ALL GAME ASSETS") }
                    Button(
                        onClick = { downloadClientJar(details) },
                        enabled = downloadingVersion == null &&
                            !details.clientJarUrl.isNullOrBlank() &&
                            !details.clientJarSha1.isNullOrBlank() &&
                            details.clientJarSizeBytes != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (downloadingVersion == details.id) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        } else {
                            Text("DOWNLOAD & VERIFY CLIENT JAR")
                        }
                    }
                    if (downloadingVersion == details.id) {
                        Text("Downloading to Voidcraft's private storage. Keep the app open until verification finishes.", color = VoidMuted, fontSize = 11.sp)
                    }
                    downloadMessage?.let { message ->
                        Text(
                            message,
                            color = if (message.contains("failed", ignoreCase = true)) MaterialTheme.colorScheme.error else VoidMuted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                    OutlinedButton(onClick = { inspectedDetails = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("CLOSE INSPECTOR")
                    }
                }
            }
        }
        inspectionError?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, lineHeight = 17.sp)
        }
        pendingFullPrepareDetails?.let { pending ->
            AlertDialog(
                onDismissRequest = { pendingFullPrepareDetails = null },
                title = { Text("Prepare Minecraft ${pending.id}?") },
                text = {
                    Text(
                        "Voidcraft will download and verify the official client JAR, supported library JARs, the asset index, and referenced game assets. This may use hundreds of megabytes or several gigabytes of storage and mobile data. Use Wi-Fi and keep the app open. Nothing starts until you confirm. This prepares files only; the integrated engine still performs account sign-in, runtime/profile resolution, renderer setup, and actual launch."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingFullPrepareDetails = null
                            prepareFullVersion(pending)
                        }
                    ) { Text("PREPARE FILES") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingFullPrepareDetails = null }) { Text("CANCEL") }
                }
            )
        }

        pendingAssetDetails?.let { pending ->
            AlertDialog(
                onDismissRequest = { pendingAssetDetails = null },
                title = { Text("Download all game assets?") },
                text = {
                    Text(
                        "This can download hundreds of megabytes or several gigabytes and use mobile data. " +
                            "Voidcraft checks the official asset index, available storage, object sizes, and SHA-1 hashes. " +
                            "Use Wi-Fi and enough free space. Nothing downloads until you confirm."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingAssetDetails = null
                            downloadAssets(pending)
                        }
                    ) { Text("DOWNLOAD ASSETS") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingAssetDetails = null }) { Text("CANCEL") }
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Include snapshots & legacy versions", fontWeight = FontWeight.SemiBold)
                Text("Snapshots and old alpha/beta builds may be unstable.", color = VoidMuted, fontSize = 11.sp)
            }
            Switch(checked = showSnapshots, onCheckedChange = { showSnapshots = it })
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it.take(40) },
            label = { Text("Search versions, e.g. 1.12.2 or 26.x") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (searchQuery.isBlank() && versions.size > 25) {
            Text("Showing the 25 newest matching versions. Search by exact version to find older releases.", color = VoidMuted, fontSize = 11.sp, lineHeight = 16.sp)
        }

        if (versions.isEmpty() && loading) {
            CircularProgressIndicator()
        } else if (versions.isEmpty()) {
            Text(status, color = VoidMuted, fontSize = 12.sp, lineHeight = 18.sp)
        } else {
            val matchingVersions = versions.filter {
                (showSnapshots || it.type == "release") &&
                    (searchQuery.isBlank() || it.id.contains(searchQuery.trim(), ignoreCase = true))
            }
            val visibleVersions = if (searchQuery.isBlank()) matchingVersions.take(25) else matchingVersions.take(100)
            if (visibleVersions.isEmpty()) {
                Text("No versions match this search and filter.", color = VoidMuted, fontSize = 12.sp)
            }
            visibleVersions.forEach { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = VoidSurface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.id, fontWeight = FontWeight.Bold)
                            Text(entry.type.uppercase(), color = VoidMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            if (entry.releaseTime.isNotBlank()) {
                                Text(entry.releaseTime.take(10), color = VoidMuted, fontSize = 11.sp)
                            }
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(onClick = { onSelectVersion(entry.id) }) {
                                Text(if (selectedVersion == entry.id) "SELECTED" else "SELECT")
                            }
                            OutlinedButton(
                                onClick = { inspect(entry) },
                                enabled = inspectingId == null
                            ) {
                                if (inspectingId == entry.id) {
                                    CircularProgressIndicator(strokeWidth = 2.dp)
                                } else {
                                    Text("INSPECT")
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(status, color = VoidMuted, fontSize = 11.sp, lineHeight = 16.sp)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = VoidSurface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Java runtimes available; game engine still pending", fontWeight = FontWeight.Bold)
                Text(
                    "Selecting a version saves your preference. This screen can download and verify the official client JAR, compatible library JAR artifacts, the asset index, and all referenced asset objects in private app storage. It still does not provide the Android LWJGL/native compatibility engine, Microsoft authentication, inherited/loader-specific version resolution, or an actual game launch. Choose a Java runtime compatible with the selected version.",
                    color = VoidMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

private fun fetchMinecraftVersions(): MinecraftVersionManifest {
    val connection = (URL(MOJANG_VERSION_MANIFEST).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 12000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "Voidcraft-Android-Launcher/0.1")
    }
    try {
        if (connection.responseCode !in 200..299) {
            error("Mojang returned HTTP ${connection.responseCode}")
        }
        val finalUrl = connection.url
        require(isApprovedMinecraftVersionManifestUrl(finalUrl)) {
            "Version catalog redirected outside Mojang's approved HTTPS host"
        }
        val contentLength = connection.getHeaderFieldLong("Content-Length", -1L)
        if (contentLength > MAX_VERSION_MANIFEST_BYTES) {
            error("Mojang version catalog exceeds the 10 MiB safety limit")
        }
        val bodyBytes = connection.inputStream.use { input ->
            val buffer = ByteArray(8192)
            val output = ByteArrayOutputStream()
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_VERSION_MANIFEST_BYTES) {
                    "Mojang version catalog exceeds the 10 MiB safety limit"
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val body = bodyBytes.toString(Charsets.UTF_8)
        val root = JSONObject(body)
        val latest = root.optJSONObject("latest") ?: JSONObject()
        val rows = root.optJSONArray("versions")
        val parsed = buildList {
            if (rows != null) {
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    val id = row.optString("id").trim()
                    val type = row.optString("type").trim()
                    if (id.isNotBlank() && type in setOf("release", "snapshot", "old_beta", "old_alpha")) {
                        add(MinecraftVersionEntry(id, type, row.optString("releaseTime"), row.optString("url"), row.optString("sha1")))
                    }
                }
            }
        }
        return MinecraftVersionManifest(
            latestRelease = latest.optString("release"),
            latestSnapshot = latest.optString("snapshot"),
            versions = parsed
        )
    } finally {
        connection.disconnect()
    }
}
 
private fun fetchMinecraftVersionDetails(context: Context, entry: MinecraftVersionEntry): MinecraftVersionDetails {
    require(entry.metadataUrl.isNotBlank()) { "Mojang manifest did not provide a version metadata URL" }
    require(isValidMinecraftSha1(entry.metadataSha1)) {
        "Mojang version catalog did not provide a valid version metadata SHA-1"
    }
    val metadataUrl = URL(entry.metadataUrl)
    require(isApprovedMinecraftVersionMetadataUrl(metadataUrl)) {
        "Unexpected metadata host; only Mojang version metadata is accepted"
    }

    val connection = (metadataUrl.openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 15000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "Voidcraft-Android-Launcher/0.1")
    }
    try {
        if (connection.responseCode !in 200..299) {
            error("Mojang returned HTTP ${connection.responseCode}")
        }
        val finalUrl = connection.url
        require(isApprovedMinecraftVersionMetadataUrl(finalUrl)) {
            "Version metadata redirected outside Mojang's approved HTTPS hosts"
        }
        val contentLength = connection.getHeaderFieldLong("Content-Length", -1L)
        if (contentLength > 10L * 1024L * 1024L) {
            error("Version metadata exceeds the 10 MiB safety limit")
        }
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
        val bodySha1 = sha1OfBytes(bodyBytes)
        require(bodySha1.equals(entry.metadataSha1, ignoreCase = true)) {
            "Version JSON SHA-1 does not match Mojang's version manifest"
        }
        val body = bodyBytes.toString(Charsets.UTF_8)
        val root = JSONObject(body)
        val versionId = root.optString("id", entry.id).ifBlank { entry.id }
        require(versionId == entry.id) { "Version JSON ID does not match the selected version" }
        val safeVersionId = versionId.takeIf { it.matches(Regex("^[A-Za-z0-9._-]{1,100}$")) }
            ?: error("Invalid version ID in Mojang metadata")
        val versionDirectory = File(resolveEngineStorageDirectory(context, "DIR_HOME_VERSION", "minecraft/versions"), safeVersionId)
        require(versionDirectory.isDirectory || versionDirectory.mkdirs()) { "Could not create version metadata folder" }
        val jsonFile = File(versionDirectory, "$safeVersionId.json")
        val jsonTemporary = File(versionDirectory, "$safeVersionId.json.part")
        java.io.FileOutputStream(jsonTemporary).use { output ->
            output.write(bodyBytes)
            output.fd.sync()
        }
        if (jsonFile.exists()) require(jsonFile.delete()) { "Could not replace cached version metadata" }
        require(jsonTemporary.renameTo(jsonFile)) { "Could not finalize verified version metadata" }

        val javaVersion = root.optJSONObject("javaVersion")
        val downloads = root.optJSONObject("downloads")
        val client = downloads?.optJSONObject("client")
        val assetIndex = root.optJSONObject("assetIndex")
        val libraries = root.optJSONArray("libraries")
        return MinecraftVersionDetails(
            id = versionId,
            metadataUrl = entry.metadataUrl,
            metadataSha1 = entry.metadataSha1,
            javaComponent = javaVersion?.optString("component")?.takeIf { it.isNotBlank() },
            javaMajorVersion = javaVersion?.takeIf { it.has("majorVersion") && !it.isNull("majorVersion") }?.optInt("majorVersion"),
            clientJarUrl = client?.optString("url")?.takeIf { it.isNotBlank() },
            clientJarSha1 = client?.optString("sha1")?.takeIf { it.isNotBlank() },
            clientJarSizeBytes = client?.takeIf { it.has("size") && !it.isNull("size") }?.optLong("size"),
            assetIndexId = assetIndex?.optString("id")?.takeIf { it.isNotBlank() },
            assetIndexUrl = assetIndex?.optString("url")?.takeIf { it.isNotBlank() },
            libraryCount = libraries?.length() ?: 0,
            releaseTime = root.optString("releaseTime", entry.releaseTime)
        )
    } finally {
        connection.disconnect()
    }
}
 
private fun downloadAndVerifyMinecraftClientJar(
    context: Context,
    details: MinecraftVersionDetails
): File {
    val clientUrlText = details.clientJarUrl ?: error("Mojang metadata does not list a client JAR URL")
    val expectedSha1 = details.clientJarSha1?.trim()?.lowercase()
        ?: error("Mojang metadata does not list a client JAR SHA-1")
    require(isValidMinecraftSha1(expectedSha1)) { "Invalid client JAR SHA-1 in metadata" }
    val expectedSize = details.clientJarSizeBytes ?: error("Mojang metadata does not list the client JAR size")
    require(expectedSize in 1L..MAX_CLIENT_JAR_BYTES) { "Client JAR size is outside Voidcraft's safety limit" }

    val allowedHosts = setOf("piston-data.mojang.com", "launcher.mojang.com")
    val clientUrl = URL(clientUrlText)
    require(clientUrl.protocol == "https" && clientUrl.host.lowercase() in allowedHosts) {
        "Unexpected client JAR host; only official Mojang download hosts are accepted"
    }

    val safeVersionId = details.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
    require(safeVersionId.isNotBlank() && safeVersionId != "." && safeVersionId != "..") {
        "Invalid version identifier"
    }
    val versionDirectory = File(resolveEngineStorageDirectory(context, "DIR_HOME_VERSION", "minecraft/versions"), safeVersionId)
    require(versionDirectory.isDirectory || versionDirectory.mkdirs()) { "Could not create the version folder" }
    val destination = File(versionDirectory, "$safeVersionId.jar")
    val temporary = File(versionDirectory, "$safeVersionId.jar.part")

    if (destination.isFile && destination.length() == expectedSize &&
        sha1OfFile(destination).equals(expectedSha1, ignoreCase = true)
    ) {
        return destination
    }

    val freeSpace = StatFs(versionDirectory.absolutePath).availableBytes
    require(freeSpace >= expectedSize + MIN_DOWNLOAD_FREE_SPACE_BYTES) {
        "Not enough free storage in the configured game directory for this client JAR"
    }

    temporary.delete()
    val connection = (clientUrl.openConnection() as HttpURLConnection).apply {
        connectTimeout = 10000
        readTimeout = 30000
        requestMethod = "GET"
        instanceFollowRedirects = true
        setRequestProperty("Accept", "application/java-archive, application/octet-stream, */*")
        setRequestProperty("User-Agent", "Voidcraft-Android-Launcher/0.1")
    }

    try {
        if (connection.responseCode !in 200..299) {
            error("Mojang returned HTTP ${connection.responseCode}")
        }
        val finalUrl = connection.url
        require(finalUrl.protocol == "https" && finalUrl.host.lowercase() in allowedHosts) {
            "Client JAR download redirected to an untrusted host"
        }
        val contentLength = connection.getHeaderFieldLong("Content-Length", -1L)
        if (contentLength >= 0L && contentLength != expectedSize) {
            error("Download size does not match Mojang metadata")
        }

        val digest = MessageDigest.getInstance("SHA-1")
        var totalBytes = 0L
        connection.inputStream.use { input ->
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    totalBytes += count
                    require(totalBytes <= expectedSize && totalBytes <= MAX_CLIENT_JAR_BYTES) {
                        "Downloaded file is larger than the expected client JAR"
                    }
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }

        require(totalBytes == expectedSize) {
            "Downloaded size ($totalBytes bytes) does not match expected size ($expectedSize bytes)"
        }
        val actualSha1 = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        require(actualSha1.equals(expectedSha1, ignoreCase = true)) {
            "Client JAR SHA-1 verification failed; the incomplete file was rejected"
        }

        if (destination.exists()) require(destination.delete()) { "Could not replace an invalid cached JAR" }
        require(temporary.renameTo(destination)) { "Could not finalize the verified client JAR" }
        return destination
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

private fun sha1OfBytes(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-1").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

