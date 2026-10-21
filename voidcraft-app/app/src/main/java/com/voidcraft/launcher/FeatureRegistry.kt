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
    // Explicit allowlist: only built-in features with implemented, tested pages are enabled.
    private val installableBuiltIns = setOf("startup-diagnostics", "instance-notes")

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
            Status.PLANNED
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
            "Read device, memory, storage and app build information before launch.",
            Status.AVAILABLE,
            listOf("device_info", "memory_info", "storage_stats")
        ),
        Definition(
            "resource-pack-manager",
            "Resource Pack Manager",
            "Browse, enable, disable, and inspect resource packs for a selected instance.",
            Status.PLANNED,
            listOf("resource_pack_files")
        ),
        Definition(
            "resource-pack-compatibility",
            "Resource Pack Compatibility Checker",
            "Check pack metadata and declared format against the selected Minecraft version.",
            Status.PLANNED,
            listOf("resource_pack_metadata")
        ),
        Definition(
            "world-details-viewer",
            "World Details Viewer",
            "Inspect local world metadata such as game version, last-played date, and game mode.",
            Status.PLANNED,
            listOf("world_metadata")
        ),
        Definition(
            "local-playtime-tracker",
            "Playtime Tracker",
            "Track launcher-observed play sessions locally with a clear reset option.",
            Status.PLANNED,
            listOf("session_metadata")
        ),
        Definition(
            "accessibility-presets",
            "Accessibility Presets",
            "Offer launcher-level text size, contrast, and reduced-animation presets.",
            Status.PLANNED,
            listOf("launcher_preferences")
        ),
        Definition(
            "profile-icon-studio",
            "Profile Icon Studio",
            "Create and assign original local icons for launcher instances and profiles.",
            Status.PLANNED,
            listOf("profile_metadata")
        ),
        Definition(
            "configuration-backup",
            "Configuration Backup",
            "Back up selected launcher and per-instance configuration files with restore preview.",
            Status.PLANNED,
            listOf("config_files")
        ),
        Definition(
            "network-diagnostics",
            "Network Diagnostics",
            "Check connectivity and latency to a user-selected server without changing network settings.",
            Status.PLANNED,
            listOf("network")
        ),
        Definition(
            "release-watch",
            "Minecraft Release Watch",
            "Show new official Minecraft version metadata and let users choose what to install.",
            Status.PLANNED,
            listOf("network")
        ),
        Definition(
            "controller-presets",
            "Controller Presets",
            "Save and switch controller mapping presets when controller integration is available.",
            Status.PLANNED,
            listOf("controller_settings")
        ),
        Definition(
            "resource-usage-history",
            "Resource Usage History",
            "Record opted-in, coarse launcher-observed memory and session timing data locally.",
            Status.PLANNED,
            listOf("device_info", "memory_info", "session_metadata")
        ),
        Definition(
            "instance-notes",
            "Instance Notes",
            "Keep private notes and troubleshooting reminders attached to an instance.",
            Status.AVAILABLE,
            listOf("profile_metadata")
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
        find(id)?.status == Status.AVAILABLE && id in installableBuiltIns

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
