package com.flame.launcher

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flame.launcher.runtime.JavaRuntimeExecutionKind
import com.flame.launcher.runtime.JavaRuntimeManager
import com.flame.launcher.runtime.NativeJvmLibraryProbe
import com.flame.launcher.runtime.NativeJvmLibraryLoader
import com.flame.launcher.runtime.NativeJvmRuntimePreparer
import com.flame.launcher.runtime.NativeJvmRuntimePreparationResult
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun JavaRuntimeStatusPanel() {
    val context = LocalContext.current
    val deviceAbi = Build.SUPPORTED_ABIS.firstOrNull()
        ?: System.getProperty("os.arch").orEmpty()
    val normalizedDeviceAbi = remember(deviceAbi) {
        JavaRuntimeManager.normalizeArchitecture(deviceAbi) ?: "unknown"
    }
    val manager = remember(context.filesDir, deviceAbi) {
        JavaRuntimeManager(
            runtimeRoot = File(context.filesDir, "runtimes"),
            deviceArchitecture = deviceAbi
        )
    }
    var refreshKey by remember(manager) { mutableStateOf(0) }
    val installations = remember(manager, refreshKey) { manager.discover() }
    val compatibleInstallations = remember(installations, normalizedDeviceAbi) {
        installations.filter { it.architecture == normalizedDeviceAbi }
    }
    val coroutineScope = rememberCoroutineScope()
    var probingRuntimeId by remember { mutableStateOf<String?>(null) }
    var probeDiagnostic by remember { mutableStateOf<String?>(null) }
    var preparingRuntimeId by remember { mutableStateOf<String?>(null) }
    var preparationDiagnostic by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151A23))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "JAVA RUNTIME MANAGER",
                    color = Color(0xFFFF7A1A),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                )
                Button(onClick = { refreshKey++ }) {
                    Text("Rescan")
                }
            }
            if (installations.isEmpty()) {
                Text(
                    text = "No managed Java runtime is installed yet.",
                    color = Color(0xFFF4F5F7),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "The registry is ready to discover app-managed JVMs and select the exact Java major version and ABI required by a Minecraft release. No JRE is bundled or downloaded by this build.",
                    color = Color(0xFF9AA4B2),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            } else {
                Text(
                    text = installations.size.toString() + " managed runtime(s) discovered • " +
                        compatibleInstallations.size + " match this device ABI",
                    color = Color(0xFFF4F5F7),
                    fontWeight = FontWeight.SemiBold
                )
                installations.take(4).forEach { runtime ->
                    Text(
                        text = "Java " + runtime.majorVersion + " • " + runtime.architecture +
                            " • " + runtime.id +
                            if (runtime.architecture == normalizedDeviceAbi) "" else " • incompatible ABI",
                        color = if (runtime.architecture == normalizedDeviceAbi) {
                            Color(0xFF9AA4B2)
                        } else {
                            Color(0xFFFF9A85)
                        },
                        fontSize = 12.sp
                    )
                    if (runtime.executionKind == JavaRuntimeExecutionKind.EMBEDDED_JVM &&
                        runtime.architecture == normalizedDeviceAbi
                    ) {
                        Button(
                            enabled = probingRuntimeId == null && preparingRuntimeId == null,
                            onClick = {
                                coroutineScope.launch {
                                    probingRuntimeId = runtime.id
                                    try {
                                        probeDiagnostic = withContext(Dispatchers.IO) {
                                            try {
                                                NativeJvmLibraryProbe().inspect(runtime).diagnostic
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (failure: Exception) {
                                                "ERROR: " + (failure.message ?: failure.javaClass.simpleName)
                                            }
                                        }
                                    } finally {
                                        probingRuntimeId = null
                                    }
                                }
                            }
                        ) {
                            Text(
                                if (probingRuntimeId == runtime.id) "Checking native JVM…"
                                else "Check native JVM compatibility"
                            )
                        }
                        Button(
                            enabled = probingRuntimeId == null && preparingRuntimeId == null,
                            onClick = {
                                coroutineScope.launch {
                                    preparingRuntimeId = runtime.id
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            try {
                                                NativeJvmRuntimePreparer(
                                                    probe = NativeJvmLibraryProbe(),
                                                    loader = NativeJvmLibraryLoader()
                                                ).prepare(runtime)
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (failure: Exception) {
                                                NativeJvmRuntimePreparationResult.Failed(
                                                    "ERROR: " + (failure.message ?: failure.javaClass.simpleName)
                                                )
                                            }
                                        }
                                        preparationDiagnostic = when (result) {
                                            is NativeJvmRuntimePreparationResult.Ready ->
                                                "OK: JVM library retained for this app process. " +
                                                    "This does not create a Java VM or launch Minecraft."
                                            is NativeJvmRuntimePreparationResult.Failed ->
                                                result.diagnostic
                                        }
                                    } finally {
                                        preparingRuntimeId = null
                                    }
                                }
                            }
                        ) {
                            Text(
                                if (preparingRuntimeId == runtime.id) "Preparing JVM…"
                                else "Prepare and retain JVM (restart required to change)"
                            )
                        }
                    }
                }
                probeDiagnostic?.let { diagnostic ->
                    Text(
                        text = diagnostic,
                        color = if (diagnostic.startsWith("OK:")) {
                            Color(0xFF8AD6A0)
                        } else {
                            Color(0xFFFF9A85)
                        },
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
                preparationDiagnostic?.let { diagnostic ->
                    Text(
                        text = diagnostic,
                        color = if (diagnostic.startsWith("OK:")) {
                            Color(0xFF8AD6A0)
                        } else {
                            Color(0xFFFF9A85)
                        },
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
                if (compatibleInstallations.isEmpty()) {
                    Text(
                        text = "None of these runtimes can run on this device architecture.",
                        color = Color(0xFFFF9A85),
                        fontSize = 11.sp
                    )
                }
            }
            Text(
                text = "Device ABI: " + deviceAbi + ". Android JVM execution still needs the matching native runtime bridge before a game can launch.",
                color = Color(0xFF9AA4B2),
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            MojoRuntimeInstallPanel(onInstalled = { refreshKey++ })
        }
    }
}
