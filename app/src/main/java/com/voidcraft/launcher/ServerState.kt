package com.voidcraft.launcher

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val SERVER_STATE_PREFS = "voidcraft_server_state"
private const val SERVER_STATE_ADDRESSES = "saved_server_addresses"
private const val SERVER_STATE_API = "https://api.mcsrvstat.us/3/"

@Composable
fun ServerStateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(SERVER_STATE_PREFS, Context.MODE_PRIVATE) }
    val addresses = remember { mutableStateOf(prefs.getStringSet(SERVER_STATE_ADDRESSES, emptySet())?.toList()?.sorted() ?: emptyList()) }
    val states = remember { mutableStateMapOf<String, String>() }
    val scope = rememberCoroutineScope()
    var addressInput by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("Server addresses are stored on this device. Status checks send the address to mcsrvstat.us.") }

    fun saveAddresses(next: List<String>) {
        addresses.value = next.distinct().sorted()
        prefs.edit().putStringSet(SERVER_STATE_ADDRESSES, addresses.value.toSet()).apply()
    }

    fun checkServer(address: String) {
        if (busy) return
        busy = true
        states[address] = "Checking live status…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val safeAddress = Uri.encode(address, ":")
                    val connection = (URL(SERVER_STATE_API + safeAddress).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 7000
                        readTimeout = 9000
                        requestMethod = "GET"
                        setRequestProperty("Accept", "application/json")
                    }
                    try {
                        if (connection.responseCode !in 200..299) error("Status service returned HTTP ${connection.responseCode}")
                        val data = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                        if (data.optBoolean("online", false)) {
                            val players = data.optJSONObject("players")
                            val count = if (players != null) "${players.optInt("online", 0)} / ${players.optInt("max", 0)} players" else "Player count unavailable"
                            val version = data.optString("version", "Version unavailable")
                            val motdObject = data.optJSONObject("motd")
                            val motdArray = motdObject?.optJSONArray("clean")
                            val motd = if (motdArray != null) (0 until motdArray.length()).joinToString(" ") { motdArray.optString(it) }.take(180) else "MOTD unavailable"
                            "ONLINE · $count · $version\n$motd"
                        } else {
                            "OFFLINE or not responding. Verify the address and port, then retry."
                        }
                    } finally { connection.disconnect() }
                }
            }
            states[address] = result.getOrElse { "Check failed: ${it.message ?: "network error"}" }
            notice = "Status provider: mcsrvstat.us. Results can be delayed or blocked by some servers."
            busy = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedButton(onClick = onBack) { Text("← BACK") }
        Text("Server State", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Check live Minecraft Java and Bedrock server status, player counts, versions, and MOTD.", color = VoidMuted, fontSize = 13.sp)
        OutlinedTextField(
            value = addressInput,
            onValueChange = { addressInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Server address") },
            placeholder = { Text("play.example.net or play.example.net:25565") },
            singleLine = true
        )
        Button(
            onClick = {
                val address = addressInput.trim()
                val host = address.substringBefore(":").removePrefix("[").removeSuffix("]")
                if (address.isBlank() || address.length > 253 || address.any { it.isWhitespace() || it == '/' || it == '?' || it == '#' } || host.isBlank()) {
                    notice = "Enter a valid hostname or hostname:port."
                } else if (addresses.value.any { it.equals(address, true) }) {
                    notice = "That server is already saved."
                } else if (addresses.value.size >= 30) {
                    notice = "You can save up to 30 server addresses."
                } else {
                    saveAddresses(addresses.value + address)
                    addressInput = ""
                    checkServer(address)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("ADD SERVER") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { scope.launch { val saved = addresses.value; for (item in saved) { while (busy) delay(100); checkServer(item); while (busy) delay(100) } } }, enabled = !busy) { Text("REFRESH ALL") }
            Text("Saved locally · no auto-install", color = VoidMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
        }
        Text(notice, color = VoidMuted, fontSize = 11.sp, lineHeight = 16.sp)
        addresses.value.forEach { address ->
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = VoidSurface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(address, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(states[address] ?: "Not checked yet. Tap Check status to fetch live information.", color = if (states[address]?.startsWith("ONLINE") == true) Color(0xFF70E1C1) else VoidMuted, fontSize = 12.sp, lineHeight = 18.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { checkServer(address) }, enabled = !busy) { Text("CHECK STATUS") }
                        OutlinedButton(onClick = {
                            saveAddresses(addresses.value.filterNot { it == address })
                            states.remove(address)
                        }) { Text("REMOVE") }
                    }
                }
            }
        }
        if (addresses.value.isEmpty()) Text("No servers saved yet. Add a server address to begin.", color = VoidMuted, fontSize = 13.sp)
        Text("Live data comes from the public mcsrvstat.us service. Offline results can mean the server blocks status queries; they are not definitive proof that a server is down.", color = VoidMuted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}
