package com.flame.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flame.launcher.content.ContentProviderId
import com.flame.launcher.content.MinecraftContentInstallCoordinator
import com.flame.launcher.content.MinecraftContentInstallCoordinatorResult
import com.flame.launcher.content.MinecraftContentInstallItem
import com.flame.launcher.content.MinecraftContentInstallManifestStore
import com.flame.launcher.content.MinecraftContentProject
import com.flame.launcher.content.MinecraftContentType
import com.flame.launcher.content.MinecraftContentVersion
import com.flame.launcher.content.MinecraftInstanceProfile
import com.flame.launcher.content.MinecraftInstanceProfileResult
import com.flame.launcher.content.MinecraftInstanceProfileStore
import com.flame.launcher.content.MinecraftLoader
import com.flame.launcher.content.ModrinthContentProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Live Modrinth catalog browser with an explicit, app-private profile selector.
 * Profiles created here are content-install targets; they are not runnable game
 * installations until the runtime/version installer milestone is complete.
 */
@Composable
fun MinecraftContentBrowser() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val provider = remember { ModrinthContentProvider() }
    val profileStore = remember(context) {
        MinecraftInstanceProfileStore(File(context.filesDir, "minecraft-instances"))
    }
    val installCoordinator = remember(context) {
        MinecraftContentInstallCoordinator(
            MinecraftContentInstallManifestStore(File(context.filesDir, "managed-content-manifests"))
        )
    }
    var query by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(MinecraftContentType.MOD) }
    var targetMinecraftVersion by remember { mutableStateOf("1.20.1") }
    var selectedLoader by remember { mutableStateOf<MinecraftLoader?>(MinecraftLoader.FABRIC) }
    var projects by remember { mutableStateOf<List<MinecraftContentProject>>(emptyList()) }
    var selectedProject by remember { mutableStateOf<MinecraftContentProject?>(null) }
    var selectedVersion by remember { mutableStateOf<MinecraftContentVersion?>(null) }
    var versions by remember { mutableStateOf<List<MinecraftContentVersion>>(emptyList()) }
    var installStatus by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Search Modrinth to discover Minecraft content.") }
    var profileName by remember { mutableStateOf("") }
    var profileStatus by remember { mutableStateOf<String?>(null) }
    var profiles by remember {
        mutableStateOf(
            (profileStore.listProfiles() as? MinecraftInstanceProfileResult.Profiles)?.profiles.orEmpty()
        )
    }
    var selectedProfile by remember {
        mutableStateOf(
            (profileStore.selectedProfile() as? MinecraftInstanceProfileResult.Selected)?.profile
        )
    }

    fun refreshProfiles() {
        profiles = (profileStore.listProfiles() as? MinecraftInstanceProfileResult.Profiles)
            ?.profiles.orEmpty()
        selectedProfile = (profileStore.selectedProfile() as? MinecraftInstanceProfileResult.Selected)
            ?.profile
    }

    fun createProfile() {
        val loader = selectedLoader ?: MinecraftLoader.FABRIC
        when (val result = profileStore.createProfile(profileName, targetMinecraftVersion, loader)) {
            is MinecraftInstanceProfileResult.Created -> {
                when (val selected = profileStore.selectProfile(result.profile.id)) {
                    is MinecraftInstanceProfileResult.Selected -> {
                        selectedProfile = selected.profile
                        profileStatus = "Created and selected ${selected.profile.name}."
                        profileName = ""
                        targetMinecraftVersion = selected.profile.minecraftVersion
                        selectedLoader = selected.profile.loader
                        refreshProfiles()
                    }
                    is MinecraftInstanceProfileResult.Failed -> profileStatus = selected.reason
                    else -> profileStatus = "Could not select the new profile."
                }
            }
            is MinecraftInstanceProfileResult.Failed -> profileStatus = result.reason
            else -> profileStatus = "Could not create profile."
        }
    }

    fun selectProfile(profile: MinecraftInstanceProfile) {
        when (val result = profileStore.selectProfile(profile.id)) {
            is MinecraftInstanceProfileResult.Selected -> {
                selectedProfile = result.profile
                targetMinecraftVersion = result.profile.minecraftVersion
                selectedLoader = result.profile.loader
                profileStatus = "Selected ${result.profile.name}."
            }
            is MinecraftInstanceProfileResult.Failed -> profileStatus = result.reason
            else -> profileStatus = "Could not select profile."
        }
    }

    fun installSelectedVersion() {
        val profile = selectedProfile
        val project = selectedProject
        val version = selectedVersion
        if (profile == null || project == null || version == null) {
            installStatus = "Select a profile and a content version first."
            return
        }
        loading = true
        error = null
        installStatus = "Resolving dependencies and preparing verified downloads…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                installCoordinator.install(
                    selectedItems = listOf(MinecraftContentInstallItem(project, version)),
                    target = profile.target(),
                    stagingDirectory = File(context.cacheDir, "minecraft-content-staging"),
                    modpackStagingDirectory = File(context.filesDir, "modpack-import-staging"),
                    findVersion = { providerId, projectId, versionId ->
                        if (providerId != ContentProviderId.MODRINTH) {
                            null
                        } else {
                            provider.getProjectVersions(
                                projectId = projectId,
                                minecraftVersion = profile.minecraftVersion,
                                loader = profile.loader
                            ).firstOrNull { versionId == null || it.versionId == versionId }
                        }
                    },
                    findProject = { providerId, projectId ->
                        if (providerId == ContentProviderId.MODRINTH) {
                            provider.getProject(projectId)
                        } else null
                    },
                    progress = { projectId, downloaded, expected ->
                        installStatus = if (expected == null) {
                            "Downloading $projectId: $downloaded bytes"
                        } else {
                            "Downloading $projectId: $downloaded / $expected bytes"
                        }
                    }
                )
            }
            loading = false
            installStatus = when (result) {
                is MinecraftContentInstallCoordinatorResult.Installed -> {
                    val installedFiles = result.plan.items.count { it.action.name == "INSTALL_FILE" }
                    val stagedModpacks = result.plan.items.count { it.action.name == "STAGE_MODPACK_ARCHIVE" }
                    "Installed $installedFiles managed file(s); staged $stagedModpacks modpack archive(s) for import. Manifest saved."
                }
                is MinecraftContentInstallCoordinatorResult.Failed ->
                    buildString {
                        append(result.reason)
                        if (result.cleanupFailures.isNotEmpty()) {
                            append(" Cleanup: ")
                            append(result.cleanupFailures.joinToString("; "))
                        }
                    }
            }
        }
    }

    fun search() {
        loading = true
        error = null
        selectedProject = null
        selectedVersion = null
        installStatus = null
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
        selectedVersion = null
        installStatus = null
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
            "Live public catalog search. CurseForge is not enabled in this build.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Minecraft profiles", style = MaterialTheme.typography.titleMedium)
                Text(
                    selectedProfile?.let {
                        "Active: ${it.name} · Minecraft ${it.minecraftVersion} · ${it.loader.label()}"
                    } ?: "No profile selected yet.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (profiles.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        profiles.forEach { profile ->
                            FilterChip(
                                selected = selectedProfile?.id == profile.id,
                                onClick = { selectProfile(profile) },
                                label = { Text(profile.name) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("New profile name") },
                    singleLine = true
                )
                Button(
                    onClick = { createProfile() },
                    enabled = profileName.isNotBlank() && targetMinecraftVersion.isNotBlank()
                ) {
                    Text("Create & select profile")
                }
                profileStatus?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Profiles use Flame's private app storage. They are isolated content targets, not playable installations yet; the game runtime and world selector are still pending.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text("Catalog filters", style = MaterialTheme.typography.titleSmall)
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
            "Changing these filters does not change the selected profile's version or loader.",
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
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search projects") },
            singleLine = true
        )
        Button(onClick = { search() }, enabled = !loading) {
            Text("Search Modrinth")
        }
        if (loading) CircularProgressIndicator()
        Text(status, style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

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
                        Button(
                            onClick = { selectedVersion = version },
                            enabled = !loading
                        ) {
                            Text(if (selectedVersion?.versionId == version.versionId) "Selected version" else "Select version")
                        }
                    }
                    Button(
                        onClick = { installSelectedVersion() },
                        enabled = !loading && selectedProfile != null && selectedVersion != null
                    ) {
                        Text("Install selected version")
                    }
                    installStatus?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    if (selectedProfile == null) {
                        Text(
                            "Create or select a profile before installing content.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
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
                    Button(onClick = { loadVersions(project) }, enabled = !loading) {
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
