package com.flame.launcher

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flame.launcher.runtime.MinecraftVersionCatalog
import com.flame.launcher.runtime.MinecraftVersionDetails
import com.flame.launcher.runtime.MinecraftVersionEntry
import com.flame.launcher.runtime.MinecraftVersionManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MinecraftVersionCatalogPanel() {
    val scope = rememberCoroutineScope()
    val catalog = remember { MinecraftVersionCatalog() }
    var manifest by remember { mutableStateOf<MinecraftVersionManifest?>(null) }
    var selectedVersion by remember { mutableStateOf<String?>(null) }
    var selectedDetails by remember { mutableStateOf<MinecraftVersionDetails?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151A23))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                text = "MINECRAFT VERSION CATALOG",
                color = Color(0xFFFF7A1A),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp
            )
            Text(
                text = "Verified Mojang metadata",
                color = Color(0xFFF4F5F7),
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp
            )
            Text(
                text = "Load the official release list. Version JSON is downloaded on an IO thread and checked against Mojang's manifest SHA-1 before its Java requirement and file descriptors are shown.",
                color = Color(0xFF9AA4B2),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        selectedVersion = null
                        selectedDetails = null
                        try {
                            manifest = withContext(Dispatchers.IO) { catalog.loadManifest() }
                        } catch (failure: Exception) {
                            error = failure.message ?: "Unable to load Mojang version metadata."
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading
            ) {
                Text(if (loading) "Loading..." else "Load official releases")
            }

            manifest?.let { loaded ->
                Text(
                    text = "Latest release: " + loaded.latestRelease,
                    color = Color(0xFFFFB36E),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                loaded.versions.asSequence()
                    .filter { it.type == "release" }
                    .take(8)
                    .forEach { entry ->
                        VersionEntryRow(
                            entry = entry,
                            selected = selectedVersion == entry.id,
                            enabled = !loading,
                            onInspect = {
                                scope.launch {
                                    loading = true
                                    error = null
                                    selectedVersion = entry.id
                                    selectedDetails = null
                                    try {
                                        selectedDetails = withContext(Dispatchers.IO) {
                                            catalog.loadVersionDetails(entry)
                                        }
                                    } catch (failure: Exception) {
                                        error = failure.message ?: "Unable to verify version details."
                                    } finally {
                                        loading = false
                                    }
                                }
                            }
                        )
                    }
            }

            selectedDetails?.let { details ->
                Text(
                    text = "Verified " + details.id + " • Java " +
                        (details.javaMajorVersion?.toString() ?: "requirement not declared"),
                    color = Color(0xFFF4F5F7),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = details.libraries.size.toString() +
                        " library entries • asset index " + details.assets +
                        " • client JAR descriptor verified",
                    color = Color(0xFF9AA4B2),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }

            error?.let {
                Text(
                    text = it,
                    color = Color(0xFFFF9A85),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
private fun VersionEntryRow(
    entry: MinecraftVersionEntry,
    selected: Boolean,
    enabled: Boolean,
    onInspect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = entry.id,
                color = if (selected) Color(0xFFFFB36E) else Color(0xFFF4F5F7),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = entry.releaseTime.take(10),
                color = Color(0xFF9AA4B2),
                fontSize = 10.sp
            )
        }
        Button(onClick = onInspect, enabled = enabled) {
            Text("Inspect")
        }
    }
}
