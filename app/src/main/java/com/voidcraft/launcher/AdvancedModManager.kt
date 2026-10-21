package com.voidcraft.launcher

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

private val ModManagerSurface = androidx.compose.ui.graphics.Color(0xFF141824)
private val ModManagerMuted = androidx.compose.ui.graphics.Color(0xFF9AA3B8)
private const val MAX_MOD_BYTES = 100L * 1024L * 1024L

private data class ManagedModFile(
    val file: File,
    val enabled: Boolean
)

@Composable
fun AdvancedModManagerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mods by remember { mutableStateOf<List<ManagedModFile>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Import compatible .jar mod files to begin.") }
    var statusIsError by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<File?>(null) }

    fun refresh() {
        val root = File(context.filesDir, "advanced-mod-manager")
        val enabled = File(root, "enabled").listFiles().orEmpty().filter { it.isFile && it.extension.equals("jar", true) }
        val disabled = File(root, "disabled").listFiles().orEmpty().filter { it.isFile && it.extension.equals("jar", true) }
        mods = (enabled.map { ManagedModFile(it, true) } + disabled.map { ManagedModFile(it, false) })
            .sortedBy { it.file.name.lowercase() }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/java-archive")) { uri: Uri? ->
        val source = pendingExport
        pendingExport = null
        if (uri == null || source == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        source.inputStream().use { input -> input.copyTo(output) }
                    } ?: error("Could not open the destination file.")
                }
            }
            result.onSuccess {
                status = "Exported ${source.name}."
                statusIsError = false
            }.onFailure {
                status = "Export failed: ${it.message ?: "file error"}"
                statusIsError = true
            }
            busy = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            status = "Checking mod archive…"
            statusIsError = false
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                        ?.takeIf { it.isNotBlank() } ?: "imported-mod.jar"
                    if (!displayName.endsWith(".jar", true)) error("Choose a .jar mod file.")
                    val root = File(context.filesDir, "advanced-mod-manager")
                    val enabledDir = File(root, "enabled").apply { mkdirs() }
                    val temp = File(enabledDir, "import-${System.currentTimeMillis()}.part")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var total = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > MAX_MOD_BYTES) error("Mod files must be 100 MB or smaller.")
                                output.write(buffer, 0, read)
                            }
                        }
                    } ?: error("Could not read the selected file.")
                    if (temp.length() == 0L) {
                        temp.delete()
                        error("The selected file is empty.")
                    }
                    try {
                        ZipFile(temp).use { archive ->
                            if (archive.entries().asSequence().none()) error("The selected JAR archive is empty or invalid.")
                        }
                    } catch (e: Exception) {
                        temp.delete()
                        throw IllegalArgumentException("This file is not a valid JAR/ZIP archive.")
                    }
                    val clean = displayName.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(100).ifBlank { "mod.jar" }
                    val baseName = clean.removeSuffix(".jar").removeSuffix(".JAR").ifBlank { "mod" }
                    var destination = File(enabledDir, "$baseName.jar")
                    var suffix = 2
                    while (destination.exists()) {
                        destination = File(enabledDir, "$baseName-$suffix.jar")
                        suffix++
                    }
                    if (!temp.renameTo(destination)) {
                        temp.copyTo(destination, overwrite = false)
                        temp.delete()
                    }
                    destination
                }
            }
            result.onSuccess {
                refresh()
                status = "Imported ${it.name}. It is enabled in your local mod library."
                statusIsError = false
            }.onFailure {
                status = it.message ?: "Could not import this file."
                statusIsError = true
            }
            busy = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("ADVANCED MOD MANAGER", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Text("Your mod library", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Import, organize, enable, disable, export, or remove JAR files. Files stay in VoidCraft's private app storage.", color = ModManagerMuted, fontSize = 13.sp, lineHeight = 19.sp)
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = ModManagerSurface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { importLauncher.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Text("  IMPORT JAR")
                    }
                    OutlinedButton(onClick = { refresh(); status = "Mod library refreshed."; statusIsError = false }, enabled = !busy) {
                        Text("REFRESH")
                    }
                }
                if (busy) CircularProgressIndicator()
                Text(status, color = if (statusIsError) androidx.compose.ui.graphics.Color(0xFFFF9C9C) else ModManagerMuted, fontSize = 12.sp, lineHeight = 17.sp)
                Text("${mods.size} mod file(s) · ${mods.count { it.enabled }} enabled · ${mods.count { !it.enabled }} disabled", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (mods.isEmpty()) {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = ModManagerSurface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Your library is empty", fontWeight = FontWeight.Bold)
                    Text("Choose a JAR file from your device to add it. Only valid, non-empty archives up to 100 MB are accepted.", color = ModManagerMuted, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }
        mods.forEach { mod ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = ModManagerSurface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(mod.file.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${formatModBytes(mod.file.length())} · ${if (mod.enabled) "Enabled in library" else "Disabled"}", color = ModManagerMuted, fontSize = 11.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val destinationDir = File(File(context.filesDir, "advanced-mod-manager"), if (mod.enabled) "disabled" else "enabled")
                                destinationDir.mkdirs()
                                val destination = File(destinationDir, mod.file.name)
                                if (destination.exists()) {
                                    status = "A file with that name already exists in the target folder."
                                    statusIsError = true
                                } else if (mod.file.renameTo(destination)) {
                                    refresh()
                                    status = "${destination.name} ${if (mod.enabled) "disabled" else "enabled"} in your library."
                                    statusIsError = false
                                } else {
                                    status = "Could not move the mod file."
                                    statusIsError = true
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !busy
                        ) {
                            Icon(if (mod.enabled) Icons.Default.ToggleOff else Icons.Default.ToggleOn, contentDescription = null)
                            Text(if (mod.enabled) "  DISABLE" else "  ENABLE")
                        }
                        OutlinedButton(onClick = { pendingExport = mod.file; exportLauncher.launch(mod.file.name) }, enabled = !busy) {
                            Icon(Icons.Default.Download, contentDescription = "Export mod")
                        }
                        OutlinedButton(
                            onClick = {
                                if (mod.file.delete()) {
                                    refresh()
                                    status = "Removed ${mod.file.name} from the local library."
                                    statusIsError = false
                                } else {
                                    status = "Could not remove this file."
                                    statusIsError = true
                                }
                            },
                            enabled = !busy
                        ) { Icon(Icons.Default.Delete, contentDescription = "Remove mod") }
                    }
                }
            }
        }
        Surface(color = androidx.compose.ui.graphics.Color(0xFF302719), shape = RoundedCornerShape(14.dp)) {
            Text(
                "Compatibility note: the Minecraft Java runtime and game-profile folder are not integrated yet. Enable/disable currently organizes your local library; it does not load mods into a running game. Only install mods from sources you trust.",
                modifier = Modifier.padding(14.dp),
                color = androidx.compose.ui.graphics.Color(0xFFFFD99B),
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

private fun formatModBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}
