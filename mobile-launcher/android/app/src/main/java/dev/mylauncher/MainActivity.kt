package dev.mylauncher

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF10120F)
private val Panel = Color(0xFF1B2019)
private val Accent = Color(0xFFB6E36A)
private val Muted = Color(0xFFA0A69A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Background) {
                    var page by rememberSaveable { mutableStateOf("Home") }
                    var profileName by rememberSaveable {
                        mutableStateOf(getSharedPreferences("mylauncher", MODE_PRIVATE).getString("profile_name", "Player") ?: "Player")
                    }
                    if (page == "Home") {
                        LauncherHome(
                            onStart = {
                                Toast.makeText(
                                    this,
                                    "Game launching is not connected yet.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            profileName = profileName,
                            onOpen = { selected -> page = selected }
                        )
                    } else {
                        LauncherPage(
                            title = page,
                            profileName = profileName,
                            onProfileNameChange = { updated ->
                                profileName = updated
                                getSharedPreferences("mylauncher", MODE_PRIVATE).edit().putString("profile_name", updated).apply()
                            },
                            onBack = { page = "Home" }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LauncherHome(
    onStart: () -> Unit,
    profileName: String,
    onOpen: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("MYLAUNCHER", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Your world, your way.", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier.size(46.dp).background(Panel, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Gamepad, contentDescription = "MyLauncher", tint = Accent)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Panel)
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text("JAVA EDITION • MOBILE", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(12.dp))
                Text("Ready for\na new adventure?", color = Color.White, fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                Text("A mobile-first home for your Minecraft Java profiles.", color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background)
                ) {
                    Icon(Icons.Default.Gamepad, contentDescription = null)
                    Text("  START GAME", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text("Prototype only · Game runtime not connected", color = Muted, fontSize = 11.sp)
            }
        }

        Text("QUICK ACCESS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            QuickTile("Versions", "Manage builds", Icons.Default.Terminal, Modifier.weight(1f)) { onOpen("Versions") }
            QuickTile("Features", "Add tools", Icons.Default.Extension, Modifier.weight(1f)) { onOpen("Features") }
            QuickTile("Settings", "Tune launcher", Icons.Default.Settings, Modifier.weight(1f)) { onOpen("Settings") }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Panel)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier.size(44.dp).background(Color(0xFF303B26), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Gamepad, contentDescription = null, tint = Accent)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(profileName.ifBlank { "Player" }, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Offline profile · Stored on this device", color = Muted, fontSize = 12.sp)
                }
                Text("ACTIVE", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("FEATURE STORE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("COMING SOON", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            FeatureRow("Social Hub", "Friends and community links", "Optional")
            FeatureRow("Theme Customizer", "Personalize your launcher", "Optional")
        }

        Text("MyLauncher 0.1.0 · Built for mobile", color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp))
    }
}

@Composable
private fun LauncherPage(
    title: String,
    profileName: String,
    onProfileNameChange: (String) -> Unit,
    onBack: () -> Unit
) {
    var versions by remember { mutableStateOf<List<MinecraftVersion>>(emptyList()) }
    var versionsLoading by remember { mutableStateOf(false) }
    var versionsError by remember { mutableStateOf<String?>(null) }
    var refreshToken by remember { mutableStateOf(0) }

    LaunchedEffect(title, refreshToken) {
        if (title == "Versions" && versions.isEmpty() && !versionsLoading) {
            versionsLoading = true
            versionsError = null
            try {
                versions = MinecraftVersionRepository.fetchVersions()
            } catch (error: Exception) {
                versionsError = error.message ?: "Could not load Minecraft versions."
            } finally {
                versionsLoading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Background).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Accent)
                Text("  BACK", color = Color.White)
            }
            Text(title.uppercase(), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }

        when (title) {
            "Versions" -> {
                Text("Game versions", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Live version metadata from the official Mojang version manifest. Downloads and installation are not enabled yet.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
                if (versionsLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = Accent, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text("Loading official version list…", color = Muted, fontSize = 13.sp)
                    }
                }
                versionsError?.let { message ->
                    InfoCard("Could not load versions", "Check your connection", message)
                    OutlinedButton(onClick = { versionsError = null; versions = emptyList(); refreshToken += 1 }) {
                        Text("TRY AGAIN", color = Color.White)
                    }
                }
                if (versions.isNotEmpty()) {
                    val latestRelease = versions.firstOrNull { it.type == "release" }
                    val latestSnapshot = versions.firstOrNull { it.type == "snapshot" }
                    latestRelease?.let { InfoCard("Latest release", it.id, "Official release · Selectable metadata only; not installed.") }
                    latestSnapshot?.let { InfoCard("Latest snapshot", it.id, "Snapshot build · Selectable metadata only; not installed.") }
                    Text("RECENT VERSIONS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    versions.take(12).forEach { version ->
                        InfoCard(version.id, version.type.uppercase(), "Released: " + version.releaseTime.take(10))
                    }
                }
            }
            "Features" -> {
                Text("Feature store", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Optional add-ons will be installed only after you choose them. This prototype does not download or execute add-ons.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
                InfoCard("Social Hub", "Coming soon", "Community links and social shortcuts.")
                InfoCard("Theme Customizer", "Coming soon", "Change accent colors and launcher appearance.")
                InfoCard("Server Status", "Coming soon", "Optional server status cards.")
            }
            else -> {
                Text("Launcher settings", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Set the local player name saved on this device. This does not sign you into Microsoft or authenticate online play.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
                var draftName by rememberSaveable(profileName) { mutableStateOf(profileName) }
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it.take(16) },
                    label = { Text("Player name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                Text("Use 1–16 letters, numbers, or underscores for a simple offline name.", color = Muted, fontSize = 12.sp)
                Button(
                    onClick = {
                        val cleanName = draftName.trim()
                        if (cleanName.matches(Regex("[A-Za-z0-9_]{1,16}"))) {
                            onProfileNameChange(cleanName)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background)
                ) {
                    Text("SAVE PROFILE", fontWeight = FontWeight.Bold)
                }
                InfoCard("Account", "Offline profile", "Microsoft authentication is not configured yet.")
                InfoCard("Runtime", "Not configured", "A compatible Java runtime and game launch pipeline are still required.")
                InfoCard("Safety", "Downloads disabled", "The app does not download game files or run third-party code yet.")
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, status: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(status, color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(description, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun QuickTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(21.dp))
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun FeatureRow(title: String, description: String, status: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(38.dp).background(Color(0xFF303B26), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Extension, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(description, color = Muted, fontSize = 11.sp)
            }
            Text(status, color = Muted, fontSize = 10.sp)
        }
    }
}
