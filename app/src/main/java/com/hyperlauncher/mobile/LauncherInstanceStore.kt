package com.hyperlauncher.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Stores independent app-private Minecraft directories for each launcher instance.
 * The legacy minecraft root remains the Default instance so existing installs are preserved.
 */
class LauncherInstanceStore(private val baseStorageRoot: File) {
    private val lock = Any()
    private val registryFile = File(baseStorageRoot, "instances.json")
    private val instancesDirectory = File(baseStorageRoot, "instances")

    fun list(): JSONObject = synchronized(lock) {
        readRegistry().let { snapshot(it) }
    }

    fun create(nameValue: String, versionValue: String): JSONObject = synchronized(lock) {
        val name = validateName(nameValue)
        val version = validateVersion(versionValue)
        val registry = readRegistry()
        val records = registry.getJSONArray("instances")
        for (i in 0 until records.length()) {
            val existing = records.getJSONObject(i).optString("name")
            require(!existing.equals(name, ignoreCase = true)) {
                "An instance with that name already exists"
            }
        }
        val id = UUID.randomUUID().toString()
        val directory = instanceDirectory(id)
        require(directory.mkdirs() || directory.isDirectory) {
            "Could not create the instance directory"
        }
        records.put(
            JSONObject()
                .put("id", id)
                .put("name", name)
                .put("version", version)
                .put("createdAt", System.currentTimeMillis())
        )
        registry.put("selectedId", id)
        try {
            writeRegistry(registry)
        } catch (error: Exception) {
            directory.deleteRecursively()
            throw error
        }
        snapshot(registry)
    }

    fun select(id: String): JSONObject = synchronized(lock) {
        val registry = readRegistry()
        require(findInstance(registry, id) != null) { "That instance does not exist" }
        if (id != DEFAULT_ID) {
            val directory = instanceDirectory(id)
            require(directory.mkdirs() || directory.isDirectory) {
                "Could not create the selected instance directory"
            }
        }
        registry.put("selectedId", id)
        writeRegistry(registry)
        snapshot(registry)
    }

    fun updateSelectedVersion(versionValue: String): JSONObject = synchronized(lock) {
        val version = validateVersion(versionValue)
        val registry = readRegistry()
        val selectedId = registry.optString("selectedId", DEFAULT_ID)
        val instance = findInstance(registry, selectedId)
            ?: throw IllegalStateException("The selected instance is missing")
        instance.put("version", version)
        writeRegistry(registry)
        snapshot(registry)
    }

    fun rename(id: String, newNameValue: String): JSONObject = synchronized(lock) {
        require(id != DEFAULT_ID) { "The Default instance cannot be renamed" }
        val newName = validateName(newNameValue)
        val registry = readRegistry()
        val target = findInstance(registry, id)
            ?: throw IllegalArgumentException("That instance does not exist")
        val records = registry.getJSONArray("instances")
        for (i in 0 until records.length()) {
            val existing = records.getJSONObject(i)
            if (existing.optString("id") != id) {
                require(!existing.optString("name").equals(newName, ignoreCase = true)) {
                    "An instance with that name already exists"
                }
            }
        }
        target.put("name", newName)
        writeRegistry(registry)
        snapshot(registry)
    }

    fun delete(id: String): JSONObject = synchronized(lock) {
        require(id != DEFAULT_ID) { "The Default instance cannot be deleted" }
        val registry = readRegistry()
        require(registry.optString("selectedId", DEFAULT_ID) != id) {
            "Select a different instance before deleting this one"
        }
        require(findInstance(registry, id) != null) { "That instance does not exist" }
        val directory = instanceDirectory(id)
        val parent = instancesDirectory.canonicalFile
        require(directory.canonicalFile.parentFile == parent) {
            "Refusing to delete a directory outside the instances folder"
        }
        val records = registry.getJSONArray("instances")
        val kept = JSONArray()
        for (i in 0 until records.length()) {
            val record = records.getJSONObject(i)
            if (record.optString("id") != id) kept.put(record)
        }
        registry.put("instances", kept)
        writeRegistry(registry)
        if (directory.exists() && !directory.deleteRecursively()) {
            throw IllegalStateException("The instance was removed from the list, but some files could not be deleted")
        }
        snapshot(registry)
    }

    fun storageRoot(id: String): File {
        require(id == DEFAULT_ID || UUID_PATTERN.matches(id)) { "Invalid instance ID" }
        return if (id == DEFAULT_ID) baseStorageRoot else instanceDirectory(id)
    }

    private fun readRegistry(): JSONObject {
        require(baseStorageRoot.exists() || baseStorageRoot.mkdirs()) {
            "Could not create launcher storage"
        }
        if (!registryFile.exists()) {
            val initial = JSONObject()
                .put("schemaVersion", 1)
                .put("selectedId", DEFAULT_ID)
                .put(
                    "instances",
                    JSONArray().put(
                        JSONObject()
                            .put("id", DEFAULT_ID)
                            .put("name", "Default")
                            .put("version", "release")
                            .put("createdAt", 0L)
                    )
                )
            writeRegistry(initial)
            return initial
        }
        require(registryFile.length() in 1L..MAX_REGISTRY_BYTES) {
            "The instance registry has an invalid size"
        }
        val registry = JSONObject(registryFile.readText(Charsets.UTF_8))
        require(registry.optInt("schemaVersion", -1) == 1) {
            "The instance registry version is not supported"
        }
        val records = registry.optJSONArray("instances")
            ?: throw IllegalStateException("The instance registry is incomplete")
        var defaultFound = false
        val seenIds = HashSet<String>()
        for (i in 0 until records.length()) {
            val record = records.optJSONObject(i)
                ?: throw IllegalStateException("The instance registry contains an invalid record")
            val id = record.optString("id")
            validateRecord(record)
            require(seenIds.add(id)) { "The instance registry contains duplicate IDs" }
            if (id == DEFAULT_ID) defaultFound = true
        }
        if (!defaultFound) {
            records.put(
                JSONObject()
                    .put("id", DEFAULT_ID)
                    .put("name", "Default")
                    .put("version", "release")
                    .put("createdAt", 0L)
            )
        }
        val selectedId = registry.optString("selectedId", DEFAULT_ID)
        if (findInstance(registry, selectedId) == null) registry.put("selectedId", DEFAULT_ID)
        if (!defaultFound || selectedId != registry.optString("selectedId")) writeRegistry(registry)
        return registry
    }

    private fun validateRecord(record: JSONObject) {
        val id = record.optString("id")
        require(id == DEFAULT_ID || UUID_PATTERN.matches(id)) { "Invalid instance ID in registry" }
        validateName(record.optString("name"))
        validateVersion(record.optString("version"))
    }

    private fun validateName(value: String): String {
        val name = value.trim()
        require(name == value && name.length in 1..32 &&
            name.matches(Regex("^[A-Za-z0-9][A-Za-z0-9 _.-]{0,31}$")) &&
            name != "." && name != "..") {
            "Use 1–32 letters, numbers, spaces, dots, dashes, or underscores for the instance name"
        }
        return name
    }

    private fun validateVersion(value: String): String {
        val version = value.trim()
        require(version == "release" || version == "snapshot" ||
            version.matches(Regex("^[0-9][A-Za-z0-9._-]{0,63}$"))) {
            "Choose an official Minecraft release, snapshot, or version ID"
        }
        return version
    }

    private fun findInstance(registry: JSONObject, id: String): JSONObject? {
        val records = registry.optJSONArray("instances") ?: return null
        for (i in 0 until records.length()) {
            val instance = records.optJSONObject(i) ?: continue
            if (instance.optString("id") == id) return instance
        }
        return null
    }

    private fun instanceDirectory(id: String): File {
        require(UUID_PATTERN.matches(id)) { "Invalid instance ID" }
        return File(instancesDirectory, id)
    }

    private fun snapshot(registry: JSONObject): JSONObject {
        val copy = JSONObject()
            .put("ok", true)
            .put("selectedId", registry.optString("selectedId", DEFAULT_ID))
            .put("instances", JSONArray(registry.getJSONArray("instances").toString()))
        return copy
    }

    private fun writeRegistry(registry: JSONObject) {
        require(baseStorageRoot.exists() || baseStorageRoot.mkdirs()) {
            "Could not create launcher storage"
        }
        val temporary = File(baseStorageRoot, "instances.json.tmp")
        temporary.writeText(registry.toString(), Charsets.UTF_8)
        try {
            Files.move(
                temporary.toPath(),
                registryFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            Files.move(
                temporary.toPath(),
                registryFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    companion object {
        const val DEFAULT_ID = "default"
        private const val MAX_REGISTRY_BYTES = 256L * 1024L
        private val UUID_PATTERN = Regex("^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$")
    }
}
