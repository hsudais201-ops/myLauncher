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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
val VoidSurface = Color(0xFF141824)
private val VoidAccent = Color(0xFF9B8CFF)
private val VoidMint = Color(0xFF70E1C1)
val VoidMuted = Color(0xFF9AA3B8)
private const val PREFS = "voidcraft_preferences"
private const val STORE_ENABLED = "feature_store_enabled"
private const val INSTALLED_IDS = "installed_feature_ids"
private const val SOCIAL_FRIENDS = "social_friends"
private const val SOCIAL_MESSAGES = "social_messages"
private const val SOCIAL_TOKEN = "social_online_token"
private const val SOCIAL_USERNAME = "social_online_username"
private const val SOCIAL_API_URL = "https://voidcraft-social-hub-staging.hsudais201.workers.dev"
private const val CATALOG_URL = "https://raw.githubusercontent.com/hsudais201-ops/myLauncher/main/store/catalog.json"

data class SocialMessage(val sender: String, val text: String, val sentAt: Long)
data class OnlineFriendRequest(val id: String, val username: String, val createdAt: Long)
data class OnlineSocialMessage(val id: String, val sender: String, val recipient: String, val body: String, val createdAt: Long)

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
    var studioAccent by remember { mutableIntStateOf(prefs.getInt("theme_studio_accent", 0xFF9C7BFF.toInt())) }
    var selectedGameVersion by remember { mutableStateOf(prefs.getString("selected_game_version", "latest") ?: "latest") }
    var catalog by remember { mutableStateOf(loadBundledCatalog(context)) }
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedFeature by remember { mutableStateOf<StoreFeature?>(null) }
    var socialHubOpen by remember { mutableStateOf(false) }
    var serverStateOpen by remember { mutableStateOf(false) }
    var themeStudioOpen by remember { mutableStateOf(false) }
    var cosmeticStoreOpen by remember { mutableStateOf(false) }
    var equippedCosmetic by remember { mutableStateOf(prefs.getString("equipped_cosmetic_id", null)) }
    var activeConversation by remember { mutableStateOf<String?>(null) }
    var socialFriends by remember { mutableStateOf<List<String>>(prefs.getStringSet(SOCIAL_FRIENDS, emptySet<String>())?.toList()?.sorted() ?: emptyList<String>()) }
    var socialMessages by remember { mutableStateOf(loadSocialMessages(prefs.getString(SOCIAL_MESSAGES, "{}") ?: "{}")) }
    var onlineToken by remember { mutableStateOf(prefs.getString(SOCIAL_TOKEN, null)) }
    var onlineUsername by remember { mutableStateOf(prefs.getString(SOCIAL_USERNAME, null)) }
    var onlineFriends by remember { mutableStateOf<List<String>>(emptyList()) }
    var onlineIncoming by remember { mutableStateOf<List<OnlineFriendRequest>>(emptyList()) }
    var onlineOutgoing by remember { mutableStateOf<List<OnlineFriendRequest>>(emptyList()) }
    var onlineMessages by remember { mutableStateOf<Map<String, List<OnlineSocialMessage>>>(emptyMap()) }
    var onlineSocialBusy by remember { mutableStateOf(false) }
    var onlineStatus by remember { mutableStateOf<String?>(null) }
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
        if (remote != null) {
            catalog = remote.distinctBy { it.id }
        }
        isRefreshing = false
    }

    LaunchedEffect(storeOpen) {
        if (storeOpen) refreshCatalog()
    }

    suspend fun refreshOnlineData() {
        val token = onlineToken ?: return
        onlineSocialBusy = true
        try {
            val response = socialApiRequest("GET", "/api/friends", token)
            fun readRequests(key: String): List<OnlineFriendRequest> {
                val rows = response.optJSONArray(key) ?: return emptyList()
                return buildList {
                    for (i in 0 until rows.length()) {
                        val row = rows.optJSONObject(i) ?: continue
                        add(OnlineFriendRequest(row.optString("id"), row.optString("username"), row.optLong("created_at")))
                    }
                }
            }
            val friendRows = response.optJSONArray("friends")
            onlineFriends = buildList {
                if (friendRows != null) for (i in 0 until friendRows.length()) {
                    val row = friendRows.optJSONObject(i) ?: continue
                    val name = row.optString("username")
                    if (name.isNotBlank()) add(name)
                }
            }
            onlineIncoming = readRequests("incoming")
            onlineOutgoing = readRequests("outgoing")
            onlineStatus = "Online friends refreshed."
        } catch (e: Exception) {
            onlineStatus = e.message ?: "Could not refresh online friends."
        } finally {
            onlineSocialBusy = false
        }
    }

    LaunchedEffect(socialHubOpen, onlineToken) {
        if (socialHubOpen && onlineToken != null) refreshOnlineData()
    }

    fun saveInstalled(next: Set<String>) {
        installedIds = next
        prefs.edit().putStringSet(INSTALLED_IDS, next).apply()
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = when (activeTheme) {
                "theme-studio" -> Color(studioAccent)
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
                        onClick = { storeOpen = false; selectedFeature = null; serverStateOpen = false; themeStudioOpen = false; cosmeticStoreOpen = false; selectedTab = index },
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
                    onBack = { selectedFeature = null; socialHubOpen = false; activeConversation = null; storeOpen = false },
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
                    if (socialHubOpen && selectedFeature?.id == "social-hub") {
                        SocialHubScreen(
                            friends = socialFriends,
                            messagesByFriend = socialMessages,
                            selectedFriend = activeConversation,
                            onBack = { socialHubOpen = false; activeConversation = null },
                            onSelectFriend = { activeConversation = it },
                            onAddFriend = { rawName ->
                                val username = rawName.trim()
                                when {
                                    !username.matches(Regex("^[A-Za-z0-9_]{3,24}$")) ->
                                        scope.launch { snackbarHostState.showSnackbar("Use a 3–24 character username: letters, numbers, or underscore.") }
                                    username.equals("You", ignoreCase = true) ->
                                        scope.launch { snackbarHostState.showSnackbar("You cannot add yourself.") }
                                    socialFriends.any { it.equals(username, ignoreCase = true) } ->
                                        scope.launch { snackbarHostState.showSnackbar("That friend is already on your local list.") }
                                    else -> {
                                        socialFriends = (socialFriends + username).sorted()
                                        prefs.edit().putStringSet(SOCIAL_FRIENDS, socialFriends.toSet()).apply()
                                        scope.launch { snackbarHostState.showSnackbar("$username added to your local friends list.") }
                                    }
                                }
                            },
                            onSendMessage = { friend, rawText ->
                                val body = rawText.trim()
                                if (body.isNotEmpty()) {
                                    val updated = socialMessages.toMutableMap()
                                    updated[friend] = (updated[friend] ?: emptyList()) + SocialMessage("You", body, System.currentTimeMillis())
                                    socialMessages = updated
                                    prefs.edit().putString(SOCIAL_MESSAGES, saveSocialMessages(updated)).apply()
                                }
                            },
                            onlineToken = onlineToken,
                            onlineUsername = onlineUsername,
                            onlineFriends = onlineFriends,
                            incomingRequests = onlineIncoming,
                            outgoingRequests = onlineOutgoing,
                            onlineMessages = onlineMessages,
                            onlineBusy = onlineSocialBusy,
                            onlineStatus = onlineStatus,
                            onOnlineAuth = { username, password, createAccount ->
                                scope.launch {
                                    onlineSocialBusy = true
                                    onlineStatus = if (createAccount) "Creating account…" else "Signing in…"
                                    try {
                                        val body = JSONObject().put("username", username).put("password", password)
                                        val response = socialApiRequest(
                                            "POST",
                                            if (createAccount) "/api/register" else "/api/login",
                                            null,
                                            body
                                        )
                                        val token = response.optString("token")
                                        if (token.isBlank()) error("The server did not return a session.")
                                        val user = response.optJSONObject("user")
                                        onlineToken = token
                                        onlineUsername = user?.optString("username")?.takeIf { it.isNotBlank() } ?: username
                                        prefs.edit()
                                            .putString(SOCIAL_TOKEN, token)
                                            .putString(SOCIAL_USERNAME, onlineUsername)
                                            .apply()
                                        onlineStatus = "Signed in as $onlineUsername."
                                        refreshOnlineData()
                                    } catch (e: Exception) {
                                        onlineSocialBusy = false
                                        onlineStatus = e.message ?: "Online sign-in failed."
                                    }
                                }
                            },
                            onOnlineLogout = {
                                scope.launch {
                                    val token = onlineToken
                                    try {
                                        if (token != null) socialApiRequest("POST", "/api/logout", token)
                                    } catch (_: Exception) { }
                                    onlineToken = null
                                    onlineUsername = null
                                    onlineFriends = emptyList()
                                    onlineIncoming = emptyList()
                                    onlineOutgoing = emptyList()
                                    onlineMessages = emptyMap()
                                    prefs.edit().remove(SOCIAL_TOKEN).remove(SOCIAL_USERNAME).apply()
                                    onlineStatus = "Signed out."
                                }
                            },
                            onRefreshOnline = { scope.launch { refreshOnlineData() } },
                            onSendFriendRequest = { username ->
                                scope.launch {
                                    val token = onlineToken
                                    if (token == null) {
                                        onlineStatus = "Sign in to send online friend requests."
                                    } else {
                                        onlineSocialBusy = true
                                        try {
                                            val response = socialApiRequest(
                                                "POST", "/api/friends/requests", token,
                                                JSONObject().put("username", username)
                                            )
                                            onlineStatus = response.optString("message", "Friend request sent.")
                                            refreshOnlineData()
                                        } catch (e: Exception) {
                                            onlineStatus = e.message ?: "Could not send friend request."
                                            onlineSocialBusy = false
                                        }
                                    }
                                }
                            },
                            onRespondFriendRequest = { requestId, accept ->
                                scope.launch {
                                    val token = onlineToken
                                    if (token != null) {
                                        onlineSocialBusy = true
                                        try {
                                            socialApiRequest(
                                                "POST", "/api/friends/requests/$requestId/respond", token,
                                                JSONObject().put("action", if (accept) "accept" else "reject")
                                            )
                                            onlineStatus = if (accept) "Friend request accepted." else "Friend request rejected."
                                            refreshOnlineData()
                                        } catch (e: Exception) {
                                            onlineStatus = e.message ?: "Could not update friend request."
                                            onlineSocialBusy = false
                                        }
                                    }
                                }
                            },
                            onLoadOnlineMessages = { friend ->
                                scope.launch {
                                    val token = onlineToken
                                    if (token != null) {
                                        onlineSocialBusy = true
                                        try {
                                            val response = socialApiRequest(
                                                "GET", "/api/messages?with=${Uri.encode(friend)}&after=0", token
                                            )
                                            val items = response.optJSONArray("messages")
                                            val parsed = buildList {
                                                if (items != null) for (i in 0 until items.length()) {
                                                    val row = items.optJSONObject(i) ?: continue
                                                    add(
                                                        OnlineSocialMessage(
                                                            row.optString("id"),
                                                            row.optString("sender"),
                                                            row.optString("recipient"),
                                                            row.optString("body"),
                                                            row.optLong("created_at")
                                                        )
                                                    )
                                                }
                                            }
                                            onlineMessages = onlineMessages + (friend to parsed)
                                            onlineStatus = "Loaded online chat with $friend."
                                        } catch (e: Exception) {
                                            onlineStatus = e.message ?: "Could not load online messages."
                                        } finally {
                                            onlineSocialBusy = false
                                        }
                                    }
                                }
                            },
                            onSendOnlineMessage = { friend, text ->
                                scope.launch {
                                    val token = onlineToken
                                    if (token != null) {
                                        onlineSocialBusy = true
                                        try {
                                            socialApiRequest(
                                                "POST", "/api/messages", token,
                                                JSONObject().put("username", friend).put("text", text)
                                            )
                                            val response = socialApiRequest(
                                                "GET", "/api/messages?with=${Uri.encode(friend)}&after=0", token
                                            )
                                            val items = response.optJSONArray("messages")
                                            val parsed = buildList {
                                                if (items != null) for (i in 0 until items.length()) {
                                                    val row = items.optJSONObject(i) ?: continue
                                                    add(OnlineSocialMessage(row.optString("id"), row.optString("sender"), row.optString("recipient"), row.optString("body"), row.optLong("created_at")))
                                                }
                                            }
                                            onlineMessages = onlineMessages + (friend to parsed)
                                            onlineStatus = "Message sent to $friend."
                                        } catch (e: Exception) {
                                            onlineStatus = e.message ?: "Could not send message."
                                        } finally {
                                            onlineSocialBusy = false
                                        }
                                    }
                                }
                            }
                        )
                    } else {
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
                            if (feature.id == "cosmetic-store") {
                                equippedCosmetic = null
                                prefs.edit().remove("equipped_cosmetic_id").apply()
                            }
                            scope.launch { snackbarHostState.showSnackbar("${feature.name} removed from Voidcraft.") }
                        },
                        onOpen = {
                            val feature = selectedFeature!!
                            if (feature.id == "social-hub") {
                                socialHubOpen = true
                                activeConversation = null
                            } else if (feature.id == "advanced-mod-manager") {
                                selectedFeature = null
                                socialHubOpen = false
                                serverStateOpen = false
                                storeOpen = false
                                selectedTab = 2
                            } else if (feature.id == "server-state") {
                                selectedFeature = null
                                socialHubOpen = false
                                storeOpen = false
                                serverStateOpen = true
                            } else if (feature.id == "theme-studio") {
                                selectedFeature = null
                                socialHubOpen = false
                                storeOpen = false
                                serverStateOpen = false
                                themeStudioOpen = true
                            } else if (feature.id == "cosmetic-store") {
                                selectedFeature = null
                                socialHubOpen = false
                                storeOpen = false
                                serverStateOpen = false
                                themeStudioOpen = false
                                cosmeticStoreOpen = true
                            } else if (feature.id == "theme-neon-night" || feature.id == "theme-void-minimal") {
                                activeTheme = feature.id
                                prefs.edit().putString("active_theme", activeTheme).apply()
                                saveInstalled(installedIds + feature.id)
                                scope.launch { snackbarHostState.showSnackbar("${feature.name} applied.") }
                            } else {
                                scope.launch { snackbarHostState.showSnackbar("Opened ${feature.name} details. Full feature integration is not implemented yet.") }
                            }
                        }
                    )
                    }
                } else {
                    StoreScreen(
                        features = catalog,
                        installedIds = installedIds,
                        onSelect = { selectedFeature = it }
                    )
                }
            } else if (cosmeticStoreOpen) {
                CosmeticStoreScreen(
                    equippedId = equippedCosmetic,
                    onEquip = { id ->
                        equippedCosmetic = id
                        prefs.edit().putString("equipped_cosmetic_id", id).apply()
                        scope.launch { snackbarHostState.showSnackbar("Cosmetic equipped for your launcher profile.") }
                    },
                    onUnequip = {
                        equippedCosmetic = null
                        prefs.edit().remove("equipped_cosmetic_id").apply()
                        scope.launch { snackbarHostState.showSnackbar("Cosmetic unequipped.") }
                    },
                    onBack = { cosmeticStoreOpen = false }
                )
            } else if (themeStudioOpen) {
                ThemeStudioScreen(
                    currentAccent = studioAccent,
                    onApply = { color ->
                        studioAccent = color
                        activeTheme = "theme-studio"
                        prefs.edit().putInt("theme_studio_accent", color).putString("active_theme", activeTheme).apply()
                        scope.launch { snackbarHostState.showSnackbar("Theme Studio accent applied.") }
                    },
                    onBack = { themeStudioOpen = false }
                )
            } else if (serverStateOpen) {
                ServerStateScreen(onBack = { serverStateOpen = false })
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
                    LauncherTab.Versions -> GameVersionsScreen(
                        selectedVersion = selectedGameVersion,
                        onSelectVersion = { version ->
                            selectedGameVersion = version
                            prefs.edit().putString("selected_game_version", version).apply()
                            scope.launch {
                                snackbarHostState.showSnackbar("Selected Minecraft $version. Game download and launch are not integrated yet.")
                            }
                        }
                    )
                    LauncherTab.Mods -> {
                        if ("advanced-mod-manager" in installedIds) {
                            AdvancedModManagerScreen()
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                PlaceholderScreen(
                                    icon = Icons.Default.Extension,
                                    title = "Advanced Mod Manager",
                                    description = "Install this optional feature from the Feature Store to import and organize your local JAR mod library."
                                )
                                Button(
                                    onClick = {
                                        if (!storeEnabled) {
                                            storeEnabled = true
                                            prefs.edit().putBoolean(STORE_ENABLED, true).apply()
                                        }
                                        val manager = catalog.firstOrNull { it.id == "advanced-mod-manager" }
                                        if (manager != null) selectedFeature = manager
                                        storeOpen = true
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("OPEN FEATURE STORE") }
                            }
                        }
                    }
                    LauncherTab.Settings -> LauncherSettingsScreen(
                        onOpenFeatureStore = {
                            storeOpen = true
                            selectedFeature = null
                            if (!storeEnabled) {
                                storeEnabled = true
                                prefs.edit().putBoolean(STORE_ENABLED, true).apply()
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    }
}



private suspend fun socialApiRequest(
    method: String,
    path: String,
    token: String?,
    body: JSONObject? = null
): JSONObject = withContext(Dispatchers.IO) {
    val connection = (URL(SOCIAL_API_URL + path).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10000
        readTimeout = 15000
        requestMethod = method
        setRequestProperty("Accept", "application/json")
        if (token != null) setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            doOutput = true
        }
    }
    try {
        if (body != null) {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
        val response = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
        if (status !in 200..299) {
            throw IllegalStateException(response.optString("error").takeIf { it.isNotBlank() } ?: "Online request failed (HTTP $status).")
        }
        response
    } finally {
        connection.disconnect()
    }
}


private fun loadSocialMessages(json: String): Map<String, List<SocialMessage>> {
    return runCatching {
        val root = JSONObject(json)
        buildMap {
            val keys = root.keys()
            while (keys.hasNext()) {
                val friend = keys.next()
                val array = root.optJSONArray(friend) ?: continue
                val messages = buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        add(
                            SocialMessage(
                                sender = item.optString("sender", "You"),
                                text = item.optString("text", ""),
                                sentAt = item.optLong("sentAt", 0L)
                            )
                        )
                    }
                }
                put(friend, messages)
            }
        }
    }.getOrDefault(emptyMap())
}

private fun saveSocialMessages(messages: Map<String, List<SocialMessage>>): String {
    val root = JSONObject()
    messages.forEach { (friend, entries) ->
        val array = org.json.JSONArray()
        entries.forEach { message ->
            array.put(JSONObject().put("sender", message.sender).put("text", message.text).put("sentAt", message.sentAt))
        }
        root.put(friend, array)
    }
    return root.toString()
}

@Composable
private fun SocialHubScreen(
    friends: List<String>,
    messagesByFriend: Map<String, List<SocialMessage>>,
    selectedFriend: String?,
    onBack: () -> Unit,
    onSelectFriend: (String) -> Unit,
    onAddFriend: (String) -> Unit,
    onSendMessage: (String, String) -> Unit,
    onlineToken: String?,
    onlineUsername: String?,
    onlineFriends: List<String>,
    incomingRequests: List<OnlineFriendRequest>,
    outgoingRequests: List<OnlineFriendRequest>,
    onlineMessages: Map<String, List<OnlineSocialMessage>>,
    onlineBusy: Boolean,
    onlineStatus: String?,
    onOnlineAuth: (String, String, Boolean) -> Unit,
    onOnlineLogout: () -> Unit,
    onRefreshOnline: () -> Unit,
    onSendFriendRequest: (String) -> Unit,
    onRespondFriendRequest: (String, Boolean) -> Unit,
    onLoadOnlineMessages: (String) -> Unit,
    onSendOnlineMessage: (String, String) -> Unit
) {
    var newUsername by remember { mutableStateOf("") }
    var draftMessage by remember(selectedFriend) { mutableStateOf("") }
    var authUsername by remember { mutableStateOf("") }
    var authPassword by remember { mutableStateOf("") }
    var onlineFriendDraft by remember { mutableStateOf("") }
    var selectedOnlineFriend by remember { mutableStateOf<String?>(null) }
    var onlineDraftMessage by remember(selectedOnlineFriend) { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Text(" Back")
            }
            Column(Modifier.weight(1f)) {
                Text("SOCIAL HUB", fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
                Text("Local contacts + online beta", color = VoidMuted, fontSize = 11.sp)
            }
        }
        Surface(color = Color(0xFF20283A), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("TWO MODES", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                Text("Local contacts and notes stay on this device. Online mode uses a separate beta account and sends messages through the Voidcraft staging service.", color = VoidMuted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        Text("LOCAL FRIENDS", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        OutlinedTextField(
            value = newUsername,
            onValueChange = { newUsername = it.take(24) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Local username") },
            placeholder = { Text("Example: Alex_123") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )
        Button(
            onClick = {
                if (newUsername.trim().matches(Regex("^[A-Za-z0-9_]{3,24}$"))) {
                    onAddFriend(newUsername.trim())
                    newUsername = ""
                }
            },
            enabled = newUsername.trim().matches(Regex("^[A-Za-z0-9_]{3,24}$")),
            modifier = Modifier.fillMaxWidth()
        ) { Text("ADD LOCAL CONTACT") }
        if (friends.isEmpty()) {
            Surface(color = VoidSurface, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Your local list is empty", fontWeight = FontWeight.Bold)
                    Text("Local contacts are not real friend requests and cannot receive messages from other devices.", color = VoidMuted, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        } else {
            friends.forEach { friend ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelectFriend(friend) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (selectedFriend == friend) Color(0xFF252A45) else VoidSurface)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(color = Color(0xFF302B50), shape = CircleShape) {
                            Text(friend.take(1).uppercase(), modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(friend, fontWeight = FontWeight.Bold)
                            Text("${(messagesByFriend[friend] ?: emptyList()).size} local notes", color = VoidMuted, fontSize = 11.sp)
                        }
                        Text(if (selectedFriend == friend) "OPEN" else "NOTES →", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (selectedFriend != null && friends.contains(selectedFriend)) {
            val chat = messagesByFriend[selectedFriend] ?: emptyList()
            Surface(color = VoidSurface, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("LOCAL NOTES WITH $selectedFriend".uppercase(), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                    if (chat.isEmpty()) Text("No notes yet.", color = VoidMuted, fontSize = 12.sp)
                    chat.takeLast(30).forEach { message ->
                        Surface(color = Color(0xFF20283A), shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(message.sender, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text(message.text, fontSize = 13.sp, lineHeight = 18.sp)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = draftMessage,
                        onValueChange = { draftMessage = it.take(500) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Local note") },
                        placeholder = { Text("Write a note") },
                        shape = RoundedCornerShape(14.dp)
                    )
                    Button(
                        onClick = {
                            val body = draftMessage.trim()
                            if (body.isNotEmpty()) { onSendMessage(selectedFriend, body); draftMessage = "" }
                        },
                        enabled = draftMessage.trim().isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("SAVE LOCAL NOTE") }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("ONLINE COMMUNITY · BETA", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Surface(color = VoidSurface, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onlineToken == null || onlineUsername.isNullOrBlank()) {
                    Text("Create a beta account or sign in", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("Use a unique password of at least 12 characters. This staging beta has no email verification or password recovery. Do not reuse another account's password.", color = VoidMuted, fontSize = 12.sp, lineHeight = 18.sp)
                    OutlinedTextField(
                        value = authUsername,
                        onValueChange = { authUsername = it.take(24) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Online username") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedTextField(
                        value = authPassword,
                        onValueChange = { authPassword = it.take(128) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Password (12+ characters)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onOnlineAuth(authUsername.trim(), authPassword, false) },
                            enabled = !onlineBusy && authUsername.trim().matches(Regex("^[A-Za-z0-9_]{3,24}$")) && authPassword.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text("SIGN IN") }
                        OutlinedButton(
                            onClick = { onOnlineAuth(authUsername.trim(), authPassword, true) },
                            enabled = !onlineBusy && authUsername.trim().matches(Regex("^[A-Za-z0-9_]{3,24}$")) && authPassword.length >= 12,
                            modifier = Modifier.weight(1f)
                        ) { Text("CREATE") }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Signed in as $onlineUsername", fontWeight = FontWeight.Bold)
                            Text("Voidcraft staging account", color = VoidMuted, fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = onOnlineLogout, enabled = !onlineBusy) { Text("SIGN OUT") }
                    }
                    OutlinedButton(onClick = onRefreshOnline, enabled = !onlineBusy, modifier = Modifier.fillMaxWidth()) {
                        if (onlineBusy) CircularProgressIndicator(Modifier.height(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = null)
                        Text("  REFRESH ONLINE FRIENDS")
                    }
                    if (incomingRequests.isNotEmpty()) {
                        Text("INCOMING REQUESTS", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        incomingRequests.forEach { request ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(request.username, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                Button(onClick = { onRespondFriendRequest(request.id, true) }, enabled = !onlineBusy) { Text("ACCEPT") }
                                OutlinedButton(onClick = { onRespondFriendRequest(request.id, false) }, enabled = !onlineBusy) { Text("NO") }
                            }
                        }
                    }
                    if (outgoingRequests.isNotEmpty()) {
                        Text("PENDING REQUESTS", color = VoidMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        outgoingRequests.forEach { Text("Waiting for ${it.username}", color = VoidMuted, fontSize = 12.sp) }
                    }
                    Text("ADD ONLINE FRIEND", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = onlineFriendDraft,
                        onValueChange = { onlineFriendDraft = it.take(24) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Exact online username") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Button(
                        onClick = { onSendFriendRequest(onlineFriendDraft.trim()); onlineFriendDraft = "" },
                        enabled = !onlineBusy && onlineFriendDraft.trim().matches(Regex("^[A-Za-z0-9_]{3,24}$")),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("SEND FRIEND REQUEST") }
                    Text("ONLINE FRIENDS (${onlineFriends.size})", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    if (onlineFriends.isEmpty()) Text("No accepted online friends yet.", color = VoidMuted, fontSize = 12.sp)
                    onlineFriends.forEach { friend ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { selectedOnlineFriend = friend; onLoadOnlineMessages(friend) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = if (selectedOnlineFriend == friend) Color(0xFF252A45) else Color(0xFF20283A))
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(friend, fontWeight = FontWeight.Bold)
                                    Text("Open online chat", color = VoidMuted, fontSize = 11.sp)
                                }
                                Text(if (selectedOnlineFriend == friend) "OPEN" else "CHAT →", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    val activeOnlineFriend = selectedOnlineFriend
                    if (activeOnlineFriend != null && onlineFriends.contains(activeOnlineFriend)) {
                        val chat = onlineMessages[activeOnlineFriend] ?: emptyList()
                        Text("ONLINE CHAT WITH $activeOnlineFriend".uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        OutlinedButton(onClick = { onLoadOnlineMessages(activeOnlineFriend) }, enabled = !onlineBusy, modifier = Modifier.fillMaxWidth()) { Text("REFRESH CHAT") }
                        if (chat.isEmpty()) Text("No online messages yet.", color = VoidMuted, fontSize = 12.sp)
                        chat.takeLast(50).forEach { message ->
                            Surface(
                                color = if (message.sender == onlineUsername) Color(0xFF302B50) else Color(0xFF20283A),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(message.sender, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    Text(message.body, fontSize = 13.sp, lineHeight = 18.sp)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = onlineDraftMessage,
                            onValueChange = { onlineDraftMessage = it.take(2000) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Online message") },
                            shape = RoundedCornerShape(14.dp)
                        )
                        Button(
                            onClick = {
                                val body = onlineDraftMessage.trim()
                                if (body.isNotEmpty()) { onSendOnlineMessage(activeOnlineFriend, body); onlineDraftMessage = "" }
                            },
                            enabled = !onlineBusy && onlineDraftMessage.trim().isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("SEND ONLINE MESSAGE") }
                    }
                }
                val statusText = onlineStatus
                if (!statusText.isNullOrBlank()) {
                    Text(statusText, color = if (statusText.startsWith("Could not") || statusText.contains("failed", true) || statusText.contains("expired", true) || statusText.contains("request failed", true)) Color(0xFFFF9C9C) else VoidMuted, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}

private fun loadBundledCatalog(context: Context): List<StoreFeature> {
    return runCatching {
        context.assets.open("store/catalog.json").bufferedReader().use { parseCatalog(it.readText()) }
    }.getOrDefault(emptyList())
}

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
                Text("Only features published in the catalog appear here", color = VoidMuted, fontSize = 11.sp)
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
        if (features.isEmpty()) {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = VoidSurface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No features added yet", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("When you add a feature to store/catalog.json on the repository, it will appear here after you refresh the Store.", color = VoidMuted, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
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
        Text("Only repository catalog entries are shown. If online refresh fails, the last loaded catalog remains visible.", color = VoidMuted, fontSize = 11.sp, lineHeight = 16.sp)
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
