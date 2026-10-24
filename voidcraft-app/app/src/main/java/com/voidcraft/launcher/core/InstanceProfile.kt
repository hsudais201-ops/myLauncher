package com.voidcraft.launcher.core

import java.util.UUID

/**
 * Metadata for a VoidCraft game instance.
 *
 * This model deliberately does not own or mutate the game's directory. The
 * relative path is derived from a validated ID, and destructive operations
 * must be implemented separately with backup and confirmation.
 */
data class InstanceProfile(
    val id: String,
    val name: String,
    val minecraftVersion: String,
    val loaderId: String = "vanilla",
    val createdAtEpochMillis: Long = System.currentTimeMillis()
) {
    init {
        require(ID_PATTERN.matches(id)) { "Instance ID contains unsupported characters" }
        require(name.isNotBlank() && name.length <= MAX_NAME_LENGTH) {
            "Instance name must contain 1-$MAX_NAME_LENGTH characters"
        }
        require(minecraftVersion.isNotBlank() && minecraftVersion.length <= MAX_FIELD_LENGTH) {
            "Minecraft version must contain 1-$MAX_FIELD_LENGTH characters"
        }
        require(LOADER_PATTERN.matches(loaderId)) { "Loader ID contains unsupported characters" }
        require(createdAtEpochMillis >= 0L) { "Creation time must not be negative" }
    }

    /** A logical relative path only; this model does not create the directory. */
    val relativeGameDirectory: String
        get() = "instances/$id/game"

    companion object {
        const val MAX_NAME_LENGTH = 80
        const val MAX_FIELD_LENGTH = 64

        private val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")
        private val LOADER_PATTERN = Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,39}")

        fun new(
            name: String,
            minecraftVersion: String,
            loaderId: String = "vanilla",
            createdAtEpochMillis: Long = System.currentTimeMillis()
        ): InstanceProfile = InstanceProfile(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            minecraftVersion = minecraftVersion.trim(),
            loaderId = loaderId.trim().lowercase(),
            createdAtEpochMillis = createdAtEpochMillis
        )
    }
}
