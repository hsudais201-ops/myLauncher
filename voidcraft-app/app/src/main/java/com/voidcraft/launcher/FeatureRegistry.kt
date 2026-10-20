package com.voidcraft.launcher

import android.content.Context

/**
 * Local registry for first-party VoidCraft add-ons.
 *
 * A catalog entry is not executable code. Only IDs in the explicit built-in
 * allowlist can be activated; downloaded/community code is never loaded by
 * this registry. Remote signatures, package delivery and permission grants
 * must be implemented before third-party installation is enabled.
 */
object FeatureRegistry {
    const val PREFS_NAME = "voidcraft_installed_features"
    private const val KEY_INSTALLED_IDS = "installed_feature_ids"

    data class Definition(
        val id: String,
        val name: String,
        val description: String,
        val status: Status,
        val permissions: List<String> = emptyList()
    )

    enum class Status { AVAILABLE, PLANNED, REVIEW_REQUIRED }

    private val definitions = listOf(
        Definition(
            "theme-studio",
            "Theme Studio",
            "Customize the launcher appearance using built-in, reversible preferences.",
            Status.AVAILABLE
        ),
        Definition(
            "smart-performance-advisor",
            "Smart Performance Advisor",
            "Review device information and suggest reversible settings; never impose an FPS cap.",
            Status.PLANNED,
            listOf("device_info")
        ),
        Definition(
            "server-status",
            "Minecraft Server Status",
            "Track saved servers, MOTD, online state, player counts and server icons.",
            Status.PLANNED,
            listOf("network")
        ),
        Definition(
            "friends-chat",
            "Friends & Chat",
            "Friend requests and private messaging require a reviewed backend.",
            Status.PLANNED,
            listOf("network", "account")
        ),
        Definition(
            "crash-report-center",
            "Crash Report Center",
            "Inspect local logs and prepare user-reviewed diagnostic reports.",
            Status.PLANNED,
            listOf("game_logs")
        ),
        Definition(
            "screenshot-gallery",
            "Screenshot Gallery",
            "Browse screenshots chosen from VoidCraft's game files.",
            Status.PLANNED,
            listOf("screenshots")
        ),
        Definition(
            "world-backup-scheduler",
            "World Backup Scheduler",
            "Create and verify backups with a restore preview before any replacement.",
            Status.PLANNED,
            listOf("world_files")
        ),
        Definition(
            "mod-compatibility-checker",
            "Mod Compatibility Checker",
            "Compare mod metadata with the selected Minecraft version and loader.",
            Status.PLANNED,
            listOf("mod_metadata")
        ),
        Definition(
            "shader-preset-manager",
            "Shader Preset Manager",
            "Manage renderer-aware presets when renderer integration is available.",
            Status.PLANNED,
            listOf("graphics_settings")
        ),
        Definition(
            "startup-diagnostics",
            "Startup Diagnostics",
            "Check runtime, storage, memory and native-library readiness.",
            Status.PLANNED,
            listOf("device_info", "game_logs")
        ),
        Definition(
            "community-feature-upload",
            "Community Feature Upload",
            "Submissions require authenticated review, signature validation and compatibility checks.",
            Status.REVIEW_REQUIRED,
            listOf("network")
        )
    )

    /** Return stable first-party catalog entries; callers may filter by text. */
    fun catalog(): List<Definition> = definitions.toList()

    fun find(id: String): Definition? = definitions.firstOrNull { it.id == id }

    /**
     * Only explicitly available built-in features can be activated.
     * Unknown IDs, planned items and community submissions fail closed.
     */
    fun canInstall(id: String): Boolean =
        find(id)?.status == Status.AVAILABLE && id == "theme-studio"

    fun install(context: Context, id: String): Result<Unit> {
        if (!canInstall(id)) {
            return Result.failure(IllegalStateException(
                "Feature is not installable in this build. No code was executed."
            ))
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = prefs.getStringSet(KEY_INSTALLED_IDS, emptySet())
            .orEmpty().toMutableSet()
        updated.add(id)
        val saved = prefs.edit().putStringSet(KEY_INSTALLED_IDS, updated).commit()
        return if (saved) Result.success(Unit) else Result.failure(
            IllegalStateException("Could not persist installed-feature state.")
        )
    }

    fun uninstall(context: Context, id: String): Result<Unit> {
        if (find(id) == null) {
            return Result.failure(IllegalArgumentException("Unknown feature ID."))
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = prefs.getStringSet(KEY_INSTALLED_IDS, emptySet())
            .orEmpty().toMutableSet()
        updated.remove(id)
        val saved = prefs.edit().putStringSet(KEY_INSTALLED_IDS, updated).commit()
        return if (saved) Result.success(Unit) else Result.failure(
            IllegalStateException("Could not persist installed-feature state.")
        )
    }

    fun isInstalled(context: Context, id: String): Boolean =
        id in context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_INSTALLED_IDS, emptySet()).orEmpty()

    fun installedIds(context: Context): Set<String> =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_INSTALLED_IDS, emptySet()).orEmpty().toSet()
}
