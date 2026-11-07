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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.flame.launcher.runtime.JavaRuntimeManager
import com.flame.launcher.runtime.MojoRuntimeCatalogClient
import com.flame.launcher.runtime.MojoRuntimeCertificate
import com.flame.launcher.runtime.MojoRuntimeInstallProgress
import com.flame.launcher.runtime.MojoRuntimePackageDownloadCoordinator
import com.flame.launcher.runtime.MojoRuntimePackageInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun MojoRuntimeInstallPanel(onInstalled: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deviceAbi = Build.SUPPORTED_ABIS.firstOrNull()
        ?: System.getProperty("os.arch").orEmpty()
    val architecture = remember(deviceAbi) {
        JavaRuntimeManager.normalizeArchitecture(deviceAbi)
    }
    val cancellation = remember { AtomicBoolean(false) }
    DisposableEffect(cancellation) {
        onDispose { cancellation.set(true) }
    }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Signed Mojo runtime packages are available for supported device ABIs.") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151A23))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "INSTALL JAVA RUNTIMES",
                color = Color(0xFFFF7A1A),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp
            )
            Text(
                text = "Download and verify the signed universal and device-specific runtime components, then install them into Flame's private runtime directory.",
                color = Color(0xFF9AA4B2),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            if (architecture == null) {
                Text(
                    text = "This device ABI is not supported by the current Mojo runtime catalog.",
                    color = Color(0xFFFF9A85),
                    fontSize = 12.sp
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(17, 21, 25).forEach { major ->
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                            onClick = {
                                cancellation.set(false)
                                busy = true
                                status = "Preparing Java $major for $architecture…"
                                scope.launch {
                                    try {
                                        val installation = withContext(Dispatchers.IO) {
                                            val publicKey = context.assets.open("mojo-runtime-cert.pem").use {
                                                MojoRuntimeCertificate.readPublicKey(it)
                                            }
                                            val descriptor = MojoRuntimeCatalogClient()
                                                .load(major, architecture)
                                            val packageParent = File(context.cacheDir, "mojo-runtime-packages")
                                            if (!packageParent.exists()) {
                                                check(packageParent.mkdirs()) {
                                                    "Unable to create temporary runtime package directory."
                                                }
                                            }
                                            val packageDirectory = File(packageParent, "java-$major-$architecture")
                                            try {
                                                val downloaded = MojoRuntimePackageDownloadCoordinator().download(
                                                    descriptor = descriptor,
                                                    publicKey = publicKey,
                                                    destinationDirectory = packageDirectory,
                                                    onProgress = { component, received, total ->
                                                        status = if (total != null) {
                                                            "Downloading $component: $received / $total bytes"
                                                        } else {
                                                            "Downloading $component: $received bytes"
                                                        }
                                                    },
                                                    isCancelled = cancellation::get
                                                )
                                                MojoRuntimePackageInstaller(
                                                    runtimeRoot = File(context.filesDir, "runtimes"),
                                                    deviceArchitecture = deviceAbi
                                                ).install(
                                                    downloaded = downloaded,
                                                    onProgress = { progress ->
                                                        when (progress) {
                                                            is MojoRuntimeInstallProgress.Extracting ->
                                                                status = "Extracting ${progress.component}…"
                                                            is MojoRuntimeInstallProgress.Completed ->
                                                                status = "Validating installed Java runtime…"
                                                        }
                                                    },
                                                    isCancelled = cancellation::get
                                                )
                                            } finally {
                                                packageDirectory.deleteRecursively()
                                            }
                                        }
                                        status = "Java ${installation.majorVersion} installed and validated for $architecture."
                                        onInstalled()
                                    } catch (_: CancellationException) {
                                        status = "Runtime installation cancelled. No partial runtime was published."
                                    } catch (failure: Exception) {
                                        status = failure.message ?: "Runtime installation failed."
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        ) {
                            Text("Java $major", fontSize = 11.sp)
                        }
                    }
                }
            }
            if (busy) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        cancellation.set(true)
                        status = "Cancelling after the current safe operation…"
                    }
                ) {
                    Text("Cancel installation")
                }
            }
            Text(
                text = status,
                color = Color(0xFFB7C1CE),
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            Text(
                text = "The runtime installer does not yet provide the Android JVM/LWJGL bridge required to launch Minecraft.",
                color = Color(0xFF9AA4B2),
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
        }
    }
}
