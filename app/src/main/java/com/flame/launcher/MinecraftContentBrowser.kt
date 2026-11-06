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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.flame.launcher.content.ContentProviderId
import com.flame.launcher.content.CurseForgeContentProvider
import com.flame.launcher.content.MinecraftContentInstallCoordinator
import com.flame.launcher.content.MinecraftContentInstallCoordinatorResult
import com.flame.launcher.content.MinecraftContentInstallItem
import com.flame.launcher.content.MinecraftContentInstallManifestStore
import com.flame.launcher.content.MinecraftContentInstallManifestResult
import com.flame.launcher.content.MinecraftManagedContentInstall
import com.flame.launcher.content.MinecraftManagedContentUninstallResult
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
 * Live Modrinth and CurseForge catalog browser with an explicit, app-private profile selector.
 * Profiles created here are content-install targets; they are not runnable game
 * installations until the runtime/version installer milestone is complete.
 */
@Composable
fun MinecraftContentBrowser() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val provider = remember { ModrinthContentProvider() }
    var selectedProvider by remember { mutableStateOf(ContentProviderId.MODRINTH) }
    var curseForgeApiKey by remember { mutableStateOf("") }
    val curseForgeProvider = remember(curseForgeApiKey) {
        curseForgeApiKey.trim().takeIf(String::isNotEmpty)?.let(::CurseForgeContentProvider)
    }
    val profileStore = remember(context) {
        MinecraftInstanceProfileStore(File(context.filesDir, "minecraft-instances"))
    }
    val manifestStore = remember(context) {
        MinecraftContentInstallManifestStore(File(context.filesDir, "managed-content-manifests"))
    }
    val installCoordinator = remember(manifestStore) {
        MinecraftContentInstallCoordinator(manifestStore)
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
    var includeOptionalModpackFiles by remember { mutableStateOf(false) }
    var managedEntries by remember {
        mutableStateOf(
            (profileStore.selectedProfile() as? MinecraftInstanceProfileResult.Selected)?.profile?.let { profile ->
                (manifestStore.load(profile.contentDirectory) as? MinecraftContentInstallManifestResult.Loaded)
                    ?.manifest?.entries
            }.orEmpty()
        )
    }
    var pendingUninstall by remember { mutableStateOf<MinecraftManagedContentInstall?>(null) }
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

    fun refreshManagedEntries(profile: MinecraftInstanceProfile?) {
        if (profile == null) {
            managedEntries = emptyList()
            return
        }
        managedEntries = when (val loaded = manifestStore.load(profile.contentDirectory)) {
            is MinecraftContentInstallManifestResult.Loaded -> loaded.manifest.entries
            is MinecraftContentInstallManifestResult.Failed -> {
                installStatus = "Could not load managed content: ${loaded.reason}"
                emptyList()
            }
            is MinecraftContentInstallManifestResult.Saved -> emptyList()
        }
    }

    fun uninstallEntry(entry: MinecraftManagedContentInstall) {
        val profile = selectedProfile ?: return
        loading = true
        installStatus = "Verifying managed file before uninstall…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                manifestStore.uninstallManagedFile(
                    profile.contentDirectory,
                    entry.installedPath
                )
            }
            loading = false
            installStatus = when (result) {
                is MinecraftManagedContentUninstallResult.Uninstalled ->
                    "Uninstalled ${result.entry.fileName}."
                is MinecraftManagedContentUninstallResult.Failed -> result.reason
            }
            if (result is MinecraftManagedContentUninstallResult.Uninstalled) {
                refreshManagedEntries(profile)
            }
        }
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
                        refreshManagedEntries(selected.profile)
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
                refreshManagedEntries(result.profile)
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
            val result = try {
                withContext(Dispatchers.IO) {
                    installCoordinator.install(
                    selectedItems = listOf(MinecraftContentInstallItem(project, version)),
                    target = profile.target(),
                    stagingDirectory = File(context.cacheDir, "minecraft-content-staging"),
                    modpackStagingDirectory = File(context.filesDir, "modpack-import-staging"),
                    findVersion = { providerId, projectId, versionId ->
                        val compatibleVersions = when (providerId) {
                            ContentProviderId.MODRINTH -> provider.getProjectVersions(
                                projectId = projectId,
                                minecraftVersion = profile.minecraftVersion,
                                loader = profile.loader
                            )
                            ContentProviderId.CURSEFORGE -> (curseForgeProvider
                                ?: throw IllegalStateException("Enter your CurseForge API key to resolve dependencies."))
                                .getProjectVersions(
                                    projectId = projectId,
                                    minecraftVersion = profile.minecraftVersion,
                                    loader = profile.loader
                                )
                        }
                        compatibleVersions.firstOrNull { versionId == null || it.versionId == versionId }
                    },
                    findProject = { providerId, projectId ->
                        when (providerId) {
                            ContentProviderId.MODRINTH -> provider.getProject(projectId)
                            ContentProviderId.CURSEFORGE -> (curseForgeProvider
                                ?: throw IllegalStateException("Enter your CurseForge API key to load dependency details."))
                                .getProject(projectId, MinecraftContentType.MOD)
                        }
                    },
                    includeOptionalModpackFiles = includeOptionalModpackFiles,
                    progress = { projectId, downloaded, expected ->
                        installStatus = if (expected == null) {
                            "Downloading $projectId: $downloaded bytes"
                        } else {
                            "Downloading $projectId: $downloaded / $expected bytes"
                        }
                    }
                    )
                }
            } catch (failure: Exception) {
                loading = false
                installStatus = failure.message ?: "Install failed unexpectedly."
                return@launch
            }
            loading = false
            if (result is MinecraftContentInstallCoordinatorResult.Installed) {
                refreshManagedEntries(profile)
            }
            installStatus = when (result) {
                is MinecraftContentInstallCoordinatorResult.Installed -> {
                    val installedFiles = result.plan.items.count { it.action.name == "INSTALL_FILE" }
                    val stagedModpacks = result.plan.items.count { it.action.name == "STAGE_MODPACK_ARCHIVE" }
                    buildString {
                        append("Installed $installedFiles managed file(s); staged $stagedModpacks modpack archive(s). Manifest saved.")
                        if (result.skippedUnsupportedFiles > 0) append(" Skipped ${result.skippedUnsupportedFiles} client-incompatible file(s).")
                        if (result.omittedOptionalFiles > 0) append(" Omitted ${result.omittedOptionalFiles} optional file(s).")
                        if (result.skippedServerOverrideFiles > 0) append(" Skipped ${result.skippedServerOverrideFiles} server override file(s).")
                    }
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
                    when (selectedProvider) {
                        ContentProviderId.MODRINTH -> provider.searchProjects(
                            query = query,
                            type = selectedType,
                            minecraftVersion = targetMinecraftVersion.takeIf(String::isNotBlank),
                            loader = selectedLoader,
                            limit = 20
                        )
                        ContentProviderId.CURSEFORGE -> (curseForgeProvider
                            ?: throw IllegalStateException("Enter your CurseForge API key to search its catalog."))
                            .searchProjects(
                                query = query,
                                type = selectedType,
                                minecraftVersion = targetMinecraftVersion.takeIf(String::isNotBlank),
                                loader = selectedLoader,
                                limit = 20
                            )
                    }
                }
                status = "Showing ${projects.size} ${selectedProvider.name.lowercase().replaceFirstChar(Char::uppercase)} results."
            } catch (failure: Exception) {
                error = failure.message ?: "Could not load ${selectedProvider.name} projects."
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
                    when (project.provider) {
                        ContentProviderId.MODRINTH -> provider.getProjectVersions(
                            projectId = project.projectId,
                            minecraftVersion = targetMinecraftVersion.takeIf(String::isNotBlank),
                            loader = selectedLoader
                        )
                        ContentProviderId.CURSEFORGE -> (curseForgeProvider
                            ?: throw IllegalStateException("Enter your CurseForge API key to view versions."))
                            .getProjectVersions(
                                projectId = project.projectId,
                                minecraftVersion = targetMinecraftVersion.takeIf(String::isNotBlank),
                                loader = selectedLoader
                            )
                    }
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
            "Browse ${selectedProvider.name.lowercase().replaceFirstChar(Char::uppercase)}",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            if (selectedProvider == ContentProviderId.MODRINTH) {
                "Search Modrinth's public catalog."
            } else {
                "CurseForge requires your own API key. The key stays in memory and is not saved by Flame."
            },
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

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Managed content", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Files recorded by Flame for the selected profile. Files changed outside Flame are protected from uninstall.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (selectedProfile == null) {
                    Text("Select a profile to view its managed files.")
                } else if (managedEntries.isEmpty()) {
                    Text("No managed content installed in this profile.")
                } else {
                    managedEntries.forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(entry.fileName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${entry.contentType.label()} · ${entry.provider.name} · ${entry.versionId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { pendingUninstall = entry },
                                enabled = !loading
                            ) {
                                Text("Uninstall")
                            }
                        }
                    }
                }
            }
        }

        Text("Content source", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedProvider == ContentProviderId.MODRINTH,
                onClick = {
                    selectedProvider = ContentProviderId.MODRINTH
                    projects = emptyList()
                    selectedProject = null
                    selectedVersion = null
                    versions = emptyList()
                    error = null
                    status = "Search Modrinth to discover Minecraft content."
                },
                label = { Text("Modrinth") }
            )
            FilterChip(
                selected = selectedProvider == ContentProviderId.CURSEFORGE,
                onClick = {
                    selectedProvider = ContentProviderId.CURSEFORGE
                    projects = emptyList()
                    selectedProject = null
                    selectedVersion = null
                    versions = emptyList()
                    error = null
                    status = "Enter a CurseForge API key to search its catalog."
                },
                label = { Text("CurseForge") }
            )
        }
        if (selectedProvider == ContentProviderId.CURSEFORGE) {
            OutlinedTextField(
                value = curseForgeApiKey,
                onValueChange = { curseForgeApiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("CurseForge API key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            Text(
                "Your key is used only for this app session. Do not share it or commit it to source control.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        Button(
            onClick = { search() },
            enabled = !loading && (selectedProvider == ContentProviderId.MODRINTH || curseForgeApiKey.isNotBlank())
        ) {
            Text("Search ${selectedProvider.name.lowercase().replaceFirstChar(Char::uppercase)}")
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
                    if (project.contentType == MinecraftContentType.MODPACK) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Include optional modpack files")
                                Text(
                                    "Adds files marked optional by the pack author. Required files are always installed.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = includeOptionalModpackFiles,
                                onCheckedChange = { includeOptionalModpackFiles = it },
                                enabled = !loading
                            )
                        }
                    }
                    Button(
                        onClick = { installSelectedVersion() },
                        enabled = !loading && selectedProfile != null && selectedVersion != null &&
                            (project.provider == ContentProviderId.MODRINTH ||
                                curseForgeApiKey.isNotBlank())
                    ) {
                        Text("Install selected version")
                    }
                    if (project.provider == ContentProviderId.CURSEFORGE) {
                        Text(
                            if (curseForgeApiKey.isBlank()) {
                                "Enter your CurseForge API key to resolve dependencies and install this version."
                            } else {
                                "CurseForge installs use provider-aware dependency resolution, compatibility checks, verified downloads, and rollback."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
        pendingUninstall?.let { entry ->
            AlertDialog(
                onDismissRequest = { pendingUninstall = null },
                title = { Text("Uninstall managed content?") },
                text = {
                    Text(
                        "Flame will verify the file fingerprint and remove only ${entry.fileName}. Modified or unmanaged files are protected."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingUninstall = null
                            uninstallEntry(entry)
                        },
                        enabled = !loading
                    ) {
                        Text("Uninstall")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingUninstall = null }) {
                        Text("Cancel")
                    }
                }
            )
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
