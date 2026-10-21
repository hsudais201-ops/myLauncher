package com.voidcraft.launcher

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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.launch

private val VoidBackground = Color(0xFF0A0C12)
private val VoidSurface = Color(0xFF141824)
private val VoidAccent = Color(0xFF9B8CFF)
private val VoidMint = Color(0xFF70E1C1)
private val VoidMuted = Color(0xFF9AA3B8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = VoidAccent,
                    secondary = VoidMint,
                    background = VoidBackground,
                    surface = VoidSurface,
                    onPrimary = Color(0xFF171326),
                    onBackground = Color(0xFFF4F5FA),
                    onSurface = Color(0xFFF4F5FA)
                )
            ) {
                VoidcraftApp()
            }
        }
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
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = VoidBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = VoidSurface, tonalElevation = 0.dp) {
                LauncherTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
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
            Header()
            Spacer(Modifier.height(24.dp))
            when (LauncherTab.entries[selectedTab]) {
                LauncherTab.Home -> HomeScreen(
                    onPlayClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Game engine integration is next. Nothing has launched yet."
                            )
                        }
                    },
                    onVersionsClick = { selectedTab = 1 },
                    onSettingsClick = { selectedTab = 3 }
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
                LauncherTab.Settings -> PlaceholderScreen(
                    icon = Icons.Default.Tune,
                    title = "Launcher settings",
                    description = "Account sign-in, memory allocation, Java runtime and controls will be configured here."
                )
            }
            Spacer(Modifier.height(24.dp))
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
                    .background(
                        Brush.linearGradient(listOf(VoidAccent, Color(0xFF5A4EC4))),
                        RoundedCornerShape(15.dp)
                    )
                    .padding(11.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Build, contentDescription = null, tint = Color.White)
            }
            Column {
                Text("VOIDCRAFT", fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text("JAVA EDITION LAUNCHER", fontSize = 10.sp, color = VoidMuted, letterSpacing = 1.2.sp)
            }
        }
        Surface(shape = CircleShape, color = Color(0xFF20283A)) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = "Offline mode status",
                tint = VoidMint,
                modifier = Modifier.padding(11.dp)
            )
        }
    }
}

@Composable
private fun HomeScreen(
    onPlayClick: () -> Unit,
    onVersionsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("YOUR NEXT WORLD
AWAITS.", fontSize = 31.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "A fresh home for Minecraft Java on Android.",
            color = VoidMuted,
            fontSize = 14.sp
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = VoidSurface)
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF252A45), VoidSurface, VoidSurface)
                        )
                    )
                    .padding(22.dp)
            ) {
                Surface(color = Color(0xFF33304F), shape = RoundedCornerShape(50)) {
                    Text(
                        "  EARLY DEVELOPMENT  ",
                        color = VoidAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp)
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text("Ready when you are.", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "The interface is taking shape. The Minecraft runtime and sign-in flow are not connected yet.",
                    color = VoidMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = onPlayClick,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VoidAccent)
                ) {
                    Icon(Icons.Default.SportsEsports, contentDescription = null)
                    Text("  PLAY", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                }
            }
        }

        Text("QUICK ACCESS", fontSize = 12.sp, color = VoidMuted, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.CheckCircle,
                title = "Versions",
                subtitle = "Game profiles",
                onClick = onVersionsClick
            )
            QuickCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Tune,
                title = "Settings",
                subtitle = "Tune your setup",
                onClick = onSettingsClick
            )
        }
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101E1D))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = VoidMint)
                Column {
                    Text("Built for Android", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Designed for touch, with performance and controls planned next.",
                        color = VoidMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = VoidSurface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = VoidAccent)
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, color = VoidMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PlaceholderScreen(icon: ImageVector, title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Surface(color = VoidSurface, shape = RoundedCornerShape(22.dp)) {
            Icon(icon, contentDescription = null, tint = VoidAccent, modifier = Modifier.padding(22.dp))
        }
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text(description, color = VoidMuted, fontSize = 14.sp, lineHeight = 21.sp)
        OutlinedButton(
            onClick = {},
            enabled = false,
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("COMING IN A FUTURE BUILD")
        }
    }
}
