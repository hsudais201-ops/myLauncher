package com.mflegend.flamelauncher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.mflegend.flamelauncher.storage.SafGameStorage
import com.mflegend.flamelauncher.versions.MinecraftVersionScanner
import com.mflegend.flamelauncher.versions.InstalledMinecraftVersion
import com.mflegend.flamelauncher.catalog.MojangVersion
import com.mflegend.flamelauncher.catalog.MojangVersionManifestClient
import com.mflegend.flamelauncher.catalog.MojangClientJarDownloader
import com.mflegend.flamelauncher.catalog.MojangLibraryDownloader
import com.mflegend.flamelauncher.catalog.MojangAssetDownloader
import com.mflegend.flamelauncher.catalog.MojangVersionInstaller
import com.mflegend.flamelauncher.catalog.DownloadControl
import com.mflegend.flamelauncher.runtime.AndroidRuntimeEnvironment
import com.mflegend.flamelauncher.runtime.AndroidJavaRuntimeManager
import com.mflegend.flamelauncher.runtime.RuntimeLaunchReadiness
import java.util.concurrent.CancellationException

private val Flame = Color(0xFFFF6A2A)
private val Background = Color(0xFF101116)
private val Panel = Color(0xFF1B1D25)
private val Muted = Color(0xFF9CA3AF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FlameLauncherApp() }
    }
}

@Composable
private fun FlameLauncherApp() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("flame_launcher", android.content.Context.MODE_PRIVATE) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var gameFolderUri by remember { mutableStateOf(preferences.getString("game_tree_uri", null)) }
    var folderPermissionError by remember { mutableStateOf<String?>(null) }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val permissionFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, permissionFlags)
                preferences.edit().putString("game_tree_uri", uri.toString()).apply()
                gameFolderUri = uri.toString()
                folderPermissionError = null
            } catch (error: SecurityException) {
                folderPermissionError = "Android could not retain access to this folder. Please choose it again."
            }
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Flame,
        secondary = Color(0xFFFFB36B),
        background = Background,
        surface = Panel,
        onBackground = Color.White,
        onSurface = Color.White
    )) {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                NavigationBar(containerColor = Panel) {
                    val tabs = listOf("Home", "Versions", "Library", "Settings")
                    val icons = listOf(Icons.Default.Home, Icons.Default.Download, Icons.Default.Extension, Icons.Default.Settings)
                    tabs.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { androidx.compose.material3.Icon(icons[index], contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("FLAME", color = Flame, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                        Text("Launcher", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text("ANDROID", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
                }
                when (selectedTab) {
                    0 -> HomeScreen()
                    1 -> VersionsScreen(gameFolderUri)
                    2 -> LibraryScreen(gameFolderUri)
                    else -> SettingsScreen(gameFolderUri, folderPermissionError, onChooseFolder = { folderPicker.launch(null) })
                }
                Text("Created and maintained by MFLegend", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("YOUR JAVA EDITION HUB", color = Flame, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("Your next world\nstarts here.", color = Color.White, fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
            Text("Flame Launcher is being built for Android phones and tablets.", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Surface(color = Color(0xFF3A241D), shape = RoundedCornerShape(50)) {
                Text("Android edition · Early development", color = Color(0xFFFFB36B), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("GAME RUNTIME", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("Not connected yet", color = Flame, fontWeight = FontWeight.SemiBold)
            Text("The Android Java runtime, graphics bridge and input integration are not ready. Launching is disabled until a real device-tested runtime is integrated.", color = Muted, fontSize = 13.sp)
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("Play Minecraft")
            }
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("COMING TO FLAME", color = Flame, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            FeatureLine("Touch-first controls", "Mobile controls with optional keyboard and mouse.")
            FeatureLine("Game file manager", "Choose folders safely through Android's document picker.")
            FeatureLine("Java runtime", "Device-compatible runtime and graphics integration.")
        }
    }
}

@Composable
private fun FeatureLine(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(detail, color = Muted, fontSize = 12.sp)
    }
}


@Composable
private fun VersionsScreen(folderUri: String?) {
    val context = LocalContext.current
    var versions by remember { mutableStateOf<List<InstalledMinecraftVersion>>(emptyList()) }
    var missingMetadata by remember { mutableStateOf<List<String>>(emptyList()) }
    var warnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var directoryFound by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var scanRevision by remember { mutableIntStateOf(0) }
    var catalog by remember { mutableStateOf<List<MojangVersion>>(emptyList()) }
    var latestRelease by remember { mutableStateOf<String?>(null) }
    var latestSnapshot by remember { mutableStateOf<String?>(null) }
    var catalogLoading by remember { mutableStateOf(false) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var metadataDownloadId by remember { mutableStateOf<String?>(null) }
    var metadataDownloadMessage by remember { mutableStateOf<String?>(null) }
    var clientJarDownloadId by remember { mutableStateOf<String?>(null) }
    var libraryDownloadId by remember { mutableStateOf<String?>(null) }
    var assetDownloadId by remember { mutableStateOf<String?>(null) }
    var installId by remember { mutableStateOf<String?>(null) }
    var installProgress by remember { mutableStateOf<String?>(null) }
    var activeInstallControl by remember { mutableStateOf<DownloadControl?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(folderUri, scanRevision) {
        if (folderUri == null) { versions = emptyList(); directoryFound = false; return@LaunchedEffect }
        loading = true
        scanError = null
        try {
            val result = withContext(Dispatchers.IO) { MinecraftVersionScanner(SafGameStorage(context.contentResolver, Uri.parse(folderUri))).scan() }
            directoryFound = result.versionsDirectoryFound
            versions = result.versions
            missingMetadata = result.foldersWithoutMetadata
            warnings = result.warnings
        } catch (error: Exception) { scanError = error.localizedMessage ?: "Could not scan installed versions." }
        finally { loading = false }
    }

    LaunchedEffect(Unit) {
        catalogLoading = true
        catalogError = null
        try {
            val manifest = withContext(Dispatchers.IO) { MojangVersionManifestClient().fetch() }
            catalog = manifest.versions
            latestRelease = manifest.latestRelease
            latestSnapshot = manifest.latestSnapshot
        } catch (error: Exception) { catalogError = error.localizedMessage ?: "Could not load Mojang version manifest." }
        finally { catalogLoading = false }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Minecraft versions", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("Browse official releases and snapshots. Install files downloads the supported vanilla payload with transfer progress, cancellation, and checksum verification. Android runtime and game launching are not ready yet.", color = Muted, fontSize = 13.sp)
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("OFFICIAL VERSION CATALOG", color = Flame, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                if (catalogLoading) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(); Text("Loading Mojang manifest…", color = Muted, fontSize = 12.sp) } }
                else if (catalogError != null) { Text(catalogError ?: "Manifest unavailable.", color = Color(0xFFFF9A82), fontSize = 12.sp) }
                else {
                    Text("Latest release: " + (latestRelease ?: "Unknown"), color = Color.White, fontSize = 13.sp)
                    Text("Latest snapshot: " + (latestSnapshot ?: "Unknown"), color = Muted, fontSize = 12.sp)
                    Text(catalog.size.toString() + " versions in Mojang manifest", color = Muted, fontSize = 11.sp)
                    catalog.take(30).forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(item.id, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text(item.releaseTime.take(10), color = Muted, fontSize = 10.sp) }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(item.type, color = Flame, fontSize = 11.sp)
                                Button(
                                    onClick = {
                                        if (folderUri == null) {
                                            metadataDownloadMessage = "Choose your game folder in Settings before saving metadata."
                                        } else {
                                            scope.launch {
                                                metadataDownloadId = item.id
                                                metadataDownloadMessage = null
                                                try {
                                                    withContext(Dispatchers.IO) {
                                                        require(item.id.matches(Regex("[A-Za-z0-9._-]{1,100}"))) { "Invalid version identifier." }
                                                        val storage = SafGameStorage(context.contentResolver, Uri.parse(folderUri))
                                                        val root = storage.root()
                                                        val versionsDir = storage.listChildren(root).firstOrNull { it.isDirectory && it.name == "versions" }
                                                            ?.uri ?: storage.createDirectory(root, "versions")
                                                        val versionDir = storage.listChildren(versionsDir).firstOrNull { it.isDirectory && it.name == item.id }
                                                            ?.uri ?: storage.createDirectory(versionsDir, item.id)
                                                        val bytes = MojangVersionManifestClient().fetchVersionMetadata(item)
                                                        val fileName = item.id + ".json"
                                                        val existingMetadata = storage.listChildren(versionDir)
                                                            .firstOrNull { !it.isDirectory && it.name == fileName }
                                                        val temporaryName = ".$fileName.download"
                                                        storage.listChildren(versionDir)
                                                            .firstOrNull { !it.isDirectory && it.name == temporaryName }
                                                            ?.let { storage.delete(it.uri) }
                                                        val temporaryUri = storage.createFile(versionDir, temporaryName, "application/json")
                                                        try {
                                                            storage.openOutput(temporaryUri).use { output ->
                                                                output.write(bytes)
                                                                output.flush()
                                                            }
                                                            storage.replaceWithVerifiedFile(temporaryUri, existingMetadata?.uri, fileName)
                                                        } catch (error: Exception) {
                                                            storage.deleteTemporaryAfterFailure(temporaryUri, error)
                                                            throw error
                                                        }
                                                    }
                                                    metadataDownloadMessage = item.id + " metadata downloaded and SHA-1 checked."
                                                    scanRevision += 1
                                                } catch (error: Exception) {
                                                    metadataDownloadMessage = item.id + ": " + (error.localizedMessage ?: "Metadata download failed.")
                                                } finally {
                                                    metadataDownloadId = null
                                                }
                                            }
                                        }
                                    },
                                    enabled = metadataDownloadId == null && clientJarDownloadId == null && libraryDownloadId == null && assetDownloadId == null && installId == null,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    if (metadataDownloadId == item.id) CircularProgressIndicator(modifier = Modifier.height(14.dp), strokeWidth = 2.dp)
                                    else Text("Get JSON", fontSize = 10.sp)
                                }
                                Button(
                                    onClick = {
                                        if (folderUri == null) {
                                            metadataDownloadMessage = "Choose your game folder in Settings before downloading the client JAR."
                                        } else {
                                            scope.launch {
                                                clientJarDownloadId = item.id
                                                metadataDownloadMessage = null
                                                try {
                                                    withContext(Dispatchers.IO) {
                                                        val storage = SafGameStorage(context.contentResolver, Uri.parse(folderUri))
                                                        val root = storage.root()
                                                        val versionsDir = storage.listChildren(root).firstOrNull { it.isDirectory && it.name == "versions" }
                                                            ?.uri ?: storage.createDirectory(root, "versions")
                                                        val versionDir = storage.listChildren(versionsDir).firstOrNull { it.isDirectory && it.name == item.id }
                                                            ?.uri ?: storage.createDirectory(versionsDir, item.id)
                                                        val metadataBytes = MojangVersionManifestClient().fetchVersionMetadata(item)
                                                        MojangClientJarDownloader().download(item.id, metadataBytes, storage, versionDir)
                                                    }
                                                    metadataDownloadMessage = item.id + " client JAR downloaded and SHA-1 verified."
                                                    scanRevision += 1
                                                } catch (error: Exception) {
                                                    metadataDownloadMessage = item.id + ": " + (error.localizedMessage ?: "Client JAR download failed.")
                                                } finally {
                                                    clientJarDownloadId = null
                                                }
                                            }
                                        }
                                    },
                                    enabled = clientJarDownloadId == null && metadataDownloadId == null && libraryDownloadId == null && assetDownloadId == null && installId == null,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    if (clientJarDownloadId == item.id) CircularProgressIndicator(modifier = Modifier.height(14.dp), strokeWidth = 2.dp)
                                    else Text("Get JAR", fontSize = 10.sp)
                                }
                                Button(
                                    onClick = {
                                        if (folderUri == null) {
                                            metadataDownloadMessage = "Choose your game folder in Settings before downloading libraries."
                                        } else {
                                            scope.launch {
                                                libraryDownloadId = item.id
                                                metadataDownloadMessage = null
                                                try {
                                                    val result = withContext(Dispatchers.IO) {
                                                        val storage = SafGameStorage(context.contentResolver, Uri.parse(folderUri))
                                                        val metadataBytes = MojangVersionManifestClient().fetchVersionMetadata(item)
                                                        MojangLibraryDownloader().download(metadataBytes, storage, storage.root())
                                                    }
                                                    metadataDownloadMessage = item.id + ": ${result.downloaded} libraries downloaded, ${result.alreadyPresent} verified already present, ${result.skippedByRules} OS-rule entries skipped."
                                                    scanRevision += 1
                                                } catch (error: Exception) {
                                                    metadataDownloadMessage = item.id + ": " + (error.localizedMessage ?: "Library download failed.")
                                                } finally {
                                                    libraryDownloadId = null
                                                }
                                            }
                                        }
                                    },
                                    enabled = libraryDownloadId == null && clientJarDownloadId == null && metadataDownloadId == null && assetDownloadId == null && installId == null,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    if (libraryDownloadId == item.id) CircularProgressIndicator(modifier = Modifier.height(14.dp), strokeWidth = 2.dp)
                                    else Text("Get libs", fontSize = 10.sp)
                                }
                                Button(
                                    onClick = {
                                        if (folderUri == null) {
                                            metadataDownloadMessage = "Choose your game folder in Settings before downloading assets."
                                        } else {
                                            scope.launch {
                                                assetDownloadId = item.id
                                                metadataDownloadMessage = null
                                                try {
                                                    val result = withContext(Dispatchers.IO) {
                                                        val storage = SafGameStorage(context.contentResolver, Uri.parse(folderUri))
                                                        val metadataBytes = MojangVersionManifestClient().fetchVersionMetadata(item)
                                                        MojangAssetDownloader().download(metadataBytes, storage, storage.root())
                                                    }
                                                    metadataDownloadMessage = item.id + ": ${result.downloaded} assets downloaded, ${result.alreadyPresent} verified already present (index ${result.indexId})."
                                                    scanRevision += 1
                                                } catch (error: Exception) {
                                                    metadataDownloadMessage = item.id + ": " + (error.localizedMessage ?: "Asset download failed.")
                                                } finally {
                                                    assetDownloadId = null
                                                }
                                            }                                        }
                                    },
                                    enabled = assetDownloadId == null && libraryDownloadId == null && clientJarDownloadId == null && metadataDownloadId == null && installId == null,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    if (assetDownloadId == item.id) CircularProgressIndicator(modifier = Modifier.height(14.dp), strokeWidth = 2.dp)
                                    else Text("Get assets", fontSize = 10.sp)
                                }
                                Button(
                                    onClick = {
                                        if (folderUri == null) {
                                            metadataDownloadMessage = "Choose your game folder in Settings before installing version files."
                                        } else {
                                            scope.launch {
                                                installId = item.id
                                                installProgress = "Preparing download…"
                                                val mainHandler = Handler(Looper.getMainLooper())
                                                val control = DownloadControl { stage, completed, total ->
                                                    val amount = String.format(java.util.Locale.US, "%.1f MB", completed / 1048576.0)
                                                    val target = total?.let { " / " + String.format(java.util.Locale.US, "%.1f MB", it / 1048576.0) } ?: ""
                                                    mainHandler.post {
                                                        if (installId == item.id && activeInstallControl != null) installProgress = "$stage: $amount$target"
                                                    }
                                                }
                                                activeInstallControl = control
                                                metadataDownloadMessage = "Preparing " + item.id + " files…"
                                                try {
                                                    val result = withContext(Dispatchers.IO) {
                                                        val storage = SafGameStorage(context.contentResolver, Uri.parse(folderUri))
                                                        MojangVersionInstaller().install(item, storage, control)
                                                    }
                                                    metadataDownloadMessage = "${result.versionId}: files downloaded. Client JAR ${result.clientJarBytes} bytes; ${result.libraries.downloaded} libraries; ${result.assets.downloaded} assets; ${result.libraries.skippedByRules} OS-rule entries skipped."
                                                    scanRevision += 1
                                                } catch (error: CancellationException) {
                                                    metadataDownloadMessage = item.id + ": cancelled. Verified completed files were kept; choose Install files again to continue."
                                                } catch (error: Exception) {
                                                    metadataDownloadMessage = item.id + ": installation stopped — " + (error.localizedMessage ?: "Download failed.") + " Verified completed files were kept. Fix the issue and retry Install files; existing files are checked again."
                                                } finally {
                                                    installId = null
                                                    activeInstallControl = null
                                                    installProgress = null
                                                }
                                            }
                                        }
                                    },
                                    enabled = installId == null && assetDownloadId == null && libraryDownloadId == null && clientJarDownloadId == null && metadataDownloadId == null,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    if (installId == item.id) CircularProgressIndicator(modifier = Modifier.height(14.dp), strokeWidth = 2.dp)
                                    else Text("Install files", fontSize = 10.sp)
                                }
                                if (installId == item.id) {
                                    Button(
                                        onClick = {
                                            activeInstallControl?.cancel()
                                            installProgress = "Cancellation requested…"
                                        },
                                        enabled = activeInstallControl != null,
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                    ) { Text("Cancel", fontSize = 10.sp) }
                                }
                            }
                        }
                    }
                    if (installProgress != null) Text(installProgress ?: "", color = Flame, fontSize = 11.sp)
                    if (metadataDownloadMessage != null) Text(metadataDownloadMessage ?: "", color = if (metadataDownloadMessage!!.contains("downloaded")) Color(0xFF8DD9A8) else Color(0xFFFFB36B), fontSize = 11.sp)
                }
            }
        }
        Text("LOCAL INSTALLATIONS", color = Flame, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        when {
            folderUri == null -> InfoScreen("Game folder required", "Choose your Minecraft game directory in Settings before scanning installed versions.")
            loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(); Text("Scanning local versions…", color = Muted, fontSize = 13.sp) }
            scanError != null -> Text(scanError ?: "Scan failed.", color = Color(0xFFFF9A82), fontSize = 13.sp)
            !directoryFound -> InfoScreen("Versions folder not found", "No versions directory was found at the selected folder root. Select your .minecraft directory, not its parent folder.")
            versions.isEmpty() -> InfoScreen("No readable version metadata", "The versions folder exists, but no valid version JSON files were found.")
            else -> {
                Text(versions.size.toString() + " local version(s) detected", color = Muted, fontSize = 12.sp)
                versions.forEach { version ->
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(version.id, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text((version.type ?: "Unknown type").replaceFirstChar { it.uppercase() } + (version.inheritsFrom?.let { " · inherits $it" } ?: ""), color = Flame, fontSize = 12.sp)
                            Text("Main class: " + (version.mainClass ?: "Not specified"), color = Muted, fontSize = 11.sp)
                            if (version.assetIndexId != null) Text("Asset index: " + version.assetIndexId, color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        if (missingMetadata.isNotEmpty()) Text("Folders without matching JSON: " + missingMetadata.joinToString(), color = Muted, fontSize = 11.sp)
        warnings.forEach { Text(it, color = Color(0xFFFFB36B), fontSize = 11.sp) }
    }
}

@Composable
private fun LibraryScreen(folderUri: String?) {
    val context = LocalContext.current
    val directoryStack = remember(folderUri) {
        mutableStateListOf<Uri>().apply {
            if (folderUri != null) {
                val tree = Uri.parse(folderUri)
                val rootId = android.provider.DocumentsContract.getTreeDocumentId(tree)
                add(android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, rootId))
            }
        }
    }
    val currentDirectory = directoryStack.lastOrNull()
    var entries by remember(folderUri) { mutableStateOf<List<SafGameStorage.Entry>>(emptyList()) }
    var loading by remember(folderUri) { mutableStateOf(false) }
    var loadError by remember(folderUri) { mutableStateOf<String?>(null) }

    LaunchedEffect(folderUri, currentDirectory) {
        if (folderUri == null || currentDirectory == null) {
            entries = emptyList()
            return@LaunchedEffect
        }
        loading = true
        loadError = null
        try {
            entries = withContext(Dispatchers.IO) {
                SafGameStorage(context.contentResolver, Uri.parse(folderUri)).listChildren(currentDirectory)
            }
        } catch (error: Exception) {
            entries = emptyList()
            loadError = if (error is SecurityException) {
                "Folder access expired. Choose the game folder again in Settings."
            } else {
                error.localizedMessage ?: "Could not read this folder."
            }
        } finally {
            loading = false
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (directoryStack.size > 1) {
                    androidx.compose.material3.IconButton(onClick = { directoryStack.removeAt(directoryStack.lastIndex) }) {
                        androidx.compose.material3.Icon(Icons.Default.ArrowBack, contentDescription = "Go to parent folder")
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Game folder", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(if (folderUri == null) "No folder selected" else "Browse selected Android folder", color = Muted, fontSize = 12.sp)
                }
            }
            if (folderUri == null) {
                Text("Choose a game folder in Settings to browse its contents.", color = Muted, fontSize = 14.sp)
            } else if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator()
                    Text("Reading folder…", color = Muted, fontSize = 13.sp)
                }
            } else if (loadError != null) {
                Text(loadError ?: "Unable to read folder.", color = Color(0xFFFF9A82), fontSize = 13.sp)
            } else if (entries.isEmpty()) {
                Text("This folder is empty, or the document provider returned no items.", color = Muted, fontSize = 13.sp)
            } else {
                if (directoryStack.size == 1) {
                    val expectedFolders = listOf("versions", "assets", "libraries", "mods", "resourcepacks", "saves")
                    val foundFolders = entries.filter { it.isDirectory && it.name.lowercase() in expectedFolders }
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF242630)), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("MINECRAFT DIRECTORY CHECK", color = Flame, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            if (foundFolders.isEmpty()) {
                                Text("No standard Minecraft folders detected at this level.", color = Muted, fontSize = 12.sp)
                            } else {
                                Text("Detected: " + foundFolders.joinToString { it.name }, color = Color.White, fontSize = 12.sp)
                            }
                            Text(if (foundFolders.any { it.name.equals("versions", ignoreCase = true) }) "Version directory found — installed versions can be inspected next." else "No versions directory detected at this level.", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
                Text(entries.size.toString() + " item(s)", color = Muted, fontSize = 11.sp)
                entries.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable(enabled = entry.isDirectory) { directoryStack.add(entry.uri) }
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = if (entry.isDirectory) Flame else Muted
                        )
                        Column(Modifier.weight(1f)) {
                            Text(entry.name.ifBlank { "Unnamed item" }, color = Color.White, fontSize = 14.sp)
                            Text(if (entry.isDirectory) "Folder" else entry.mimeType, color = Muted, fontSize = 11.sp)
                        }
                        if (entry.sizeBytes != null && !entry.isDirectory) {
                            Text(entry.sizeBytes.toString() + " B", color = Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(folderUri: String?, error: String?, onChooseFolder: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    fun checkPersistedFolderAccess(): Boolean =
        folderUri?.let { selectedUri ->
            val selectedTree = Uri.parse(selectedUri)
            context.contentResolver.persistedUriPermissions.any { permission ->
                permission.uri == selectedTree &&
                    permission.isReadPermission &&
                    permission.isWritePermission
            }
        } ?: false

    var hasPersistedFolderAccess by remember(folderUri) {
        mutableStateOf(checkPersistedFolderAccess())
    }
    DisposableEffect(lifecycleOwner, folderUri) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPersistedFolderAccess = checkPersistedFolderAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val runtime = remember { AndroidRuntimeEnvironment.inspect(context) }
    val runtimeScan by produceState<AndroidJavaRuntimeManager.ScanResult?>(initialValue = null, runtime) {
        value = withContext(Dispatchers.IO) {
            AndroidJavaRuntimeManager(context).scan(runtime)
        }
    }
    val scanResult = runtimeScan
    val launchReadiness = remember { RuntimeLaunchReadiness() }

    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Settings", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("GAME STORAGE", color = Flame, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("Choose a game folder. Flame Launcher can browse it and install supported vanilla version files. Android-specific native dependencies, runtime integration, and game launching are still in development.", color = Muted, fontSize = 13.sp)
            Button(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) {
                Text(if (folderUri == null) "Choose game folder" else "Change game folder")
            }
            if (folderUri != null && hasPersistedFolderAccess) {
                Text("Saved folder access is available", color = Color(0xFF8DD9A8), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(folderUri, color = Muted, fontSize = 11.sp)
            } else if (folderUri != null) {
                Text("Saved folder access is missing or expired. Choose the game folder again.", color = Color(0xFFFFB36B), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(folderUri, color = Muted, fontSize = 11.sp)
            } else {
                Text("No game folder selected", color = Muted, fontSize = 12.sp)
            }
            if (error != null) {
                Text(error, color = Color(0xFFFF9A82), fontSize = 12.sp)
            }
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("JAVA RUNTIME READINESS", color = Flame, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text(
                if (runtime.supportsInitialArm64Runtime) "ARM64 device capability detected" else "Initial ARM64 target not confirmed",
                color = if (runtime.supportsInitialArm64Runtime) Color(0xFF8DD9A8) else Color(0xFFFFB36B),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text("Android API: ${runtime.androidApiLevel}", color = Muted, fontSize = 12.sp)
            Text("Primary ABI: ${runtime.primaryAbi ?: "Unknown"}", color = Muted, fontSize = 12.sp)
            Text("Supported ABIs: ${runtime.supportedAbis.joinToString().ifBlank { "Unknown" }}", color = Muted, fontSize = 12.sp)
            Text("App process: ${if (runtime.is64BitProcess) "64-bit" else "32-bit"}", color = Muted, fontSize = 12.sp)
            Text("App heap limit: ${runtime.maxHeapMb} MB · Device memory class: ${runtime.memoryClassMb?.let { "$it MB" } ?: "Unknown"}", color = Muted, fontSize = 12.sp)
            Text("Available processor cores: ${runtime.processorCount}", color = Muted, fontSize = 12.sp)
            if (scanResult == null) {
                Text("Checking app-private Java runtime storage…", color = Muted, fontSize = 12.sp)
            } else if (scanResult.abiMatchedRuntimes.isNotEmpty()) {
                Text(
                    "ABI-matched runtime candidates found: ${scanResult.abiMatchedRuntimes.joinToString { "Java ${it.javaMajorVersion} (${it.id})" }}",
                    color = Color(0xFF8DD9A8),
                    fontSize = 12.sp
                )
            } else if (scanResult.runtimes.isNotEmpty()) {
                Text("Java runtime candidates were found, but none match this app process ABI.", color = Color(0xFFFFB36B), fontSize = 12.sp)
            } else {
                Text("No ABI-matched Minecraft Java runtime candidate is installed in Flame Launcher's private runtime storage.", color = Muted, fontSize = 12.sp)
            }
            Text(
                "ABI matching is not proof of Android linker or native-library compatibility. The scan does not load or start a JVM; Minecraft launching remains unavailable until a supported Android runtime, native dependencies, and graphics bridge are integrated.",
                color = Muted,
                fontSize = 12.sp
            )
            Text(
                if (launchReadiness.canLaunch) "Launch requirements verified." else "PLAY IS LOCKED — outstanding verification:",
                color = Color(0xFFFFB36B),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            launchReadiness.missingRequirements.forEach { requirement ->
                Text("• $requirement", color = Muted, fontSize = 11.sp)
            }
            scanResult?.warnings?.take(2)?.forEach { warning ->
                Text(warning, color = Color(0xFFFFB36B), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun InfoScreen(title: String, detail: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = Muted, fontSize = 14.sp)
        }
    }
}
