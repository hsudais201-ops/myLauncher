package com.mflegend.flamelauncher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
                    1 -> InfoScreen("Game versions", "Browse and install supported Minecraft Java versions. Version downloads will be connected after the Android storage and engine layers are ready.")
                    2 -> InfoScreen("Your library", "Mods, resource packs, worlds and local profiles will appear here after Android document-provider support is implemented.")
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
            Text("Your next world
starts here.", color = Color.White, fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
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
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(detail, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SettingsScreen(folderUri: String?, error: String?, onChooseFolder: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Settings", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("GAME STORAGE", color = Flame, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("Choose a folder for Flame Launcher to request persistent Android document-provider access. This saves permission only; game file management is not connected yet.", color = Muted, fontSize = 13.sp)
            Button(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) {
                Text(if (folderUri == null) "Choose game folder" else "Change game folder")
            }
            if (folderUri != null) {
                Text("Selected folder permission saved", color = Color(0xFF8DD9A8), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(folderUri, color = Muted, fontSize = 11.sp)
            } else {
                Text("No game folder selected", color = Muted, fontSize = 12.sp)
            }
            if (error != null) {
                Text(error, color = Color(0xFFFF9A82), fontSize = 12.sp)
            }
        }
    }
    InfoScreen("Device settings", "Android Java runtime, memory limits, graphics and touch-control preferences will be added in later stages.")
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
