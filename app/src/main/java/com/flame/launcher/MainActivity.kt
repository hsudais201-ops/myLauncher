package com.flame.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flame.launcher.content.MinecraftInstanceProfilesPanel
import com.flame.launcher.navigation.FlameNavigation
import com.flame.launcher.navigation.LauncherSection
import com.flame.launcher.render.FlameRenderSurface
import kotlinx.coroutines.launch

private val FlameBackground = Color(0xFF0B0D12)
private val FlamePanel = Color(0xFF151A23)
private val FlamePanelRaised = Color(0xFF1D2430)
private val FlameAccent = Color(0xFFFF7A1A)
private val FlameAccentDark = Color(0xFF3B1B08)
private val FlameText = Color(0xFFF4F5F7)
private val FlameMuted = Color(0xFF9AA4B2)
private val FlameBorder = Color(0xFF2A3341)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlameTheme {
                FlameLauncherApp()
            }
        }
    }
}

@Composable
private fun FlameTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = FlameAccent,
            onPrimary = FlameAccentDark,
            secondary = Color(0xFFFFB36E),
            background = FlameBackground,
            onBackground = FlameText,
            surface = FlamePanel,
            onSurface = FlameText,
            surfaceVariant = FlamePanelRaised,
            onSurfaceVariant = FlameMuted,
            outline = FlameBorder
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlameLauncherApp() {
    var selectedSection by rememberSaveable { mutableStateOf(LauncherSection.HOME) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = FlamePanel,
                drawerContentColor = FlameText
            ) {
                DrawerBrand()
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = FlameBorder
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FlameNavigation.mainSections.forEach { section ->
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    section.title,
                                    fontWeight = if (selectedSection == section) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    }
                                )
                            },
                            selected = selectedSection == section,
                            onClick = {
                                selectedSection = section
                                coroutineScope.launch { drawerState.close() }
                            },
                            icon = {
                                Icon(
                                    imageVector = sectionIcon(section),
                                    contentDescription = null
                                )
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = FlameAccent.copy(alpha = 0.16f),
                                selectedIconColor = FlameAccent,
                                selectedTextColor = FlameText,
                                unselectedIconColor = FlameMuted,
                                unselectedTextColor = FlameMuted
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "FOUNDATION BUILD  •  v0.1.0",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    color = FlameMuted,
                    fontSize = 10.sp,
                    letterSpacing = 1.1.sp
                )
            }
        }
    ) {
        Scaffold(
            containerColor = FlameBackground,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "FLAME LAUNCHER",
                                color = FlameText,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = selectedSection.title,
                                color = FlameMuted,
                                fontSize = 11.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { coroutineScope.launch { drawerState.open() } }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Menu,
                                contentDescription = "Open navigation",
                                tint = FlameText
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = FlameBackground,
                        titleContentColor = FlameText
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedSection) {
                    LauncherSection.HOME -> HomePage(
                        onNavigate = { selectedSection = it }
                    )
                    LauncherSection.PLAY -> PlayPage()
                    LauncherSection.MINECRAFT_CONTENT -> MinecraftContentPage()
                    LauncherSection.FEATURE_STORE -> FeatureStorePage()
                    LauncherSection.SOCIAL_HUB -> PlannedPage(
                        title = "Social Hub",
                        subtitle = "A separate community space for your Minecraft life.",
                        body = "Friends, chat, player profiles, and community connections will be added after the account and service foundation is ready.",
                        icon = Icons.Outlined.Groups
                    )
                    LauncherSection.SERVER_STATE -> PlannedPage(
                        title = "ServerState",
                        subtitle = "Know when your favourite servers are online.",
                        body = "Live server status, player counts, latency checks, and saved server lists will be connected to real status services in a later milestone.",
                        icon = Icons.Outlined.Dns
                    )
                    LauncherSection.FLAME_AI -> FlameAiPage()
                    LauncherSection.SETTINGS -> SettingsPage()
                }
                Text(
                    text = "Flame Launcher is under active development. Unavailable systems are labelled clearly.",
                    color = FlameMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun DrawerBrand() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Whatshot,
                contentDescription = null,
                tint = FlameAccent,
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "FLAME",
                color = FlameText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }
        Text(
            text = "Your Minecraft home base",
            color = FlameMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun HomePage(onNavigate: (LauncherSection) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF52230F), Color(0xFF25150F), Color(0xFF171B25))
                    )
                )
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = Color.White.copy(alpha = 0.10f),
                    shape = CircleShape
                ) {
                    Text(
                        text = "ANDROID JAVA EDITION LAUNCHER",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        color = Color(0xFFFFC08F),
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Your Minecraft.\nYour way.",
                    color = Color.White,
                    fontSize = 31.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "A mobile home for the real Minecraft Java Edition experience.",
                    color = Color(0xFFE2D4CC),
                    fontSize = 14.sp
                )
                Button(
                    onClick = { onNavigate(LauncherSection.PLAY) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFE0C6),
                        contentColor = Color(0xFF3A1A08)
                    )
                ) {
                    Text("Open Play")
                }
            }
        }

        SectionTitle(
            "GET STARTED",
            "The core launcher systems are being built in verified stages."
        )
        StatusCard(
            title = "Minecraft runtime",
            status = "Next milestone",
            body = "Verified Mojang version metadata, a Java runtime discovery/selection manager, and an OpenGL ES render host are connected. Runtime packaging, game files, and the Android LWJGL bridge remain in progress.",
            icon = Icons.Outlined.SportsEsports
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickLink(
                modifier = Modifier.weight(1f),
                title = "Minecraft Content",
                detail = "Modrinth + CurseForge",
                icon = Icons.Outlined.Extension,
                onClick = { onNavigate(LauncherSection.MINECRAFT_CONTENT) }
            )
            QuickLink(
                modifier = Modifier.weight(1f),
                title = "Feature Store",
                detail = "Official + community",
                icon = Icons.Outlined.Storefront,
                onClick = { onNavigate(LauncherSection.FEATURE_STORE) }
            )
        }
        StatusCard(
            title = "Accounts",
            status = "Planned",
            body = "Offline local profiles and Microsoft sign-in will be implemented with the real game authentication flow.",
            icon = Icons.Outlined.Groups
        )
    }
}

@Composable
private fun PlayPage() {
    SectionTitle(
        title = "Play Minecraft",
        subtitle = "Choose a Mojang release, inspect its verified launch metadata, and prepare a compatible Java runtime."
    )
    MinecraftInstanceProfilesPanel()
    JavaRuntimeStatusPanel()
    MinecraftVersionCatalogPanel()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FlamePanel)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "OPENGL ES RENDER HOST",
                color = FlameAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp
            )
            Text(
                text = "Main rendering surface",
                color = FlameText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp
            )
            FlameRenderSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Text(
                text = "On-demand OpenGL ES frames are active in this preview. Rendering Minecraft itself still requires a compatible LWJGL-to-Android native bridge and game launch pipeline.",
                color = FlameMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
    StatusCard(
        title = "Game profiles",
        status = "Content profiles available • launch profiles pending",
        body = "Content profiles already isolate installed mods and packs. Full game instances will add verified libraries, assets, client JARs, JVM arguments, and per-profile settings.",
        icon = Icons.Outlined.Home
    )
    StatusCard(
        title = "Offline and Microsoft accounts",
        status = "Authentication pending",
        body = "Offline local profiles and official Microsoft authentication will be separate supported account types. Online sessions will use the official account flow.",
        icon = Icons.Outlined.Groups
    )
}

@Composable
private fun MinecraftContentPage() {
    MinecraftContentBrowser()
}

@Composable
private fun FeatureStorePage() {
    SectionTitle(
        title = "Feature Store",
        subtitle = "Launcher add-ons only. Minecraft mods from Modrinth and CurseForge do not appear here."
    )
    StatusCard(
        title = "Official features",
        status = "Cloud catalog pending",
        body = "Flame-published features will be installed one at a time from their own verified cloud packages.",
        icon = Icons.Outlined.Whatshot
    )
    StatusCard(
        title = "Community features",
        status = "Submission system pending",
        body = "Community developers will be able to publish independently installable launcher features after package review and permission checks are ready.",
        icon = Icons.Outlined.Groups
    )
    StatusCard(
        title = "Installed features",
        status = "Empty",
        body = "No features are preinstalled in this foundation build. Installed features will have their own open, update, and uninstall controls.",
        icon = Icons.Outlined.Storefront
    )
}

@Composable
private fun FlameAiPage() {
    SectionTitle(
        title = "Flame AI",
        subtitle = "Diagnostics based on real logs and evidence, not guesses."
    )
    StatusCard(
        title = "Crash diagnostics",
        status = "Engine pending",
        body = "The analyzer will inspect crash reports, logs, mod versions, dependencies, and compatibility before suggesting a cause.",
        icon = Icons.Outlined.Psychology
    )
    StatusCard(
        title = "Safe repair",
        status = "Engine pending",
        body = "Repair actions will request approval, create backups, remain reversible, and be verified by a fresh test before showing success.",
        icon = Icons.Outlined.Extension
    )
}

@Composable
private fun SettingsPage() {
    SectionTitle(
        title = "Settings",
        subtitle = "Device-aware controls will be exposed only when the underlying engine supports them."
    )
    StatusCard(
        title = "Performance",
        status = "Planned",
        body = "RAM recommendations, resolution, rendering compatibility, Java runtime selection, and graphics presets will respect the device's real limits.",
        icon = Icons.Outlined.SportsEsports
    )
    // Show the real device ABI and managed runtime inventory without implying
    // that downloading or launching a JVM is available yet.
    JavaRuntimeStatusPanel()
    StatusCard(
        title = "Controls",
        status = "Planned",
        body = "Touch layouts, keyboard and mouse support, and per-profile control presets will be added with the game runtime.",
        icon = Icons.Outlined.Settings
    )
    StatusCard(
        title = "Application",
        status = "0.1.0 foundation",
        body = "Flame Launcher is an early development build. The launcher UI is not a replacement for the Minecraft game engine.",
        icon = Icons.Outlined.Home
    )
}

@Composable
private fun PlannedPage(
    title: String,
    subtitle: String,
    body: String,
    icon: ImageVector
) {
    SectionTitle(title = title, subtitle = subtitle)
    StatusCard(
        title = title,
        status = "Service not connected",
        body = body,
        icon = icon
    )
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = FlameText,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = FlameMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}

@Composable
private fun StatusCard(
    title: String,
    status: String,
    body: String,
    icon: ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FlamePanel),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = FlameAccent.copy(alpha = 0.12f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = FlameAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(title, color = FlameText, fontWeight = FontWeight.SemiBold)
                    Text(status, color = FlameAccent, fontSize = 11.sp)
                }
            }
            Text(
                text = body,
                color = FlameMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun ProviderCard(name: String, badge: String, detail: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FlamePanel)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                text = badge,
                color = FlameAccent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp
            )
            Text(name, color = FlameText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = FlameMuted, fontSize = 13.sp, lineHeight = 19.sp)
            Text("Provider integration not connected yet", color = FlameMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun QuickLink(
    modifier: Modifier = Modifier,
    title: String,
    detail: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = FlamePanelRaised)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = FlameAccent)
            Text(title, color = FlameText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(detail, color = FlameMuted, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

private fun sectionIcon(section: LauncherSection): ImageVector =
    when (section) {
        LauncherSection.HOME -> Icons.Outlined.Home
        LauncherSection.PLAY -> Icons.Outlined.SportsEsports
        LauncherSection.MINECRAFT_CONTENT -> Icons.Outlined.Extension
        LauncherSection.FEATURE_STORE -> Icons.Outlined.Storefront
        LauncherSection.SOCIAL_HUB -> Icons.Outlined.Groups
        LauncherSection.SERVER_STATE -> Icons.Outlined.Dns
        LauncherSection.FLAME_AI -> Icons.Outlined.Psychology
        LauncherSection.SETTINGS -> Icons.Outlined.Settings
    }
