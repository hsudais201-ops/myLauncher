package com.voidcraft.launcher

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DIAGNOSTICS_PREFS = "voidcraft_preferences"
private const val DIAGNOSTICS_MIB = 1024L * 1024L

private data class DiagnosticCheck(val title: String, val state: String, val details: String)

private data class DiagnosticSnapshot(
    val generatedAt: String,
    val appVersion: String,
    val androidVersion: String,
    val device: String,
    val abis: String,
    val selectedVersion: String,
    val freeStorageBytes: Long,
    val availableMemoryBytes: Long,
    val checks: List<DiagnosticCheck>
) {
    fun toReport(): String = buildString {
        appendLine("Voidcraft device diagnostics")
        appendLine("Generated: $generatedAt")
        appendLine("App version: $appVersion")
        appendLine("Device: $device")
        appendLine("Android: $androidVersion")
        appendLine("Supported ABIs: $abis")
        appendLine("Selected Minecraft version: $selectedVersion")
        appendLine("Internal free storage: ${freeStorageBytes / DIAGNOSTICS_MIB} MiB")
        appendLine("Available RAM at check time: ${availableMemoryBytes / DIAGNOSTICS_MIB} MiB")
        appendLine()
        appendLine("Checks")
        checks.forEach { check -> appendLine("[${check.state}] ${check.title}: ${check.details}") }
        appendLine()
        appendLine("Privacy: this report excludes passwords, social tokens, Microsoft account tokens, chat messages and raw game log contents.")
        appendLine("A PASS result does not prove Minecraft reaches the title screen or enters a world; that requires a real-device launch test.")
    }
}

@Composable
fun LauncherDiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<DiagnosticSnapshot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Run a device check to inspect the local launcher setup.") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        val report = snapshot
        if (uri != null && report != null) {
            scope.launch {
                busy = true
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(report.toReport().toByteArray(Charsets.UTF_8))
                            output.flush()
                        } ?: error("Could not open the selected report destination.")
                    }
                }
                status = result.fold(
                    onSuccess = { "Diagnostics report exported." },
                    onFailure = { "Export failed: ${it.message ?: "file error"}" }
                )
                busy = false
            }
        }
    }

    fun refreshChecks() {
        scope.launch {
            busy = true
            status = "Checking storage, runtime folders, game files and engine availability…"
            val result = withContext(Dispatchers.IO) {
                runCatching { createDiagnosticSnapshot(context) }
            }
            result.onSuccess {
                snapshot = it
                val warnings = it.checks.count { check -> check.state != "PASS" }
                status = if (warnings == 0) {
                    "Checks completed. A real game launch is still required to verify playability."
                } else {
                    "Checks completed with $warnings item(s) to review. See the details below."
                }
            }.onFailure { error ->
                status = "Diagnostics could not finish: ${error.message ?: "unknown error"}"
            }
            busy = false
        }
    }

    LaunchedEffect(Unit) { refreshChecks() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, enabled = !busy) { Text("← BACK") }
            Column(Modifier.padding(top = 5.dp)) {
                Text("Device diagnostics", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text("Preflight • storage • engine • runtime", color = VoidMuted, fontSize = 12.sp)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = VoidSurface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LOCAL READINESS REPORT", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(status, color = VoidMuted, fontSize = 12.sp, lineHeight = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { refreshChecks() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                        if (busy) CircularProgressIndicator(strokeWidth = 2.dp) else Text("RUN CHECKS")
                    }
                    OutlinedButton(
                        onClick = { exportLauncher.launch("voidcraft-diagnostics.txt") },
                        enabled = !busy && snapshot != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("EXPORT REPORT") }
                }
            }
        }

        snapshot?.let { report ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = VoidSurface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(report.device, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Android ${report.androidVersion} • ${report.abis}", color = VoidMuted, fontSize = 11.sp)
                    Text("Selected version: ${report.selectedVersion}", color = VoidMuted, fontSize = 11.sp)
                    Text("Free storage: ${report.freeStorageBytes / DIAGNOSTICS_MIB} MiB", color = VoidMuted, fontSize = 11.sp)
                    Text("Available RAM: ${report.availableMemoryBytes / DIAGNOSTICS_MIB} MiB", color = VoidMuted, fontSize = 11.sp)
                }
            }

            report.checks.forEach { check ->
                val stateColor = when (check.state) {
                    "PASS" -> MaterialTheme.colorScheme.primary
                    "BLOCKED" -> MaterialTheme.colorScheme.error
                    else -> Color(0xFFFFC66D)
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = VoidSurface)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(check.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text(check.state, color = stateColor, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Text(check.details, color = VoidMuted, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }
        }

        Text(
            "These checks do not sign in, change launcher files, install software, or start Minecraft. The report excludes credentials and raw log contents.",
            color = VoidMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}

private fun createDiagnosticSnapshot(context: Context): DiagnosticSnapshot {
    val preferences = context.getSharedPreferences(DIAGNOSTICS_PREFS, Context.MODE_PRIVATE)
    val selected = preferences.getString("selected_game_version", "latest") ?: "latest"
    val versionId = selected.takeIf { it.matches(Regex("^[A-Za-z0-9._-]{1,100}$")) }
    val freeStorage = runCatching { StatFs(context.filesDir.absolutePath).availableBytes }.getOrDefault(0L)
    val memoryInfo = ActivityManager.MemoryInfo()
    (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(memoryInfo)
    val availableMemory = memoryInfo.availMem.coerceAtLeast(0L)
    val androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    val device = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Unknown Android device" }
    val abis = Build.SUPPORTED_ABIS.joinToString(", ").ifBlank { "unknown" }
    val enginePresent = runCatching {
        Class.forName("net.kdt.pojavlaunch.LauncherActivity", false, context.classLoader)
    }.isSuccess

    val gameRoot = resolveEngineStorageDirectory(context, "DIR_GAME_NEW", "minecraft")
    val versionRoot = resolveEngineStorageDirectory(context, "DIR_HOME_VERSION", "minecraft/versions")
    val librariesRoot = resolveEngineStorageDirectory(context, "DIR_HOME_LIBRARY", "minecraft/libraries")
    val assetsRoot = resolveEngineStorageDirectory(context, "ASSETS_PATH", "minecraft/assets")
    val runtimeRoot = resolveEngineStorageDirectory(context, "MULTIRT_HOME", "java-runtimes")
    val logRoot = resolveEngineStorageDirectory(context, "DIR_HOME_LOG", "minecraft/logs")
    val selectedJson = versionId?.let { File(File(versionRoot, it), "$it.json") }
    val selectedJar = versionId?.let { File(File(versionRoot, it), "$it.jar") }
    val installedRuntimeCount = runtimeRoot.listFiles().orEmpty().count { directory ->
        directory.isDirectory && File(directory, "bin/java").isFile && File(directory, "release").isFile
    }
    val modsDirectory = File(gameRoot, "mods")
    val modCount = modsDirectory.listFiles().orEmpty().count {
        it.isFile && it.name.endsWith(".jar", ignoreCase = true)
    }
    val latestLog = File(logRoot, "latest.log")

    val checks = listOf(
        DiagnosticCheck(
            "Android CPU architecture",
            if (Build.SUPPORTED_ABIS.any { it in setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") }) "PASS" else "BLOCKED",
            "Detected ABI list: $abis. The integrated engine and runtime packages must support the device ABI."
        ),
        DiagnosticCheck(
            "Integrated Java/LWJGL engine",
            if (enginePresent) "PASS" else "BLOCKED",
            if (enginePresent) "The upstream launcher activity class is present in this app package."
            else "The integrated launcher activity is missing. Use the combined engine APK workflow artifact, not the standalone frontend APK."
        ),
        DiagnosticCheck(
            "Internal storage",
            when {
                freeStorage <= 0L -> "BLOCKED"
                freeStorage < 1024L * DIAGNOSTICS_MIB -> "CHECK"
                else -> "PASS"
            },
            "${freeStorage / DIAGNOSTICS_MIB} MiB free in app storage. Large game assets and libraries may require several gigabytes."
        ),
        DiagnosticCheck(
            "Available memory",
            if (availableMemory < 768L * DIAGNOSTICS_MIB) "CHECK" else "PASS",
            "${availableMemory / DIAGNOSTICS_MIB} MiB available to the system. Actual Minecraft memory needs vary by version, renderer, mods and device."
        ),
        DiagnosticCheck(
            "Game directory",
            if (gameRoot.isDirectory && gameRoot.canRead() && gameRoot.canWrite()) "PASS" else "CHECK",
            if (gameRoot.isDirectory) "Game directory is available for read/write access."
            else "Game directory is not created yet. The integrated engine may create it during initial setup."
        ),
        DiagnosticCheck(
            "Selected version metadata",
            when {
                selected == "latest" -> "CHECK"
                selectedJson?.isFile == true -> "PASS"
                else -> "CHECK"
            },
            when {
                selected == "latest" -> "No exact release selected. Choose a version and use INSPECT or PREPARE FULL VERSION FILES."
                selectedJson?.isFile == true -> "Metadata JSON for $selected exists."
                else -> "Metadata JSON for $selected was not found in the active version directory. Inspect the version or use the integrated engine installer."
            }
        ),
        DiagnosticCheck(
            "Selected client JAR",
            when {
                selected == "latest" -> "CHECK"
                selectedJar?.isFile == true -> "PASS"
                else -> "CHECK"
            },
            when {
                selected == "latest" -> "Select an exact Minecraft version before checking its client JAR."
                selectedJar?.isFile == true -> "Client JAR for $selected is present (${selectedJar.length() / DIAGNOSTICS_MIB} MiB)."
                else -> "Client JAR for $selected is not present at the expected location."
            }
        ),
        DiagnosticCheck(
            "Java runtimes",
            if (installedRuntimeCount > 0) "PASS" else "CHECK",
            "$installedRuntimeCount runtime folder(s) contain both bin/java and release metadata. Confirm the Java major version matches the selected game."
        ),
        DiagnosticCheck(
            "Libraries and assets",
            if (librariesRoot.isDirectory && File(assetsRoot, "objects").isDirectory) "PASS" else "CHECK",
            "Libraries directory: ${if (librariesRoot.isDirectory) "present" else "missing"}; asset objects directory: ${if (File(assetsRoot, "objects").isDirectory) "present" else "missing"}. Presence does not prove every required file is installed."
        ),
        DiagnosticCheck(
            "Mods folder",
            if (modsDirectory.isDirectory) "PASS" else "CHECK",
            "$modCount enabled .jar mod file(s) found in the active game directory. Mods must match the game version and installed loader."
        ),
        DiagnosticCheck(
            "Latest game log",
            if (latestLog.isFile && latestLog.length() > 0L) "PASS" else "CHECK",
            if (latestLog.isFile && latestLog.length() > 0L) "A latest.log file exists (${latestLog.length() / 1024L} KiB). Raw log contents are excluded from the export."
            else "No latest.log was found in the engine's standard log directory. Launch the game once to generate logs."
        )
    )

    @Suppress("DEPRECATION")
    val appVersion = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
        .getOrNull().orEmpty().ifBlank { "unknown" }
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())
    return DiagnosticSnapshot(
        generatedAt = timestamp,
        appVersion = appVersion,
        androidVersion = androidVersion,
        device = device,
        abis = abis,
        selectedVersion = selected,
        freeStorageBytes = freeStorage,
        availableMemoryBytes = availableMemory,
        checks = checks
    )
}
