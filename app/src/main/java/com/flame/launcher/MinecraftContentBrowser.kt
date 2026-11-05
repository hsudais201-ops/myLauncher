package com.flame.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flame.launcher.content.ContentCompatibility
import com.flame.launcher.content.MinecraftContentProject
import com.flame.launcher.content.MinecraftContentType
import com.flame.launcher.content.MinecraftContentVersion
import com.flame.launcher.content.MinecraftContentCompatibility
import com.flame.launcher.content.MinecraftInstanceTarget
import com.flame.launcher.content.MinecraftLoader
import com.flame.launcher.content.ModrinthContentProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * First live content-browser slice. Search and version discovery are read-only;
 * installation stays disabled until a real profile filesystem/transaction layer exists.
 */
@Composable
fun MinecraftContentBrowser() {
    val scope = rememberCoroutineScope()
    val provider = remember { ModrinthContentProvider() }
    var query by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(MinecraftContentType.MOD) }
    var targetMinecraftVersion by remember { mutableStateOf("1.20.1") }
    var selectedLoader by remember { mutableStateOf<MinecraftLoader?>(MinecraftLoader.FABRIC) }
    var projects by remember { mutableStateOf<List<MinecraftContentProject>>(emptyList()) }
    var selectedProject by remember { mutableStateOf<MinecraftContentProject?>(null) }
    var versions by remember { mutableStateOf<List<MinecraftContentVersion>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Search Modrinth to discover Minecraft content.") }

    fun search() {
        loading = true
        error = null
        selectedProject = null
        versions = emptyList()
        scope.launch {
            try {
                projects = withContext(Dispatchers.IO) {
                    provider.searchProjects(
                        query = query,
                        type = selectedType,
                        minecraftVersion = targetMinecraftVersion.takeIf(String::isNotBlank),
                        loader = selectedLoader,
                        limit = 20
                    )
                }
                status = "Showing ${projects.size} Modrinth results."
            } catch (failure: Exception) {
                error = failure.message ?: "Could not load Modrinth projects."
                status = "Search failed."
            } finally {
                loading = false
            }
        }
    }

    fun loadVersions(project: MinecraftContentProject) {
        loading = true
        error = null
        selectedProject = project
        versions = emptyList()
        scope.launch {
            try {
                versions = withContext(Dispatchers.IO) {
                    provider.getProjectVersions(
                        projectId = project.projectId,
                        minecraftVersion = targetMinecraftVersion.takeIf(String::isNotBlank),
                        loader = selectedLoader
                    )
                }
                status = "Found ${versions.size} versions for ${project.title}."
            } catch (failure: Exception) {
                error = failure.message ?: "Could not load project versions."
                status = "Version lookup failed."
            } finally {
                loading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Browse Modrinth",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Live public catalog search. CurseForge and installation are not enabled in this build.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Target filters", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = targetMinecraftVersion,
            onValueChange = { targetMinecraftVersion = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Minecraft version (for example, 1.20.1)") },
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf<MinecraftLoader?>(
                null,
                MinecraftLoader.FABRIC,
                MinecraftLoader.FORGE,
                MinecraftLoader.NEOFORGE,
                MinecraftLoader.QUILT
            ).forEach { loader ->
                FilterChip(
                    selected = selectedLoader == loader,
                    onClick = { selectedLoader = loader },
                    label = { Text(loader?.label() ?: "Any loader") }
                )
            }
        }
        Text(
            "These are catalog filters only; Flame is not connected to an installed instance yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                MinecraftContentType.MOD,
                MinecraftContentType.MODPACK,
                MinecraftContentType.RESOURCE_PACK
            ).forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = type },
                    label = { Text(type.label()) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(MinecraftContentType.SHADER, MinecraftContentType.DATAPACK).forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = type },
                    label = { Text(type.label()) }
                )
            }
        }
        OutlinedTextField(
            value = targetMinecraftVersion,
            onValueChange = { targetMinecraftVersion = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Target Minecraft version") },
            placeholder = { Text("e.g. 1.20.1") },
            singleLine = true
        )
        Text("Mod loader", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = selectedLoader == null,
                onClick = { selectedLoader = null },
                label = { Text("All") }
            )
            listOf(
                MinecraftLoader.FABRIC,
                MinecraftLoader.FORGE,
                MinecraftLoader.NEOFORGE,
                MinecraftLoader.QUILT
            ).forEach { loader ->
                FilterChip(
                    selected = selectedLoader == loader,
                    onClick = { selectedLoader = loader },
                    label = { Text(loader.label()) }
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search projects") },
            singleLine = true
        )
        Button(
            onClick = { search() },
            enabled = !loading
        ) {
            Text("Search Modrinth")
        }
        if (loading) CircularProgressIndicator()
        Text(status, style = MaterialTheme.typography.bodySmall)
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        selectedProject?.let { project ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(project.title, style = MaterialTheme.typography.titleMedium)
                    Text("Versions", style = MaterialTheme.typography.labelLarge)
                    if (versions.isEmpty() && !loading) Text("No compatible versions returned.")
                    versions.take(20).forEach { version ->
                        Text(
                            "${version.displayName} · ${version.gameVersions.joinToString()} · ${version.loaders.joinToString { it.name }}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Dependencies: ${version.dependencies.count { it.type.name == "REQUIRED" }} required",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text(
                        "Installation is not available yet; no files have been changed.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        projects.forEach { project ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(project.title, style = MaterialTheme.typography.titleMedium)
                    Text("by ${project.author}", style = MaterialTheme.typography.labelMedium)
                    Text(project.description, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${project.downloads} downloads",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { loadVersions(project) },
                        enabled = !loading
                    ) {
                        Text("View versions")
                    }
                }
            }
        }
    }
}

private fun MinecraftLoader.label(): String = when (this) {
    MinecraftLoader.FABRIC -> "Fabric"
    MinecraftLoader.FORGE -> "Forge"
    MinecraftLoader.NEOFORGE -> "NeoForge"
    MinecraftLoader.QUILT -> "Quilt"
    MinecraftLoader.UNKNOWN -> "Unknown"
}

private fun MinecraftContentType.label(): String = when (this) {
    MinecraftContentType.MOD -> "Mods"
    MinecraftContentType.MODPACK -> "Modpacks"
    MinecraftContentType.RESOURCE_PACK -> "Resource packs"
    MinecraftContentType.SHADER -> "Shaders"
    MinecraftContentType.DATAPACK -> "Datapacks"
}
