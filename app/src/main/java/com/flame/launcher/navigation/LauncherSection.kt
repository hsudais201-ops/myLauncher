package com.flame.launcher.navigation

/**
 * Main destinations in Flame Launcher's navigation drawer.
 * Minecraft content providers are deliberately separate from the Feature Store.
 */
enum class LauncherSection(
    val route: String,
    val title: String,
    val description: String
) {
    HOME("home", "Home", "Your Flame Launcher dashboard."),
    PLAY("play", "Play Minecraft", "Manage game profiles and launch Minecraft Java Edition."),
    MINECRAFT_CONTENT("minecraft-content", "Minecraft Content", "Browse Minecraft content from Modrinth and CurseForge."),
    FEATURE_STORE("feature-store", "Feature Store", "Install independent official and community launcher features."),
    SOCIAL_HUB("social-hub", "Social Hub", "Friends, chat, profiles, and community features."),
    SERVER_STATE("server-state", "ServerState", "Check Minecraft server status and player counts."),
    FLAME_AI("flame-ai", "Flame AI", "Analyze crashes and guide safe, reversible repairs."),
    SETTINGS("settings", "Settings", "Configure accounts, controls, graphics, and performance.")
}

object FlameNavigation {
    val mainSections: List<LauncherSection> = LauncherSection.entries.toList()

    fun find(route: String): LauncherSection? =
        mainSections.firstOrNull { it.route == route }
}
