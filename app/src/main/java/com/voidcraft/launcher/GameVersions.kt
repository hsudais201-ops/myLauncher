package com.voidcraft.launcher

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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val MOJANG_VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

private data class MinecraftVersionEntry(
    val id: String,
    val type: String,
    val releaseTime: String,
    val metadataUrl: String
)

private data class MinecraftVersionDetails(
    val id: String,
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
    var versions by remember { mutableStateOf<List<MinecraftVersionEntry>>(emptyList()) }
    var latestRelease by remember { mutableStateOf("") }
    var latestSnapshot by remember { mutableStateOf("") }
    var showSnapshots by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var inspectingId by remember { mutableStateOf<String?>(null) }
    var inspectedDetails by remember { mutableStateOf<MinecraftVersionDetails?>(null) }
    var inspectionError by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Connect to the internet to load Mojang's official version manifest.") }
    val scope = rememberCoroutineScope()

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
                runCatching { fetchMinecraftVersionDetails(entry) }
            }
            result.onSuccess { inspectedDetails = it }
                .onFailure { error ->
                    inspectionError = "Could not inspect ${{entry.id}: ${{error.message ?: "metadata error"}."
                }
            inspectingId = null
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
                        "Required Java: ${{details.javaMajorVersion?.toString() ?: "not specified"}${{details.javaComponent?.let { " ($it)" } ?: ""}",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("Client JAR: ${{if (details.clientJarUrl.isNullOrBlank()) "not listed" else "available in metadata"}", color = VoidMuted, fontSize = 12.sp)
                    details.clientJarSizeBytes?.let {
                        Text("Client JAR size: %.2f MiB".format(it / (1024.0 * 1024.0)), color = VoidMuted, fontSize = 12.sp)
                    }
                    details.clientJarSha1?.let {
                        Text("Client JAR SHA-1: $it", color = VoidMuted, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                    Text("Asset index: ${{details.assetIndexId ?: "not listed"}", color = VoidMuted, fontSize = 12.sp)
                    Text("Libraries listed: ${{details.libraryCount}", color = VoidMuted, fontSize = 12.sp)
                    Text("Release time: ${{details.releaseTime.ifBlank { "not listed" }}", color = VoidMuted, fontSize = 12.sp)
                    details.assetIndexUrl?.let {
                        Text("Asset index URL: $it", color = VoidMuted, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                    Text(
                        "Inspection only: no JARs, libraries, assets, or game files were downloaded or installed.",
                        color = VoidMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    OutlinedButton(onClick = { inspectedDetails = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("CLOSE INSPECTOR")
                    }
                }
            }
        }
        inspectionError?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, lineHeight = 17.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Include snapshots", fontWeight = FontWeight.SemiBold)
                Text("Snapshots may be unstable.", color = VoidMuted, fontSize = 11.sp)
            }
            Switch(checked = showSnapshots, onCheckedChange = { showSnapshots = it })
        }

        if (versions.isEmpty() && loading) {
            CircularProgressIndicator()
        } else if (versions.isEmpty()) {
            Text(status, color = VoidMuted, fontSize = 12.sp, lineHeight = 18.sp)
        } else {
            val visibleVersions = versions.filter { showSnapshots || it.type == "release" }.take(25)
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
                    "Selecting a version saves your preference only. Settings can install SHA-256-verified Android Java 8, 17, 21, or 25 runtimes for supported CPU architectures, but Voidcraft still does not download Minecraft libraries/assets, install a game version, authenticate a Microsoft account, or launch the game. Choose a Java version compatible with the selected game and mods.",
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
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val root = JSONObject(body)
        val latest = root.optJSONObject("latest") ?: JSONObject()
        val rows = root.optJSONArray("versions")
        val parsed = buildList {
            if (rows != null) {
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    val id = row.optString("id").trim()
                    val type = row.optString("type").trim()
                    if (id.isNotBlank() && type in setOf("release", "snapshot")) {
                        add(MinecraftVersionEntry(id, type, row.optString("releaseTime"), row.optString("url")))
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
 
private fun fetchMinecraftVersionDetails(entry: MinecraftVersionEntry): MinecraftVersionDetails {
    require(entry.metadataUrl.isNotBlank()) { "Mojang manifest did not provide a version metadata URL" }
    val metadataUrl = URL(entry.metadataUrl)
    require(
        metadataUrl.protocol == "https" &&
            metadataUrl.host.lowercase() in setOf("piston-meta.mojang.com", "launchermeta.mojang.com")
    ) { "Unexpected metadata host; only Mojang version metadata is accepted" }

    val connection = (metadataUrl.openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 15000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "Voidcraft-Android-Launcher/0.1")
    }
    try {
        if (connection.responseCode !in 200..299) {
            error("Mojang returned HTTP ${{connection.responseCode}")
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val root = JSONObject(body)
        val javaVersion = root.optJSONObject("javaVersion")
        val downloads = root.optJSONObject("downloads")
        val client = downloads?.optJSONObject("client")
        val assetIndex = root.optJSONObject("assetIndex")
        val libraries = root.optJSONArray("libraries")
        return MinecraftVersionDetails(
            id = root.optString("id", entry.id).ifBlank { entry.id },
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

