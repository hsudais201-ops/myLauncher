package com.voidcraft.launcher

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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
import kotlin.math.roundToInt

private const val SETTINGS_PREFS = "voidcraft_preferences"
private const val RELEASE_API = "https://api.github.com/repos/hsudais201-ops/myLauncher/releases/latest"
private const val MAX_IMPORTED_FILE_BYTES = 200L * 1024L * 1024L
private const val MAX_SKIN_FILE_BYTES = 8L * 1024L * 1024L

private val libraryFolders = listOf("mods", "resourcepacks", "shaderpacks", "worlds", "screenshots", "logs")
private val libraryExtensions = mapOf(
    "mods" to setOf("jar"),
    "resourcepacks" to setOf("zip"),
    "shaderpacks" to setOf("zip"),
    "worlds" to setOf("zip"),
    "screenshots" to setOf("png", "jpg", "jpeg", "webp"),
    "logs" to setOf("txt", "log")
)

@Composable
fun LauncherSettingsScreen(onOpenFeatureStore: () -> Unit) {
    var page by remember { mutableStateOf("overview") }
    when (page) {
        "profile" -> ProfileSettingsScreen(onBack = { page = "overview" })
        "performance" -> PerformanceSettingsScreen(onBack = { page = "overview" })
        "controls" -> TouchControlsSettingsScreen(onBack = { page = "overview" })
        "files" -> LocalFileLibraryScreen(onBack = { page = "overview" })
        "skin" -> LocalSkinStudioScreen(onBack = { page = "overview" })
        "updates" -> ReleaseCheckerScreen(onBack = { page = "overview" })
        "runtime" -> JavaRuntimeManagerScreen(onBack = { page = "overview" })
        "render" -> RenderBackendScreen(onBack = { page = "overview" })
        else -> SettingsOverviewScreen(
            onProfile = { page = "profile" },
            onPerformance = { page = "performance" },
            onControls = { page = "controls" },
            onFiles = { page = "files" },
            onSkin = { page = "skin" },
            onUpdates = { page = "updates" },
            onRuntime = { page = "runtime" },
            onRender = { page = "render" },
            onStore = onOpenFeatureStore
        )
    }
}

@Composable
private fun SettingsOverviewScreen(
    onProfile: () -> Unit,
    onPerformance: () -> Unit,
    onControls: () -> Unit,
    onFiles: () -> Unit,
    onSkin: () -> Unit,
    onUpdates: () -> Unit,
    onRuntime: () -> Unit,
    onRender: () -> Unit,
    onStore: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Launcher settings", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "Personalize Voidcraft on this device. Options that depend on a Minecraft runtime are saved for later integration and are labelled below.",
            color = VoidMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        SettingsTile("Offline profile", "Save a local player name for a future offline-capable runtime.", onProfile)
        SettingsTile("Performance advisor", "View device memory and save a preferred memory budget.", onPerformance)
        SettingsTile("Touch controls", "Save layout visibility, handedness, and sensitivity preferences.", onControls)
        SettingsTile("Local file library", "Import and organize local mod, resource-pack, shader, world, screenshot, and log files.", onFiles)
        SettingsTile("Skin Studio", "Import and validate a classic 64×64 or legacy 64×32 Minecraft skin PNG.", onSkin)
        SettingsTile("Check for updates", "Check the GitHub release feed and open published release notes.", onUpdates)
        SettingsTile("Java Runtime Manager", "Install and verify Android Java 8, 17, 21, and 25 runtimes where supported.", onRuntime)
        SettingsTile("Render Backend", "Run an OpenGL ES 2.0 GPU surface and shader smoke test.", onRender)
        OutlinedButton(onClick = onStore, modifier = Modifier.fillMaxWidth()) {
            Text("OPEN FEATURE STORE")
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = VoidSurface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("What is still required", fontWeight = FontWeight.Bold)
                Text(
                    "The Java Runtime Manager can install and verify several Android Java versions. The Versions screen can download the official client JAR, Android-compatible library JAR artifacts, asset index, and referenced asset objects with SHA-1 checks. The Android LWJGL/native compatibility engine, secure Microsoft authentication, inherited/loader-specific version resolution, launch command, and game surface/input lifecycle are still not integrated. These tools do not bypass account authentication.",
                    color = VoidMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsTile(title: String, description: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = VoidSurface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(description, color = VoidMuted, fontSize = 12.sp, lineHeight = 17.sp)
            Text("OPEN  →", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BackLine(title: String, onBack: () -> Unit) {
    OutlinedButton(onClick = onBack) { Text("← BACK") }
    Text(title, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
}

@Composable
private fun ProfileSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE) }
    var playerName by remember { mutableStateOf(prefs.getString("offline_profile_name", "Player") ?: "Player") }
    var status by remember { mutableStateOf("This name is stored only on this device.") }
    var engineAccountStatus by remember { mutableStateOf<String?>(null) }
    val valid = playerName.matches(Regex("^[A-Za-z0-9_]{3,16}$"))

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackLine("Offline profile", onBack)
        Text(
            "Choose the local player name to use if a compatible offline profile mode is connected later.",
            color = VoidMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        OutlinedTextField(
            value = playerName,
            onValueChange = { playerName = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(16) },
            label = { Text("Minecraft player name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("3–16 letters, numbers, or underscores.") }
        )
        Button(
            onClick = {
                prefs.edit().putString("offline_profile_name", playerName.trim()).apply()
                status = "Saved local profile name: " + playerName.trim() + "."
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth()
        ) { Text("SAVE LOCAL PROFILE") }
        Text(status, color = VoidMuted, fontSize = 12.sp)
        Button(
            onClick = {
                try {
                    context.startActivity(Intent().setClassName(context, "net.kdt.pojavlaunch.LauncherActivity"))
                    engineAccountStatus = "Opened the engine account/profile screen. Sign in through its official supported flow; this local name does not grant online access."
                } catch (_: ActivityNotFoundException) {
                    engineAccountStatus = "The account manager is available in the combined engine build, not in the standalone Voidcraft prototype."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("OPEN ENGINE ACCOUNT & PROFILE MANAGER") }
        engineAccountStatus?.let { Text(it, color = VoidMuted, fontSize = 11.sp, lineHeight = 16.sp) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = VoidSurface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Account safety", fontWeight = FontWeight.Bold)
                Text(
                    "This name is a local preference only. Use the integrated engine's account/profile manager for official sign-in and supported offline profiles. Voidcraft does not bypass Microsoft authentication.",
                    color = VoidMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun PerformanceSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE) }
    val memory = remember {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        Pair(
            (info.totalMem / (1024L * 1024L)).toInt().coerceAtLeast(512),
            (info.availMem / (1024L * 1024L)).toInt().coerceAtLeast(0)
        )
    }
    val maxRamMb = maxOf(512, minOf(4096, (memory.first * 0.60).toInt()))
    val recommendedRamMb = minOf(maxRamMb, maxOf(512, (memory.first * 0.30).toInt()))
    var allocationMb by remember {
        mutableIntStateOf(prefs.getInt("preferred_game_memory_mb", recommendedRamMb).coerceIn(512, maxRamMb))
    }
    var saved by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackLine("Performance advisor", onBack)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = VoidSurface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("DEVICE MEMORY", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text("Installed RAM: ${memory.first} MB", fontWeight = FontWeight.SemiBold)
                Text("Currently available RAM: ${memory.second} MB", color = VoidMuted, fontSize = 12.sp)
                Text("Suggested initial budget: ${recommendedRamMb} MB", color = VoidMuted, fontSize = 12.sp)
            }
        }
        Text("Preferred game memory: ${allocationMb} MB", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Slider(
            value = allocationMb.toFloat(),
            onValueChange = { allocationMb = it.roundToInt().coerceIn(512, maxRamMb) },
            valueRange = 512f..maxRamMb.toFloat()
        )
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("512 MB", color = VoidMuted, fontSize = 11.sp)
            Text("${maxRamMb} MB", color = VoidMuted, fontSize = 11.sp)
        }
        Button(
            onClick = {
                prefs.edit().putInt("preferred_game_memory_mb", allocationMb).apply()
                saved = true
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("SAVE MEMORY PREFERENCE") }
        if (saved) Text("Saved on this device.", color = VoidMuted, fontSize = 12.sp)
        Button(
            onClick = {
                try {
                    context.startActivity(Intent().setClassName(context, "net.kdt.pojavlaunch.LauncherActivity"))
                } catch (_: ActivityNotFoundException) {
                    saved = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("OPEN ENGINE PERFORMANCE & GAME SETTINGS") }
        Text(
            "The slider saves Voidcraft's suggested memory budget. The integrated engine's own settings control the actual Java runtime and game memory allocation.",
            color = VoidMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun TouchControlsSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE) }
    var showControls by remember { mutableStateOf(prefs.getBoolean("touch_controls_enabled", true)) }
    var leftHanded by remember { mutableStateOf(prefs.getBoolean("touch_controls_left_handed", false)) }
    var opacity by remember { mutableFloatStateOf(prefs.getFloat("touch_controls_opacity", 0.85f).coerceIn(0.25f, 1f)) }
    var sensitivity by remember { mutableFloatStateOf(prefs.getFloat("touch_controls_sensitivity", 1f).coerceIn(0.5f, 2f)) }
    var saved by remember { mutableStateOf(false) }
    var engineControlStatus by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackLine("Touch controls", onBack)
        Text(
            "Set your preferred touch layout before the Minecraft input overlay is connected.",
            color = VoidMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Show touch controls", fontWeight = FontWeight.SemiBold)
                Text("Default preference for the future in-game overlay.", color = VoidMuted, fontSize = 11.sp)
            }
            Switch(checked = showControls, onCheckedChange = { showControls = it; saved = false })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Left-handed layout", fontWeight = FontWeight.SemiBold)
                Text("Mirror the intended movement/action sides.", color = VoidMuted, fontSize = 11.sp)
            }
            Switch(checked = leftHanded, onCheckedChange = { leftHanded = it; saved = false })
        }
        Text("Control opacity: ${(opacity * 100).roundToInt()}%", fontWeight = FontWeight.SemiBold)
        Slider(value = opacity, onValueChange = { opacity = it; saved = false }, valueRange = 0.25f..1f)
        Text("Input sensitivity: ${(sensitivity * 100).roundToInt()}%", fontWeight = FontWeight.SemiBold)
        Slider(value = sensitivity, onValueChange = { sensitivity = it; saved = false }, valueRange = 0.5f..2f)
        Button(
            onClick = {
                prefs.edit()
                    .putBoolean("touch_controls_enabled", showControls)
                    .putBoolean("touch_controls_left_handed", leftHanded)
                    .putFloat("touch_controls_opacity", opacity)
                    .putFloat("touch_controls_sensitivity", sensitivity)
                    .apply()
                saved = true
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("SAVE CONTROL PREFERENCES") }
        if (saved) Text("Saved on this device.", color = VoidMuted, fontSize = 12.sp)
        Button(
            onClick = {
                try {
                    context.startActivity(Intent().setClassName(context, "net.kdt.pojavlaunch.CustomControlsActivity"))
                    engineControlStatus = "Opened the engine's real control-layout editor. Changes there are separate from the saved preferences above."
                } catch (_: ActivityNotFoundException) {
                    engineControlStatus = "The engine control editor is available in the combined engine build, not in the standalone Voidcraft prototype."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("OPEN IN-GAME CONTROL EDITOR") }
        engineControlStatus?.let { Text(it, color = VoidMuted, fontSize = 11.sp, lineHeight = 16.sp) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = VoidSurface)
        ) {
            Text(
                "The switches and sliders above save Voidcraft preferences only. Use the engine control editor to configure actual in-game buttons and layout.",
                modifier = Modifier.padding(16.dp),
                color = VoidMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun LocalFileLibraryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = remember { resolveEngineStorageDirectory(context, "DIR_GAME_NEW", "minecraft").apply { mkdirs() } }
    var currentFolder by remember { mutableStateOf("mods") }
    var refreshKey by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Imports target the active Minecraft game directory in the integrated build, with a private fallback in the standalone prototype.") }
    var deleteCandidate by remember { mutableStateOf<File?>(null) }
    val currentDirectory = remember(root, currentFolder) {
        val diskFolder = if (currentFolder == "worlds") "saves" else currentFolder
        File(root, diskFolder).apply { mkdirs() }
    }
    val entries = remember(currentDirectory, refreshKey) {
        currentDirectory.listFiles()?.filter { it.isFile }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                status = "Importing file…"
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val originalName = readDisplayName(context, uri)
                        val safeName = File(originalName).name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
                        if (safeName.isBlank() || safeName == "." || safeName == "..") error("Invalid file name.")
                        val extension = safeName.substringAfterLast('.', "").lowercase()
                        val allowed = libraryExtensions[currentFolder] ?: emptySet()
                        if (extension !in allowed) error("For ${currentFolder}, choose: ${allowed.joinToString(", ")}.")
                        var destination = File(currentDirectory, safeName)
                        if (destination.exists()) {
                            val stem = safeName.substringBeforeLast('.', safeName)
                            destination = File(currentDirectory, "$stem-${System.currentTimeMillis()}.$extension")
                        }
                        try {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                destination.outputStream().use { output ->
                                    val buffer = ByteArray(8192)
                                    var total = 0L
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        total += count
                                        if (total > MAX_IMPORTED_FILE_BYTES) error("Files are limited to 200 MB.")
                                        output.write(buffer, 0, count)
                                    }
                                }
                            } ?: error("Android could not read the selected file.")
                        } catch (error: Exception) {
                            destination.delete()
                            throw error
                        }
                        destination.name
                    }
                }
                result.onSuccess { name ->
                    refreshKey++
                    status = "Imported $name."
                }.onFailure { error ->
                    status = "Import failed: ${error.message ?: "file error"}"
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackLine("Local file library", onBack)
        Text(
            "Choose a folder and import a compatible file. In the integrated build, mods, resource packs, shader packs, worlds, screenshots, and logs go to the active game directory.",
            color = VoidMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        libraryFolders.chunked(3).forEach { group ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                group.forEach { name ->
                    OutlinedButton(
                        onClick = { currentFolder = name; status = "Showing $name." },
                        modifier = Modifier.weight(1f)
                    ) { Text(name.uppercase(), fontSize = 9.sp) }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = VoidSurface)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(currentFolder.uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("${entries.size} file(s)", color = VoidMuted, fontSize = 12.sp)
                Button(onClick = { importFile.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("IMPORT TO ${currentFolder.uppercase()}")
                }
                if (entries.isEmpty()) {
                    Text("No files in this folder yet.", color = VoidMuted, fontSize = 12.sp)
                } else {
                    entries.forEach { file ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(file.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text(formatFileSize(file.length()), color = VoidMuted, fontSize = 10.sp)
                            }
                            OutlinedButton(onClick = { deleteCandidate = file }) { Text("REMOVE", fontSize = 10.sp) }
                        }
                    }
                }
            }
        }
        Text(status, color = VoidMuted, fontSize = 11.sp, lineHeight = 16.sp)
        Text(
            "Accepted files: JAR for mods; ZIP for resource packs, shader packs and world archives; PNG/JPG/WEBP for screenshots; TXT/LOG for logs.",
            color = VoidMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }

    val candidate = deleteCandidate
    if (candidate != null) {
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Remove local file?") },
            text = { Text("Delete ${candidate.name} from Voidcraft's private file library? The original file you imported elsewhere will not be affected.") },
            confirmButton = {
                TextButton(onClick = {
                    if (candidate.delete()) status = "Removed ${candidate.name}." else status = "Could not remove ${candidate.name}."
                    refreshKey++
                    deleteCandidate = null
                }) { Text("REMOVE") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("CANCEL") }
            }
        )
    }
}

@Composable
private fun LocalSkinStudioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val skinFile = remember { File(context.filesDir, "skins/current.png").apply { parentFile?.mkdirs() } }
    var bitmap by remember { mutableStateOf(loadSkinBitmap(skinFile)) }
    var status by remember {
        mutableStateOf(if (skinFile.exists()) "Loaded the saved local skin preview." else "Import a Minecraft skin PNG to preview and keep a local copy.")
    }

    val pickSkin = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                status = "Validating skin PNG…"
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val length = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                        if (length > MAX_SKIN_FILE_BYTES) error("Skin file must be 8 MB or smaller.")
                        val imported = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                            ?: error("Could not decode the selected image.")
                        if (!((imported.width == 64 && imported.height == 64) || (imported.width == 64 && imported.height == 32))) {
                            imported.recycle()
                            error("Minecraft skin dimensions must be 64×64 or legacy 64×32 pixels.")
                        }
                        skinFile.outputStream().use { output ->
                            if (!imported.compress(Bitmap.CompressFormat.PNG, 100, output)) error("Could not save the skin PNG.")
                        }
                        imported
                    }
                }
                result.onSuccess { imported ->
                    bitmap?.recycle()
                    bitmap = imported
                    status = "Validated and saved a local skin preview."
                }.onFailure { error ->
                    status = "Skin import failed: ${error.message ?: "invalid image"}"
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackLine("Skin Studio", onBack)
        Text(
            "Import a skin image and keep a validated local preview on this device.",
            color = VoidMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = VoidSurface)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Imported Minecraft skin preview",
                        modifier = Modifier.size(180.dp)
                    )
                } else {
                    Text("No local skin imported", color = VoidMuted)
                }
                Button(onClick = { pickSkin.launch(arrayOf("image/png")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("IMPORT SKIN PNG")
                }
                if (skinFile.exists()) {
                    OutlinedButton(
                        onClick = {
                            if (skinFile.delete()) {
                                bitmap?.recycle()
                                bitmap = null
                                status = "Removed the saved local skin preview."
                            } else status = "Could not remove the saved skin file."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("REMOVE LOCAL SKIN") }
                }
            }
        }
        Text(status, color = VoidMuted, fontSize = 12.sp, lineHeight = 17.sp)
        Text(
            "This tool validates and previews a local texture only. It does not upload a skin to a Microsoft/Minecraft account or apply it in-game; those actions require a connected authenticated game runtime.",
            color = VoidMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}

private data class ReleaseCheckResult(val message: String, val url: String?)

@Composable
private fun ReleaseCheckerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("No update check has been run yet.") }
    var releaseUrl by remember { mutableStateOf<String?>(null) }

    fun checkRelease() {
        scope.launch {
            checking = true
            result = "Checking GitHub releases…"
            releaseUrl = null
            val lookup = withContext(Dispatchers.IO) { runCatching { fetchLatestRelease() } }
            lookup.onSuccess {
                result = it.message
                releaseUrl = it.url
            }.onFailure {
                result = "Could not check releases: ${it.message ?: "network error"}. Please retry."
            }
            checking = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackLine("Check for updates", onBack)
        Text(
            "Look for a published Voidcraft release and open its release page when one exists.",
            color = VoidMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        Button(onClick = { checkRelease() }, enabled = !checking, modifier = Modifier.fillMaxWidth()) {
            Text(if (checking) "CHECKING…" else "CHECK GITHUB RELEASES")
        }
        Text(result, color = VoidMuted, fontSize = 12.sp, lineHeight = 18.sp)
        if (releaseUrl != null) {
            Button(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)))
                    }.onFailure {
                        result = "Could not open the release page on this device."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("OPEN RELEASE PAGE") }
        }
        Text(
            "The checker does not silently replace the APK or install updates in the background. This build must be updated through a trusted published release or build artifact.",
            color = VoidMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}

private fun fetchLatestRelease(): ReleaseCheckResult {
    val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
        connectTimeout = 7000
        readTimeout = 10000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "Voidcraft-Android-Launcher/0.1")
    }
    try {
        val status = connection.responseCode
        if (status == 404) return ReleaseCheckResult("No public GitHub release has been published for Voidcraft yet.", null)
        if (status !in 200..299) error("GitHub returned HTTP $status")
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val release = JSONObject(body)
        val tag = release.optString("tag_name").ifBlank { "Unknown version" }
        val name = release.optString("name").ifBlank { tag }
        val url = release.optString("html_url").takeIf { it.startsWith("https://github.com/") }
        val published = release.optString("published_at").take(10)
        val message = if (published.isBlank()) "Latest published release: $name ($tag)." else "Latest published release: $name ($tag), published $published."
        return ReleaseCheckResult(message, url)
    } finally {
        connection.disconnect()
    }
}

private fun readDisplayName(context: Context, uri: Uri): String {
    val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    return cursor?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }?.takeIf { it.isNotBlank() } ?: (uri.lastPathSegment?.substringAfterLast('/') ?: "imported-file")
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

private fun loadSkinBitmap(file: File): Bitmap? =
    if (file.exists()) runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull() else null
