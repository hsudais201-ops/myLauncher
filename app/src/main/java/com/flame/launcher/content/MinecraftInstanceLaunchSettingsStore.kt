package com.flame.launcher.content

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID

/**
 * Per-instance launch preferences. These settings are persisted only; they do
 * not imply that a compatible JVM or Minecraft client is installed.
 */
data class MinecraftInstanceLaunchSettings(
    val minimumMemoryMb: Int = 512,
    val maximumMemoryMb: Int = 1536,
    val width: Int = 1280,
    val height: Int = 720,
    val fullscreen: Boolean = false,
    val jvmArguments: String = "",
    val gameArguments: String = "",
    val javaRuntimeId: String? = null
) {
    fun validationError(): String? {
        if (minimumMemoryMb !in MIN_MEMORY_MB..MAX_MEMORY_MB ||
            maximumMemoryMb !in MIN_MEMORY_MB..MAX_MEMORY_MB ||
            maximumMemoryMb < minimumMemoryMb
        ) return "Memory must be between 512 and 8192 MB, with maximum at least minimum."
        if (width !in MIN_WIDTH..MAX_WIDTH || height !in MIN_HEIGHT..MAX_HEIGHT) {
            return "Resolution must be between 640×480 and 7680×4320."
        }
        if (javaRuntimeId != null && !RUNTIME_ID_PATTERN.matches(javaRuntimeId)) {
            return "Preferred Java runtime ID is invalid."
        }
        if (!jvmArguments.isSafeArgumentText() || !gameArguments.isSafeArgumentText()) {
            return "Arguments are too long or contain unsupported control characters."
        }
        return null
    }

    private fun String.isSafeArgumentText(): Boolean =
        length <= MAX_ARGUMENT_LENGTH && none { it.isISOControl() }

    companion object {
        fun recommendedMaximumMemoryMb(totalMemoryMb: Int?): Int {
            if (totalMemoryMb == null || totalMemoryMb <= 0) return 1536
            val proportionalBudget = (totalMemoryMb.toLong() * 3L / 8L)
                .coerceAtMost(2048L)
                .toInt()
            return ((proportionalBudget / 256) * 256).coerceIn(512, 2048)
        }
        const val MIN_MEMORY_MB = 512
        const val MAX_MEMORY_MB = 8192
        const val MIN_WIDTH = 640
        const val MAX_WIDTH = 7680
        const val MIN_HEIGHT = 480
        const val MAX_HEIGHT = 4320
        const val MAX_ARGUMENT_LENGTH = 2048
    }
}

sealed interface MinecraftInstanceLaunchSettingsResult {
    data class Loaded(val settings: MinecraftInstanceLaunchSettings) : MinecraftInstanceLaunchSettingsResult
    data class Saved(val settings: MinecraftInstanceLaunchSettings) : MinecraftInstanceLaunchSettingsResult
    data class Failed(val reason: String) : MinecraftInstanceLaunchSettingsResult
}

/** Stores settings beside a validated profile, never in a caller-selected path. */
class MinecraftInstanceLaunchSettingsStore(private val profilesRoot: File) {
    @Synchronized
    fun load(
        profile: MinecraftInstanceProfile,
        defaults: MinecraftInstanceLaunchSettings = MinecraftInstanceLaunchSettings()
    ): MinecraftInstanceLaunchSettingsResult {
        val directory = validatedProfileDirectory(profile)
            ?: return MinecraftInstanceLaunchSettingsResult.Failed("Profile directory is invalid.")
        val file = File(directory, SETTINGS_FILE)
        if (!file.exists()) return MinecraftInstanceLaunchSettingsResult.Loaded(defaults)
        return try {
            if (!file.isFile || file.canonicalFile.parentFile != directory ||
                file.length() !in 1..MAX_SETTINGS_FILE_BYTES.toLong()
            ) {
                return MinecraftInstanceLaunchSettingsResult.Failed("Stored launch settings file is invalid or too large.")
            }
            val properties = Properties().apply { file.inputStream().use { load(it) } }
            val schema = properties.getProperty("schema")
                ?: return MinecraftInstanceLaunchSettingsResult.Failed("Stored launch settings have no schema version.")
            if (schema != SETTINGS_SCHEMA_VERSION) {
                return MinecraftInstanceLaunchSettingsResult.Failed("Stored launch settings use an unsupported schema version.")
            }
            val settings = MinecraftInstanceLaunchSettings(
                minimumMemoryMb = properties.getProperty("minimumMemoryMb")?.toInt() ?: defaults.minimumMemoryMb,
                maximumMemoryMb = properties.getProperty("maximumMemoryMb")?.toInt() ?: defaults.maximumMemoryMb,
                width = properties.getProperty("width")?.toInt() ?: defaults.width,
                height = properties.getProperty("height")?.toInt() ?: defaults.height,
                fullscreen = properties.getProperty("fullscreen")?.toBooleanStrict() ?: defaults.fullscreen,
                jvmArguments = properties.getProperty("jvmArguments") ?: defaults.jvmArguments,
                gameArguments = properties.getProperty("gameArguments") ?: defaults.gameArguments,
                javaRuntimeId = properties.getProperty("javaRuntimeId")?.takeIf(String::isNotBlank)
                    ?: defaults.javaRuntimeId
            )
            settings.validationError()?.let {
                return MinecraftInstanceLaunchSettingsResult.Failed("Stored launch settings are invalid: $it")
            }
            MinecraftInstanceLaunchSettingsResult.Loaded(settings)
        } catch (error: Exception) {
            MinecraftInstanceLaunchSettingsResult.Failed(error.message ?: "Could not read launch settings.")
        }
    }

    @Synchronized
    fun save(
        profile: MinecraftInstanceProfile,
        settings: MinecraftInstanceLaunchSettings
    ): MinecraftInstanceLaunchSettingsResult {
        settings.validationError()?.let { return MinecraftInstanceLaunchSettingsResult.Failed(it) }
        val directory = validatedProfileDirectory(profile)
            ?: return MinecraftInstanceLaunchSettingsResult.Failed("Profile directory is invalid.")
        return try {
            val properties = Properties().apply {
                setProperty("schema", "1")
                setProperty("minimumMemoryMb", settings.minimumMemoryMb.toString())
                setProperty("maximumMemoryMb", settings.maximumMemoryMb.toString())
                setProperty("width", settings.width.toString())
                setProperty("height", settings.height.toString())
                setProperty("fullscreen", settings.fullscreen.toString())
                setProperty("jvmArguments", settings.jvmArguments)
                setProperty("gameArguments", settings.gameArguments)
                settings.javaRuntimeId?.let { setProperty("javaRuntimeId", it) }
            }
            val destination = File(directory, SETTINGS_FILE)
            val temporary = File(directory, "$SETTINGS_FILE.${UUID.randomUUID()}.tmp")
            try {
                temporary.outputStream().use { properties.store(it, "Flame Launcher instance launch settings") }
                try {
                    Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: Exception) {
                    Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                temporary.delete()
            }
            MinecraftInstanceLaunchSettingsResult.Saved(settings)
        } catch (error: Exception) {
            MinecraftInstanceLaunchSettingsResult.Failed(error.message ?: "Could not save launch settings.")
        }
    }

    private fun validatedProfileDirectory(profile: MinecraftInstanceProfile): File? = runCatching {
        if (!profile.id.matches(PROFILE_ID_PATTERN)) return null
        val root = profilesRoot.canonicalFile
        val directory = profile.instanceDirectory.canonicalFile
        if (directory != File(root, profile.id).canonicalFile) return null
        if (!directory.toPath().startsWith(root.toPath())) return null
        if (!File(directory, "profile.properties").isFile) return null
        val content = profile.contentDirectory.canonicalFile
        if (content != File(directory, ".minecraft").canonicalFile || !content.isDirectory) return null
        directory
    }.getOrNull()

    companion object {
        private const val SETTINGS_FILE = "launch-settings.properties"
        private const val SETTINGS_SCHEMA_VERSION = "1"
        private val RUNTIME_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
        private const val MAX_SETTINGS_FILE_BYTES = 16 * 1024
        private val PROFILE_ID_PATTERN = Regex("profile-[A-Za-z0-9-]{36}")
    }
}
