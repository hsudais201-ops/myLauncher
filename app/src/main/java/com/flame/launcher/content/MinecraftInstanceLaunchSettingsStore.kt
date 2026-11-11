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
    val maximumMemoryMb: Int = 2048,
    val width: Int = 1280,
    val height: Int = 720,
    val fullscreen: Boolean = false,
    val jvmArguments: String = "",
    val gameArguments: String = ""
) {
    fun validationError(): String? {
        if (minimumMemoryMb !in MIN_MEMORY_MB..MAX_MEMORY_MB ||
            maximumMemoryMb !in MIN_MEMORY_MB..MAX_MEMORY_MB ||
            maximumMemoryMb < minimumMemoryMb
        ) return "Memory must be between 512 and 8192 MB, with maximum at least minimum."
        if (width !in MIN_WIDTH..MAX_WIDTH || height !in MIN_HEIGHT..MAX_HEIGHT) {
            return "Resolution must be between 640×480 and 7680×4320."
        }
        if (!jvmArguments.isSafeArgumentText() || !gameArguments.isSafeArgumentText()) {
            return "Arguments are too long or contain unsupported control characters."
        }
        return null
    }

    private fun String.isSafeArgumentText(): Boolean =
        length <= MAX_ARGUMENT_LENGTH && none { it.isISOControl() }

    companion object {
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
    fun load(profile: MinecraftInstanceProfile): MinecraftInstanceLaunchSettingsResult {
        val directory = validatedProfileDirectory(profile)
            ?: return MinecraftInstanceLaunchSettingsResult.Failed("Profile directory is invalid.")
        val file = File(directory, SETTINGS_FILE)
        if (!file.exists()) return MinecraftInstanceLaunchSettingsResult.Loaded(MinecraftInstanceLaunchSettings())
        return try {
            val properties = Properties().apply { file.inputStream().use { load(it) } }
            val settings = MinecraftInstanceLaunchSettings(
                minimumMemoryMb = properties.getProperty("minimumMemoryMb")?.toInt() ?: 512,
                maximumMemoryMb = properties.getProperty("maximumMemoryMb")?.toInt() ?: 2048,
                width = properties.getProperty("width")?.toInt() ?: 1280,
                height = properties.getProperty("height")?.toInt() ?: 720,
                fullscreen = properties.getProperty("fullscreen")?.toBooleanStrict() ?: false,
                jvmArguments = properties.getProperty("jvmArguments") ?: "",
                gameArguments = properties.getProperty("gameArguments") ?: ""
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
        private val PROFILE_ID_PATTERN = Regex("profile-[A-Za-z0-9-]{36}")
    }
}
