package com.flame.launcher.content

import java.io.File
import java.util.Properties
import java.util.UUID

data class MinecraftInstanceProfile(
    val id: String,
    val name: String,
    val minecraftVersion: String,
    val loader: MinecraftLoader,
    val instanceDirectory: File,
    val contentDirectory: File
) {
    fun target(selectedWorldDirectory: File? = null): MinecraftInstanceTarget =
        MinecraftInstanceTarget(
            minecraftVersion = minecraftVersion,
            loader = loader,
            contentDirectory = contentDirectory.absolutePath,
            selectedWorldDirectory = selectedWorldDirectory?.absolutePath
        )
}

sealed interface MinecraftInstanceProfileResult {
    data class Created(val profile: MinecraftInstanceProfile) : MinecraftInstanceProfileResult
    data class Selected(val profile: MinecraftInstanceProfile) : MinecraftInstanceProfileResult
    data class Profiles(val profiles: List<MinecraftInstanceProfile>) : MinecraftInstanceProfileResult
    data class Failed(val reason: String) : MinecraftInstanceProfileResult
}

/**
 * Minimal app-owned instance registry. Profiles are isolated under the launcher's
 * private files directory; no shared-storage path is guessed or modified.
 * A profile is a content-install target, not yet a runnable Minecraft installation.
 */
class MinecraftInstanceProfileStore(private val profilesRoot: File) {
    private val selectedFile = File(profilesRoot, "selected-profile")

    @Synchronized
    fun createProfile(
        name: String,
        minecraftVersion: String,
        loader: MinecraftLoader
    ): MinecraftInstanceProfileResult {
        val cleanName = name.trim()
        if (cleanName.isBlank() || cleanName.length > 48 ||
            cleanName.any { it == '/' || it == '\\' || it.isISOControl() }
        ) {
            return MinecraftInstanceProfileResult.Failed("Profile name must be 1–48 safe characters.")
        }
        if (!minecraftVersion.matches(VERSION_PATTERN)) {
            return MinecraftInstanceProfileResult.Failed("Enter a valid Minecraft version, such as 1.20.1.")
        }
        if (loader == MinecraftLoader.UNKNOWN) {
            return MinecraftInstanceProfileResult.Failed("Choose a supported loader.")
        }

        return try {
            if (!profilesRoot.exists() && !profilesRoot.mkdirs()) {
                return MinecraftInstanceProfileResult.Failed("Could not create the private profiles directory.")
            }
            val canonicalRoot = profilesRoot.canonicalFile
            val id = "profile-${UUID.randomUUID()}"
            val directory = File(canonicalRoot, id)
            if (!directory.mkdir()) {
                return MinecraftInstanceProfileResult.Failed("Could not create the profile directory.")
            }
            val gameDirectory = File(directory, ".minecraft")
            if (!gameDirectory.mkdir()) {
                directory.deleteRecursively()
                return MinecraftInstanceProfileResult.Failed("Could not create the profile game directory.")
            }
            val profile = MinecraftInstanceProfile(
                id = id,
                name = cleanName,
                minecraftVersion = minecraftVersion,
                loader = loader,
                instanceDirectory = directory.canonicalFile,
                contentDirectory = gameDirectory.canonicalFile
            )
            val properties = Properties().apply {
                setProperty("schema", "1")
                setProperty("id", profile.id)
                setProperty("name", profile.name)
                setProperty("minecraftVersion", profile.minecraftVersion)
                setProperty("loader", profile.loader.name)
            }
            File(directory, "profile.properties").outputStream().use {
                properties.store(it, "Flame Launcher profile")
            }
            MinecraftInstanceProfileResult.Created(profile)
        } catch (error: Exception) {
            MinecraftInstanceProfileResult.Failed(error.message ?: "Could not create profile.")
        }
    }

    @Synchronized
    fun listProfiles(): MinecraftInstanceProfileResult {
        if (!profilesRoot.exists()) return MinecraftInstanceProfileResult.Profiles(emptyList())
        return try {
            val root = profilesRoot.canonicalFile
            val profiles = profilesRoot.listFiles().orEmpty()
                .filter { it.isDirectory && it.name.startsWith("profile-") }
                .mapNotNull { directory -> readProfile(directory, root) }
                .sortedBy { it.name.lowercase() }
            MinecraftInstanceProfileResult.Profiles(profiles)
        } catch (error: Exception) {
            MinecraftInstanceProfileResult.Failed(error.message ?: "Could not read profiles.")
        }
    }

    @Synchronized
    fun selectProfile(profileId: String): MinecraftInstanceProfileResult {
        val profile = when (val result = listProfiles()) {
            is MinecraftInstanceProfileResult.Profiles -> result.profiles.firstOrNull { it.id == profileId }
            is MinecraftInstanceProfileResult.Failed -> return result
            else -> null
        } ?: return MinecraftInstanceProfileResult.Failed("Profile does not exist.")
        return try {
            if (!profilesRoot.exists() && !profilesRoot.mkdirs()) {
                return MinecraftInstanceProfileResult.Failed("Could not create profiles directory.")
            }
            selectedFile.writeText(profile.id)
            MinecraftInstanceProfileResult.Selected(profile)
        } catch (error: Exception) {
            MinecraftInstanceProfileResult.Failed(error.message ?: "Could not select profile.")
        }
    }

    @Synchronized
    fun selectedProfile(): MinecraftInstanceProfileResult {
        if (!selectedFile.isFile) return MinecraftInstanceProfileResult.Failed("No profile selected.")
        val id = runCatching { selectedFile.readText().trim() }.getOrNull()
            ?: return MinecraftInstanceProfileResult.Failed("Could not read selected profile.")
        return when (val result = listProfiles()) {
            is MinecraftInstanceProfileResult.Profiles ->
                result.profiles.firstOrNull { it.id == id }?.let(MinecraftInstanceProfileResult::Selected)
                    ?: MinecraftInstanceProfileResult.Failed("Selected profile no longer exists.")
            is MinecraftInstanceProfileResult.Failed -> result
            else -> MinecraftInstanceProfileResult.Failed("Could not load selected profile.")
        }
    }

    private fun readProfile(directory: File, canonicalRoot: File): MinecraftInstanceProfile? {
        val canonicalDirectory = directory.canonicalFile
        if (!canonicalDirectory.toPath().startsWith(canonicalRoot.toPath())) return null
        val metadata = File(canonicalDirectory, "profile.properties")
        if (!metadata.isFile) return null
        val properties = Properties().apply { metadata.inputStream().use { load(it) } }
        if (properties.getProperty("schema") != "1") return null
        val id = properties.getProperty("id") ?: return null
        if (id != canonicalDirectory.name) return null
        val name = properties.getProperty("name")?.takeIf(String::isNotBlank) ?: return null
        val version = properties.getProperty("minecraftVersion")?.takeIf { it.matches(VERSION_PATTERN) } ?: return null
        val loader = runCatching { MinecraftLoader.valueOf(properties.getProperty("loader") ?: "") }.getOrNull()
            ?.takeIf { it != MinecraftLoader.UNKNOWN } ?: return null
        val gameDirectory = File(canonicalDirectory, ".minecraft").canonicalFile
        if (!gameDirectory.toPath().startsWith(canonicalDirectory.toPath()) || !gameDirectory.isDirectory) return null
        return MinecraftInstanceProfile(id, name, version, loader, canonicalDirectory, gameDirectory)
    }

    companion object {
        private val VERSION_PATTERN = Regex("[0-9]+\\.[0-9]+(?:\\.[0-9]+)?(?:-[A-Za-z0-9.-]+)?")
    }
}
