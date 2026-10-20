package com.voidcraft.launcher.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * Metadata-only instance catalog contract.
 *
 * remove() removes a catalog entry, never the game directory, saves, or mods.
 */
interface InstanceCatalogRepository {
    fun list(): List<InstanceProfile>
    fun get(id: String): InstanceProfile?
    fun upsert(profile: InstanceProfile)
    fun remove(id: String): Boolean
}

/**
 * Simple versioned catalog saved in an app-private directory supplied by the
 * caller (on Android, prefer Context.filesDir). Writes use a temporary file
 * and an atomic rename where the filesystem supports it.
 *
 * This class intentionally does not discover, import, or migrate existing
 * launcher data. A malformed/unknown catalog is surfaced as an IOException
 * rather than silently treated as empty and accidentally overwritten.
 */
class FileInstanceCatalogRepository(private val privateDirectory: File) : InstanceCatalogRepository {
    private val catalogFile = File(privateDirectory, CATALOG_FILE_NAME)

    @Synchronized
    override fun list(): List<InstanceProfile> = readCatalog().toList()

    @Synchronized
    override fun get(id: String): InstanceProfile? {
        if (!isSafeId(id)) return null
        return readCatalog().firstOrNull { it.id == id }
    }

    @Synchronized
    override fun upsert(profile: InstanceProfile) {
        val current = readCatalog().toMutableList()
        val existingIndex = current.indexOfFirst { it.id == profile.id }
        if (existingIndex >= 0) {
            current[existingIndex] = profile
        } else {
            if (current.size >= MAX_RECORDS) throw IOException("Instance catalog limit reached")
            current.add(profile)
        }
        writeCatalog(current)
    }

    @Synchronized
    override fun remove(id: String): Boolean {
        if (!isSafeId(id)) return false
        val current = readCatalog().toMutableList()
        val removed = current.removeAll { it.id == id }
        if (removed) writeCatalog(current)
        // Deliberately do not delete anything under instances/<id>/.
        return removed
    }

    private fun readCatalog(): List<InstanceProfile> {
        if (!catalogFile.exists()) return emptyList()
        val properties = Properties()
        FileInputStream(catalogFile).use { properties.load(it) }

        val schema = properties.getProperty(KEY_SCHEMA)?.toIntOrNull()
            ?: throw IOException("Instance catalog has no valid schema version")
        if (schema != SCHEMA_VERSION) {
            throw IOException("Unsupported instance catalog schema: $schema")
        }

        val count = properties.getProperty(KEY_COUNT)?.toIntOrNull()
            ?: throw IOException("Instance catalog has no valid record count")
        if (count < 0 || count > MAX_RECORDS) {
            throw IOException("Instance catalog record count is out of range")
        }

        val records = ArrayList<InstanceProfile>(count)
        val seenIds = HashSet<String>()
        for (index in 0 until count) {
            val prefix = "instance.$index."
            try {
                val id = required(properties, prefix + "id")
                val profile = InstanceProfile(
                    id = id,
                    name = required(properties, prefix + "name"),
                    minecraftVersion = required(properties, prefix + "minecraftVersion"),
                    loaderId = required(properties, prefix + "loaderId"),
                    createdAtEpochMillis = required(properties, prefix + "createdAtEpochMillis").toLong()
                )
                if (!seenIds.add(profile.id)) {
                    throw IOException("Duplicate instance ID in catalog")
                }
                records.add(profile)
            } catch (error: IllegalArgumentException) {
                throw IOException("Invalid instance catalog record at index $index", error)
            } catch (error: NumberFormatException) {
                throw IOException("Invalid numeric field at catalog index $index", error)
            }
        }
        return records
    }

    private fun writeCatalog(records: List<InstanceProfile>) {
        if (records.size > MAX_RECORDS) throw IOException("Instance catalog limit reached")
        if (!privateDirectory.exists() && !privateDirectory.mkdirs()) {
            throw IOException("Could not create app-private catalog directory")
        }
        if (!privateDirectory.isDirectory) {
            throw IOException("Catalog parent is not a directory")
        }

        val properties = Properties()
        properties.setProperty(KEY_SCHEMA, SCHEMA_VERSION.toString())
        properties.setProperty(KEY_COUNT, records.size.toString())
        records.forEachIndexed { index, profile ->
            val prefix = "instance.$index."
            properties.setProperty(prefix + "id", profile.id)
            properties.setProperty(prefix + "name", profile.name)
            properties.setProperty(prefix + "minecraftVersion", profile.minecraftVersion)
            properties.setProperty(prefix + "loaderId", profile.loaderId)
            properties.setProperty(prefix + "createdAtEpochMillis", profile.createdAtEpochMillis.toString())
        }

        val temporaryFile = File(privateDirectory, "$CATALOG_FILE_NAME.tmp")
        try {
            FileOutputStream(temporaryFile).use { output ->
                properties.store(output, "VoidCraft instance catalog; schema $SCHEMA_VERSION")
                output.fd.sync()
            }
            try {
                Files.move(
                    temporaryFile.toPath(),
                    catalogFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                // Same-directory replacement is the safest available fallback.
                Files.move(temporaryFile.toPath(), catalogFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (error: Exception) {
            temporaryFile.delete()
            if (error is IOException) throw error
            throw IOException("Could not write instance catalog", error)
        }
    }

    private fun required(properties: Properties, key: String): String =
        properties.getProperty(key) ?: throw IOException("Missing catalog field: $key")

    private fun isSafeId(value: String): Boolean =
        value.length in 1..64 && value.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"))

    companion object {
        private const val CATALOG_FILE_NAME = "instances.properties"
        private const val SCHEMA_VERSION = 1
        private const val MAX_RECORDS = 10_000
        private const val KEY_SCHEMA = "schema.version"
        private const val KEY_COUNT = "instance.count"
    }
}
