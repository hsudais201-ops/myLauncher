package com.voidcraft.launcher

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private val VoidBackground = Color(0xFF0A0C12)
private val VoidSurface = Color(0xFF141824)
private val VoidAccent = Color(0xFF9B8CFF)
private val VoidMint = Color(0xFF70E1C1)
private val VoidMuted = Color(0xFF9AA3B8)
private const val PREFS = "voidcraft_preferences"
private const val STORE_ENABLED = "feature_store_enabled"
private const val INSTALLED_IDS = "installed_feature_ids"
private const val CATALOG_URL = "https://raw.githubusercontent.com/hsudais201-ops/myLauncher/main/store/catalog.json"

data class StoreFeature(
    val id: String,
    val name: String,
    val tagline: String,
    val description: String,
    val kind: String,
    val category: String,
    val version: String,
    val compatibility: String,
    val license: String,
    val packageUrl: String?,
    val availability: String,
    val builtIn: Boolean
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VoidcraftApp() }
    }
}

private enum class LauncherTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Versions("Versions", Icons.Default.SportsEsports),
    Mods("Mods", Icons.Default.Extension),
    Settings("Settings", Icons.Default.Settings)
}

@Composable
private fun VoidcraftApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var storeOpen by remember { mutableStateOf(false) }
    var storeEnabled by remember { mutableStateOf(prefs.getBoolean(STORE_ENABLED, false)) }
    var installedIds by remember { mutableStateOf(prefs.getStringSet(INSTALLED_IDS, emptySet())?.toSet() ?: emptySet()) }
    var activeTheme by remember { mutableStateOf(prefs.getString("active_theme", "default") ?: "default") }
    var catalog by remember { mutableStateOf(defaultCatalog()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedFeature by remember { mutableStateOf<StoreFeature?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    suspend fun refreshCatalog() {
        isRefreshing = true
        val remote = withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(CATALOG_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 7000
                    requestMethod = "GET"
                }
                try {
                    if (connection.responseCode !in 200..299) error("Catalog HTTP ${connection.responseCode}")
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    parseCatalog(body)
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
        if (!remote.isNullOrEmpty()) {
            val local = defaultCatalog().associateBy { it.id }
            catalog = (remote + local.values).distinctBy { it.id }
        }
        isRefreshing = false
    }

    LaunchedEffect(storeOpen) {
        if (storeOpen) refreshCatalog()
    }

    fun saveInstalled(next: Set<String>) {
        installedIds = next
        prefs.edit().putStringSet(INSTALLED_IDS, next).apply()
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = when (activeTheme) {
                "theme-neon-night" -> Color(0xFF70E1C1)
                "theme-void-minimal" -> Color(0xFFB7C0D8)
                else -> VoidAccent
            },
            secondary = VoidMint,
            background = VoidBackground,
            surface = VoidSurface,
            onPrimary = Color(0xFF171326),
            onBackground = Color(0xFFF4F5FA),
            onSurface = Color(0xFFF4F5FA)
        )
    ) {
    Scaffold(
        containerColor = VoidBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = VoidSurface, tonalElevation = 0.dp) {
                LauncherTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = !storeOpen && selectedTab == index,
                        onClick = { storeOpen = false; selectedFeature = null; selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(18.dp))
            if (storeOpen) {
                StoreHeader(
                    onBack = { selectedFeature = null; storeOpen = false },
                    onRefresh = { scope.launch { refreshCatalog() } },
                    isRefreshing = isRefreshing,
                    onDisable = {
                        storeEnabled = false
                        prefs.edit().putBoolean(STORE_ENABLED, false).apply()
                        selectedFeature = null
                        storeOpen = false
                        scope.launch { snackbarHostState.showSnackbar("Feature Store disabled. Your installed features were kept.") }
                    }
                )
                Spacer(Modifier.height(22.dp))
                if (selectedFeature != null) {
                    FeatureDetail(
                        feature = selectedFeature!!,
                        installed = selectedFeature!!.id in installedIds,
                        onInstall = {
                            val feature = selectedFeature!!
                            if (feature.builtIn) {
                                saveInstalled(installedIds + feature.id)
                                if (feature.id == "theme-neon-night" || feature.id == "theme-void-minimal") {
                                    activeTheme = feature.id
                                    prefs.edit().putString("active_theme", activeTheme).apply()
                                }
                                scope.launch { snackbarHostState.showSnackbar("${feature.name} enabled.") }
                            } else if (feature.packageUrl.isNullOrBlank() || !feature.packageUrl.startsWith("https://")) {
                                scope.launch { snackbarHostState.showSnackbar("This listing has no verified HTTPS package yet.") }
                            } else {
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching {
                                            val connection = (URL(feature.packageUrl).openConnection() as HttpURLConnection).apply {
                                                connectTimeout = 10000
                                                readTimeout = 20000
                                                instanceFollowRedirects = false
                                            }
                                            try {
                                                if (connection.responseCode !in 200..299) error("Download failed: HTTP ${connection.responseCode}")
                                                val length = connection.contentLengthLong
                                                if (length > 500L * 1024 * 1024) error("Package exceeds the 500 MB safety limit")
                                                val dir = File(context.filesDir, "feature-store").apply { mkdirs() }
                                                val safeName = feature.id.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".package"
                                                val destination = File(dir, safeName)
                                                val temp = File(dir, safeName + ".part")
                                                connection.inputStream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                                                if (length >= 0 && temp.length() != length) error("Downloaded package size did not match the server response")
                                                if (!temp.renameTo(destination)) error("Could not finalize the downloaded package")
                                                destination.absolutePath
                                            } finally { connection.disconnect() }
                                        }
                                    }
                                    result.onSuccess {
                                        saveInstalled(installedIds + feature.id)
                                        snackbarHostState.showSnackbar("${feature.name} downloaded to app storage. Game-folder integration is still required before Minecraft can load it.")
                                    }.onFailure {
                                        snackbarHostState.showSnackbar("Install failed: ${it.message ?: "download error"}")
                                    }
                                }
                            }
                        },
                        onUninstall = {
                            val feature = selectedFeature!!
                            File(context.filesDir, "feature-store/${feature.id.replace(Regex("[^A-Za-z0-9._-]"), "_")}.package").delete()
                            saveInstalled(installedIds - feature.id)
                            if (activeTheme == feature.id) {
                                activeTheme = "default"
                                prefs.edit().putString("active_theme", activeTheme).apply()
                            }
                            scope.launch { snackbarHostState.showSnackbar("${feature.name} removed from Voidcraft.") }
                        },
                        onOpen = {
                            val feature = selectedFeature!!
                            if (feature.id == "theme-neon-night" || feature.id == "theme-void-minimal") {
                                activeTheme = feature.id
                                prefs.edit().putString("active_theme", activeTheme).apply()
                                saveInstalled(installedIds + feature.id)
                                scope.launch { snackbarHostState.showSnackbar("${feature.name} applied.") }
                            } else {
                                scope.launch { snackbarHostState.showSnackbar("Opened ${feature.name} details. Full feature integration is not implemented yet.") }
                            }
                        }
                    )
                } else {
                    StoreScreen(
                        features = catalog,
                        installedIds = installedIds,
                        onSelect = { selectedFeature = it }
                    )
                }
            } else {
                Header()
                Spacer(Modifier.height(24.dp))
                when (LauncherTab.entries[selectedTab]) {
                    LauncherTab.Home -> HomeScreen(
                        storeEnabled = storeEnabled,
                        onPlayClick = {
                            scope.launch { snackbarHostState.showSnackbar("Game engine integration is next. Nothing has launched yet.") }
                        },
                        onVersionsClick = { selectedTab = 1 },
                        onSettingsClick = { selectedTab = 3 },
                        onStoreClick = {
                            if (!storeEnabled) {
                                storeEnabled = true
                                prefs.edit().putBoolean(STORE_ENABLED, true).apply()
                            }
                            storeOpen = true
                        }
                    )
                    LauncherTab.Versions -> PlaceholderScreen(
                        icon = Icons.Default.SportsEsports,
                        title = "Game versions",
                        description = "Choose and manage Minecraft Java versions here. Version installation is not connected yet."
                    )
                    LauncherTab.Mods -> PlaceholderScreen(
                        icon = Icons.Default.Extension,
                        title = "Your mod library",
                        description = "Mod browsing and profile management will be added after the launcher engine is integrated."
                    )
                    LauncherTab.Settings -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlaceholderScreen(
                            icon = Icons.Default.Tune,
                            title = "Launcher settings",
                            description = "Account sign-in, memory allocation, Java runtime and controls will be configured here."
                        )
                        OutlinedButton(
                            onClick = {
                                storeOpen = true
                                if (!storeEnabled) {
                                    storeEnabled = true
                                    prefs.edit().putBoolean(STORE_ENABLED, true).apply()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (storeEnabled) "OPEN FEATURE STORE" else "INSTALL FEATURE STORE") }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    }
}

private fun defaultCatalog() = listOf(
    StoreFeature("theme-neon-night", "Neon Night", "A neon accent theme for Voidcraft.", "A lightweight built-in appearance option. Installing enables the feature flag; advanced theme customization is planned.", "launcher", "Appearance", "1.0", "All supported devices", "Voidcraft", null, "available", true),
    StoreFeature("theme-void-minimal", "Void Minimal", "A quieter, minimal launcher look.", "A built-in appearance option with minimal styling. Advanced theme customization is planned.", "launcher", "Appearance", "1.0", "All supported devices", "Voidcraft", null, "available", true),
    StoreFeature("theme-studio", "Theme Studio", "Design and preview custom themes.", "Legacy catalog listing. The editor is not implemented in this clean-start build.", "launcher", "Appearance", "Preview", "Not verified", "Not specified", null, "preview", false),
    StoreFeature("performance-advisor", "Performance Advisor", "Device-aware performance recommendations.", "Legacy catalog listing. No performance changes are applied by this listing yet.", "launcher", "Performance", "Preview", "Not verified", "Not specified", null, "preview", false),
    StoreFeature("backup-manager", "World Backup Manager", "Manage backups of your Minecraft worlds.", "Legacy catalog listing. World backup/restore is not integrated into this clean-start build yet.", "launcher", "Worlds", "Preview", "Not verified", "Not specified", null, "preview", false),
    StoreFeature("friends-chat", "Friends & Chat", "A community and social hub.", "Legacy catalog listing. Online services and chat are not implemented.", "launcher", "Community", "Preview", "Not verified", "Not specified", null, "coming_soon", false),
    StoreFeature("server-status", "Server Status", "Keep an eye on your favorite servers.", "Legacy catalog listing. Live server queries are not implemented.", "launcher", "Multiplayer", "Preview", "Not verified", "Not specified", null, "preview", false),
    StoreFeature("creator-packs", "Creator Packs", "Discover creator-made content.", "Legacy catalog listing. Package and creator verification are not configured yet.", "content", "Content", "Preview", "Not verified", "Not specified", null, "coming_soon", false),
    StoreFeature("mod-content", "Mods & Modpacks", "Add community game content.", "Content packages can only be downloaded after a real HTTPS package URL is published. Automatic installation into a Minecraft profile is not yet integrated.", "content", "Mods", "Preview", "Depends on Minecraft version and loader", "Varies by creator", null, "preview", false),
    StoreFeature("resource-packs", "Resource Packs", "Change textures and sounds.", "Content packages can only be downloaded after a real HTTPS package URL is published. Game-folder integration is not yet implemented.", "content", "Resource Packs", "Preview", "Depends on Minecraft version", "Varies by creator", null, "preview", false),
    StoreFeature("shader-packs", "Shader Packs", "Explore visual enhancements.", "Content packages can only be downloaded after a real HTTPS package URL is published. Renderer compatibility is not verified.", "content", "Shaders", "Preview", "Depends on renderer and device", "Varies by creator", null, "preview", false),
    StoreFeature("maps-worlds", "Maps & Worlds", "Discover community-made worlds.", "Content packages can only be downloaded after a real HTTPS package URL is published. Safe world import is not yet integrated.", "content", "Worlds", "Preview", "Depends on Minecraft version", "Varies by creator", null, "preview", false)
)

private fun parseCatalog(body: String): List<StoreFeature> {
    val root = JSONObject(body)
    val items = root.optJSONArray("items") ?: return emptyList()
    return buildList {
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            val name = item.optString("name").trim()
            if (id.isBlank() || name.isBlank()) continue
            add(
                StoreFeature(
                    id = id,
                    name = name,
                    tagline = item.optString("tagline", "Community feature"),
                    description = item.optString("description", "No description provided."),
                    kind = item.optString("kind", "feature"),
                    category = item.optString("category", item.optString("kind", "Feature")),
                    version = item.optString("version", "Unknown"),
                    compatibility = item.optString("compatibility", "Not specified"),
                    license = item.optString("license", "Not specified"),
                    packageUrl = item.optString("packageUrl").takeIf { it.startsWith("https://") },
                    availability = item.optString("availability", "preview"),
                    builtIn = item.optBoolean("builtIn", false)
                )
            )
        }
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, Color(0xFF5A4EC4))), RoundedCornerShape(15.dp))
                    .padding(11.dp),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Build, contentDescription = null, tint = Color.White) }
            Column {
                Text("VOIDCRAFT", fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text("JAVA EDITION LAUNCHER", fontSize = 10.sp, color = VoidMuted, letterSpacing = 1.2.sp)
            }
        }
        Surface(shape = CircleShape, color = Color(0xFF20283A)) {
            Icon(Icons.Default.CloudOff, contentDescription = "Offline mode status", tint = VoidMint, modifier = Modifier.padding(11.dp))
        }
    }
}

@Composable
private fun HomeScreen(
    storeEnabled: Boolean,
    onPlayClick: () -> Unit,
    onVersionsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onStoreClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("YOUR NEXT WORLD\nAWAITS.", fontSize = 31.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold)
        Text("A fresh home for Minecraft Java on Android.", color = VoidMuted, fontSize = 14.sp)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = VoidSurface)
        ) {
            Column(
                modifier = Modifier.background(Brush.verticalGradient(listOf(Color(0xFF252A45), VoidSurface, VoidSurface))).padding(22.dp)
            ) {
                Surface(color = Color(0xFF33304F), shape = RoundedCornerShape(50)) {
                    Text("  EARLY DEVELOPMENT  ", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp))
                }
                Spacer(Modifier.height(24.dp))
                Text("Ready when you are.", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("The interface is taking shape. The Minecraft runtime and sign-in flow are not connected yet.", color = VoidMuted, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(22.dp))
                Button(onClick = onPlayClick, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                    Icon(Icons.Default.SportsEsports, contentDescription = null)
                    Text("  PLAY", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                }
            }
        }
        Text("QUICK ACCESS", fontSize = 12.sp, color = VoidMuted, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickCard(Modifier.weight(1f), Icons.Default.CheckCircle, "Versions", "Game profiles", onVersionsClick)
            QuickCard(Modifier.weight(1f), Icons.Default.Tune, "Settings", "Tune your setup", onSettingsClick)
        }
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1930))) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Feature Store", fontWeight = FontWeight.Bold)
                        Text(if (storeEnabled) "Optional features and community content" else "Install only when you want to browse features", color = VoidMuted, fontSize = 12.sp)
                    }
                }
                Button(onClick = onStoreClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Icon(if (storeEnabled) Icons.Default.ShoppingBag else Icons.Default.Download, contentDescription = null)
                    Text(if (storeEnabled) "  OPEN FEATURE STORE" else "  INSTALL FEATURE STORE")
                }
                Text("The Store stays disabled until you choose to open it. Preview listings are clearly marked and are not installed automatically.", color = VoidMuted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun QuickCard(modifier: Modifier, icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(modifier = modifier, onClick = onClick, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = VoidSurface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, color = VoidMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun StoreHeader(onBack: () -> Unit, onRefresh: () -> Unit, isRefreshing: Boolean, onDisable: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null); Text(" Back") }
            Column(Modifier.weight(1f)) {
                Text("FEATURE STORE", fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                Text("Old listings + new community releases", color = VoidMuted, fontSize = 11.sp)
            }
            OutlinedButton(onClick = onRefresh, enabled = !isRefreshing) {
                if (isRefreshing) CircularProgressIndicator(Modifier.height(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, contentDescription = "Refresh catalog")
            }
        }
        OutlinedButton(onClick = onDisable, modifier = Modifier.fillMaxWidth()) { Text("DISABLE FEATURE STORE") }
    }
}

@Composable
private fun StoreScreen(features: List<StoreFeature>, installedIds: Set<String>, onSelect: (StoreFeature) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Discover features", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
        Text("Nothing installs automatically. Open a listing to review availability, compatibility, and license.", color = VoidMuted, fontSize = 13.sp, lineHeight = 19.sp)
        val groups = features.groupBy { it.category }
        groups.forEach { (category, entries) ->
            Text(category.uppercase(), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            entries.forEach { feature ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelect(feature) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = VoidSurface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(feature.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                when {
                                    feature.id in installedIds -> "INSTALLED"
                                    feature.availability == "available" && (feature.builtIn || !feature.packageUrl.isNullOrBlank()) -> "AVAILABLE"
                                    feature.availability == "coming_soon" -> "COMING SOON"
                                    else -> "PREVIEW"
                                },
                                color = if (feature.id in installedIds) VoidMint else VoidMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(feature.tagline, color = VoidMuted, fontSize = 12.sp)
                        Text("${feature.kind.uppercase()}  ·  v${feature.version}", color = VoidMuted, fontSize = 10.sp)
                        Text("View details →", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Text("Catalog refreshes from the repository when online. If refresh fails, bundled listings remain visible.", color = VoidMuted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun FeatureDetail(
    feature: StoreFeature,
    installed: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onOpen: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(feature.name, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text(feature.tagline, color = VoidMuted, fontSize = 14.sp)
        Surface(color = VoidSurface, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(feature.description, fontSize = 14.sp, lineHeight = 21.sp)
                DetailLine("Type", feature.kind)
                DetailLine("Version", feature.version)
                DetailLine("Compatibility", feature.compatibility)
                DetailLine("License", feature.license)
                DetailLine("Availability", feature.availability.replace('_', ' '))
                if (!feature.packageUrl.isNullOrBlank()) DetailLine("Package", "HTTPS download")
                else DetailLine("Package", if (feature.builtIn) "Included launcher component" else "Not published")
            }
        }
        when {
            installed -> {
                Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("OPEN FEATURE") }
                OutlinedButton(onClick = onUninstall, modifier = Modifier.fillMaxWidth()) { Text("UNINSTALL") }
            }
            feature.availability == "available" && (feature.builtIn || !feature.packageUrl.isNullOrBlank()) -> {
                Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) { Text("INSTALL FEATURE") }
            }
            else -> {
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("NOT INSTALLABLE YET") }
            }
        }
        if (!feature.builtIn && !feature.packageUrl.isNullOrBlank()) {
            Text("Downloaded packages are stored inside Voidcraft's private app storage. This bootstrap does not yet install mods into a Minecraft profile or dynamically load native launcher code.", color = VoidMuted, fontSize = 11.sp, lineHeight = 16.sp)
        }
        if (!feature.builtIn && feature.packageUrl.isNullOrBlank()) {
            Text("This is a catalog preview only. A reviewed HTTPS package URL and the relevant feature integration must be published before installation can work.", color = VoidMuted, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = VoidMuted, fontSize = 12.sp, modifier = Modifier.weight(0.8f))
        Text(value, fontSize = 12.sp, modifier = Modifier.weight(1.2f))
    }
}

@Composable
private fun PlaceholderScreen(icon: ImageVector, title: String, description: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Surface(color = VoidSurface, shape = RoundedCornerShape(22.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(22.dp))
        }
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text(description, color = VoidMuted, fontSize = 14.sp, lineHeight = 21.sp)
        OutlinedButton(onClick = {}, enabled = false, shape = RoundedCornerShape(14.dp)) { Text("COMING IN A FUTURE BUILD") }
    }
}
