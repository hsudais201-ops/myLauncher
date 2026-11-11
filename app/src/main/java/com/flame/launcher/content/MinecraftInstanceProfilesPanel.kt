package com.flame.launcher.content

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Profile and preference controls. This screen manages content instances only;
 * it deliberately does not expose a Launch button until the JVM/game pipeline
 * has passed its device validation gates.
 */
@Composable
fun MinecraftInstanceProfilesPanel() {
    val context = LocalContext.current
    val root = remember(context) { context.getDir("minecraft-instances", Context.MODE_PRIVATE) }
    val profileStore = remember(root) { MinecraftInstanceProfileStore(root) }
    val settingsStore = remember(root) { MinecraftInstanceLaunchSettingsStore(root) }

    fun readProfiles(): List<MinecraftInstanceProfile> =
        (profileStore.listProfiles() as? MinecraftInstanceProfileResult.Profiles)?.profiles.orEmpty()

    fun readSelected(): MinecraftInstanceProfile? =
        (profileStore.selectedProfile() as? MinecraftInstanceProfileResult.Selected)?.profile

    var profiles by remember { mutableStateOf(readProfiles()) }
    var selectedId by remember { mutableStateOf(readSelected()?.id) }
    var profileName by remember { mutableStateOf("") }
    var minecraftVersion by remember { mutableStateOf("1.21.1") }
    var loader by remember { mutableStateOf(MinecraftLoader.FABRIC) }
    var renameValue by remember { mutableStateOf("") }
    var minimumMemory by remember { mutableStateOf("512") }
    var maximumMemory by remember { mutableStateOf("2048") }
    var width by remember { mutableStateOf("1280") }
    var height by remember { mutableStateOf("720") }
    var fullscreen by remember { mutableStateOf(false) }
    var jvmArguments by remember { mutableStateOf("") }
    var gameArguments by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<MinecraftInstanceProfile?>(null) }

    val selected = profiles.firstOrNull { it.id == selectedId }
    fun refresh() {
        profiles = readProfiles()
        selectedId = readSelected()?.id
    }
    fun loadSettings(profile: MinecraftInstanceProfile) {
        when (val result = settingsStore.load(profile)) {
            is MinecraftInstanceLaunchSettingsResult.Loaded -> {
                minimumMemory = result.settings.minimumMemoryMb.toString()
                maximumMemory = result.settings.maximumMemoryMb.toString()
                width = result.settings.width.toString()
                height = result.settings.height.toString()
                fullscreen = result.settings.fullscreen
                jvmArguments = result.settings.jvmArguments
                gameArguments = result.settings.gameArguments
            }
            is MinecraftInstanceLaunchSettingsResult.Failed -> message = result.reason
            is MinecraftInstanceLaunchSettingsResult.Saved -> Unit
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("GAME INSTANCES", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            "Create separate Minecraft content folders and save preferences for each profile. Launching the game is not enabled yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = profileName,
            onValueChange = { profileName = it },
            label = { Text("New profile name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = minecraftVersion,
            onValueChange = { minecraftVersion = it },
            label = { Text("Minecraft version") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(MinecraftLoader.FABRIC, MinecraftLoader.FORGE, MinecraftLoader.NEOFORGE, MinecraftLoader.QUILT).forEach { option ->
                TextButton(onClick = { loader = option }) {
                    Text(if (loader == option) "• ${option.name}" else option.name)
                }
            }
        }
        Button(
            onClick = {
                when (val result = profileStore.createProfile(profileName, minecraftVersion, loader)) {
                    is MinecraftInstanceProfileResult.Created -> {
                        profileStore.selectProfile(result.profile.id)
                        profileName = ""
                        message = "Profile created and selected."
                        refresh()
                        loadSettings(result.profile)
                    }
                    is MinecraftInstanceProfileResult.Failed -> message = result.reason
                    else -> message = "Could not create profile."
                }
            },
            enabled = profileName.isNotBlank() && minecraftVersion.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Create profile") }

        if (profiles.isEmpty()) {
            Text("No profiles yet. Create one above.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("YOUR PROFILES", style = MaterialTheme.typography.labelLarge)
            profiles.forEach { profile ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        when (val result = profileStore.selectProfile(profile.id)) {
                            is MinecraftInstanceProfileResult.Selected -> {
                                message = "Selected ${profile.name}."
                                refresh()
                                loadSettings(result.profile)
                            }
                            is MinecraftInstanceProfileResult.Failed -> message = result.reason
                            else -> Unit
                        }
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (profile.id == selectedId) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(profile.name, style = MaterialTheme.typography.titleSmall)
                        Text("${profile.minecraftVersion} • ${profile.loader.name}", style = MaterialTheme.typography.bodySmall)
                        Text(if (profile.id == selectedId) "Selected" else "Tap to select", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (selected != null) {
            Text("EDIT SELECTED: ${selected.name}", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = renameValue,
                onValueChange = { renameValue = it },
                label = { Text("Rename profile") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        when (val result = profileStore.renameProfile(selected.id, renameValue)) {
                            is MinecraftInstanceProfileResult.Updated -> {
                                renameValue = ""
                                message = "Profile renamed."
                                refresh()
                            }
                            is MinecraftInstanceProfileResult.Failed -> message = result.reason
                            else -> Unit
                        }
                    },
                    enabled = renameValue.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("Rename") }
                TextButton(onClick = { deleteCandidate = selected }) { Text("Delete") }
            }

            Text("LAUNCH PREFERENCES", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = minimumMemory,
                    onValueChange = { minimumMemory = it },
                    label = { Text("Min RAM MB") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = maximumMemory,
                    onValueChange = { maximumMemory = it },
                    label = { Text("Max RAM MB") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = width,
                    onValueChange = { width = it },
                    label = { Text("Width") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it },
                    label = { Text("Height") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Fullscreen", modifier = Modifier.padding(top = 10.dp))
                Switch(checked = fullscreen, onCheckedChange = { fullscreen = it })
            }
            OutlinedTextField(
                value = jvmArguments,
                onValueChange = { jvmArguments = it },
                label = { Text("Extra JVM arguments (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            OutlinedTextField(
                value = gameArguments,
                onValueChange = { gameArguments = it },
                label = { Text("Extra game arguments (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Button(
                onClick = {
                    val values = listOf(minimumMemory, maximumMemory, width, height).mapNotNull(String::toIntOrNull)
                    if (values.size != 4) {
                        message = "RAM and resolution fields must contain whole numbers."
                    } else {
                        val settings = MinecraftInstanceLaunchSettings(
                            minimumMemoryMb = values[0],
                            maximumMemoryMb = values[1],
                            width = values[2],
                            height = values[3],
                            fullscreen = fullscreen,
                            jvmArguments = jvmArguments,
                            gameArguments = gameArguments
                        )
                        when (val result = settingsStore.save(selected, settings)) {
                            is MinecraftInstanceLaunchSettingsResult.Saved -> message = "Preferences saved for ${selected.name}."
                            is MinecraftInstanceLaunchSettingsResult.Failed -> message = result.reason
                            is MinecraftInstanceLaunchSettingsResult.Loaded -> Unit
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save preferences") }
        }

        message?.let {
            Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        }
    }

    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete ${candidate.name}?") },
            text = { Text("This removes this profile's isolated files and saved preferences. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    when (val result = profileStore.deleteProfile(candidate.id)) {
                        is MinecraftInstanceProfileResult.Deleted -> {
                            message = "Profile deleted."
                            deleteCandidate = null
                            refresh()
                            val next = profiles.firstOrNull { it.id == selectedId }
                            if (next != null) loadSettings(next)
                        }
                        is MinecraftInstanceProfileResult.Failed -> {
                            message = result.reason
                            deleteCandidate = null
                            refresh()
                        }
                        else -> deleteCandidate = null
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") }
            }
        )
    }
}
