package com.flame.launcher.content

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
    data class Updated(val profile: MinecraftInstanceProfile) : MinecraftInstanceProfileResult
    data class Deleted(val profileId: String) : MinecraftInstanceProfileResult
    data class Failed(val reason: String) : MinecraftInstanceProfileResult
}

/**
 * App-private instance registry. Profiles are isolated beneath profilesRoot.
 * These profiles are content-install targets; they are not automatically
 * runnable until the game artifacts and a supported runtime are installed.
 */
class MinecraftInstanceProfileStore(private val profilesRoot: File) {
    private val selectedFile = File(profilesRoot, "selected-profile")

    @Synchronized
    fun createProfile(
        name: String,
        minecraftVersion: String,
        loader: MinecraftLoader
    ): MinecraftInstanceProfileResult {
        val cleanName = validateName(name)
            ?: return MinecraftInstanceProfileResult.Failed("Profile name must be 1–48 safe characters.")
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
            val properties = profileProperties(profile)
            writePropertiesAtomically(File(directory, PROFILE_FILE), properties)
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
        val profile = findProfile(profileId)
            ?: return MinecraftInstanceProfileResult.Failed("Profile does not exist.")
        return try {
            if (!profilesRoot.exists() && !profilesRoot.mkdirs()) {
                return MinecraftInstanceProfileResult.Failed("Could not create profiles directory.")
            }
            writeTextAtomically(selectedFile, profile.id)
            MinecraftInstanceProfileResult.Selected(profile)
        } catch (error: Exception) {
            MinecraftInstanceProfileResult.Failed(error.message ?: "Could not select profile.")
        }
    }

    /** Rename a profile without changing its identity or installed content. */
    @Synchronized
    fun renameProfile(profileId: String, newName: String): MinecraftInstanceProfileResult {
        val cleanName = validateName(newName)
            ?: return MinecraftInstanceProfileResult.Failed("Profile name must be 1–48 safe characters.")
        val profile = findProfile(profileId)
            ?: return MinecraftInstanceProfileResult.Failed("Profile does not exist.")
        return try {
            val metadata = File(profile.instanceDirectory, PROFILE_FILE)
            val properties = Properties().apply {
                metadata.inputStream().use { load(it) }
                setProperty("name", cleanName)
            }
            writePropertiesAtomically(metadata, properties)
            MinecraftInstanceProfileResult.Updated(profile.copy(name = cleanName))
        } catch (error: Exception) {
            MinecraftInstanceProfileResult.Failed(error.message ?: "Could not rename profile.")
        }
    }

    /**
     * Clone a profile's complete private instance, including installed content
     * and launch settings, into a fresh identity. Symlinks are rejected so a
     * profile cannot copy data from outside the app-private profile root.
     */
    @Synchronized
    fun cloneProfile(profileId: String, newName: String): MinecraftInstanceProfileResult {
        val cleanName = validateName(newName)
            ?: return MinecraftInstanceProfileResult.Failed("Profile name must be 1–48 safe characters.")
        val source = findProfile(profileId)
            ?: return MinecraftInstanceProfileResult.Failed("Profile does not exist.")
        return try {
            val root = profilesRoot.canonicalFile
            val sourceDirectory = source.instanceDirectory.canonicalFile
            require(sourceDirectory.toPath().startsWith(root.toPath()) && sourceDirectory != root) {
                "Source profile is outside the profile root."
            }
            val newId = "profile-${UUID.randomUUID()}"
            val staging = File(root, ".clone-${UUID.randomUUID()}.tmp")
            val destination = File(root, newId)
            require(staging.mkdir()) { "Could not create temporary clone directory." }
            try {
                val sourcePath = sourceDirectory.toPath()
                sourceDirectory.walkTopDown().sortedBy { it.toPath().nameCount }.forEach { entry ->
                    require(!Files.isSymbolicLink(entry.toPath())) {
                        "Cannot clone a profile containing symbolic links."
                    }
                    val relative = sourcePath.relativize(entry.toPath())
                    val target = staging.toPath().resolve(relative).normalize()
                    require(target.startsWith(staging.toPath())) { "Profile contains an unsafe path." }
                    if (entry.isDirectory) {
                        Files.createDirectories(target)
                    } else {
                        require(entry.isFile) { "Profile contains an unsupported filesystem entry." }
                        Files.createDirectories(target.parent)
                        Files.copy(entry.toPath(), target)
                    }
                }
                val metadata = File(staging, PROFILE_FILE)
                val properties = Properties().apply {
                    metadata.inputStream().use { load(it) }
                    setProperty("id", newId)
                    setProperty("name", cleanName)
                }
                writePropertiesAtomically(metadata, properties)
                moveAtomically(staging, destination)
            } finally {
                if (staging.exists()) staging.deleteRecursively()
            }
            val cloned = findProfile(newId)
                ?: throw IllegalStateException("Cloned profile failed validation.")
            MinecraftInstanceProfileResult.Created(cloned)
        } catch (error: Exception) {
            MinecraftInstanceProfileResult.Failed(error.message ?: "Could not clone profile.")
        }
    }

    /**
     * Delete only a validated profile directory beneath profilesRoot.
     * The currently selected pointer is cleared before deletion so a failed
     * filesystem removal cannot leave a dangling selected-profile reference.
     */
    @Synchronized
    fun deleteProfile(profileId: String): MinecraftInstanceProfileResult {
        val profile = findProfile(profileId)
            ?: return MinecraftInstanceProfileResult.Failed("Profile does not exist.")
        return try {
            val root = profilesRoot.canonicalFile.toPath()
            val directory = profile.instanceDirectory.canonicalFile
            if (!directory.toPath().startsWith(root) || directory == profilesRoot.canonicalFile) {
                return MinecraftInstanceProfileResult.Failed("Refusing to delete a directory outside the profile root.")
            }
            val selectedId = if (selectedFile.isFile) runCatching { selectedFile.readText().trim() }.getOrNull() else null
            if (selectedId == profile.id && selectedFile.exists() && !selectedFile.delete()) {
                return MinecraftInstanceProfileResult.Failed("Could not clear the selected profile.")
            }
            if (!directory.deleteRecursively()) {
                return MinecraftInstanceProfileResult.Failed("Could not delete profile files.")
            }
            MinecraftInstanceProfileResult.Deleted(profile.id)
        } catch (error: Exception) {
            MinecraftInstanceProfileResult.Failed(error.message ?: "Could not delete profile.")
        }
    }

    @Synchronized
    fun selectedProfile(): MinecraftInstanceProfileResult {
        if (!selectedFile.isFile) return MinecraftInstanceProfileResult.Failed("No profile selected.")
        val id = runCatching { selectedFile.readText().trim() }.getOrNull()
            ?: return MinecraftInstanceProfileResult.Failed("Could not read selected profile.")
        val profile = findProfile(id)
            ?: return MinecraftInstanceProfileResult.Failed("Selected profile no longer exists.")
        return MinecraftInstanceProfileResult.Selected(profile)
    }

    private fun findProfile(profileId: String): MinecraftInstanceProfile? {
        if (!profileId.matches(PROFILE_ID_PATTERN)) return null
        val root = profilesRoot.canonicalFile
        val directory = File(root, profileId)
        return readProfile(directory, root)
    }

    private fun readProfile(directory: File, canonicalRoot: File): MinecraftInstanceProfile? {
        val canonicalDirectory = directory.canonicalFile
        if (!canonicalDirectory.toPath().startsWith(canonicalRoot.toPath())) return null
        val metadata = File(canonicalDirectory, PROFILE_FILE)
        if (!metadata.isFile) return null
        val properties = Properties().apply { metadata.inputStream().use { load(it) } }
        if (properties.getProperty("schema") != "1") return null
        val id = properties.getProperty("id") ?: return null
        if (id != canonicalDirectory.name || !id.matches(PROFILE_ID_PATTERN)) return null
        val name = properties.getProperty("name")?.takeIf(String::isNotBlank) ?: return null
        val version = properties.getProperty("minecraftVersion")?.takeIf { it.matches(VERSION_PATTERN) } ?: return null
        val loader = runCatching { MinecraftLoader.valueOf(properties.getProperty("loader") ?: "") }.getOrNull()
            ?.takeIf { it != MinecraftLoader.UNKNOWN } ?: return null
        val gameDirectory = File(canonicalDirectory, ".minecraft").canonicalFile
        if (!gameDirectory.toPath().startsWith(canonicalDirectory.toPath()) || !gameDirectory.isDirectory) return null
        return MinecraftInstanceProfile(id, name, version, loader, canonicalDirectory, gameDirectory)
    }

    private fun profileProperties(profile: MinecraftInstanceProfile) = Properties().apply {
        setProperty("schema", "1")
        setProperty("id", profile.id)
        setProperty("name", profile.name)
        setProperty("minecraftVersion", profile.minecraftVersion)
        setProperty("loader", profile.loader.name)
    }

    private fun validateName(value: String): String? {
        val clean = value.trim()
        return clean.takeIf {
            it.isNotBlank() && it.length <= 48 &&
                it.none { character -> character == '/' || character == '\\' || character.isISOControl() }
        }
    }

    private fun writePropertiesAtomically(destination: File, properties: Properties) {
        val temporary = File(destination.parentFile, "${destination.name}.${UUID.randomUUID()}.tmp")
        try {
            temporary.outputStream().use { properties.store(it, "Flame Launcher profile") }
            moveAtomically(temporary, destination)
        } finally {
            temporary.delete()
        }
    }

    private fun writeTextAtomically(destination: File, value: String) {
        val temporary = File(destination.parentFile, "${destination.name}.${UUID.randomUUID()}.tmp")
        try {
            temporary.writeText(value)
            moveAtomically(temporary, destination)
        } finally {
            temporary.delete()
        }
    }

    private fun moveAtomically(source: File, destination: File) {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        private const val PROFILE_FILE = "profile.properties"
        private val PROFILE_ID_PATTERN = Regex("profile-[A-Za-z0-9-]{36}")
        private val VERSION_PATTERN = Regex("[0-9]+\\.[0-9]+(?:\\.[0-9]+)?(?:-[A-Za-z0-9.-]+)?")
    }
}
