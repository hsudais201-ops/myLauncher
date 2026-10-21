package com.voidcraft.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb

@Composable
fun ThemeStudioScreen(
    currentAccent: Int,
    onApply: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("voidcraft_launcher", android.content.Context.MODE_PRIVATE) }
    val initial = remember { Color(prefs.getInt("theme_studio_accent", currentAccent)) }
    var red by remember { mutableFloatStateOf(initial.red) }
    var green by remember { mutableFloatStateOf(initial.green) }
    var blue by remember { mutableFloatStateOf(initial.blue) }
    val accent = Color(red, green, blue)
    val presets = listOf(
        "Neon Mint" to Color(0xFF63E8C1),
        "Void Violet" to Color(0xFF9C7BFF),
        "Arctic Ice" to Color(0xFF63E8ED),
        "Solar Ember" to Color(0xFFFF966B)
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedButton(onClick = onBack) { Text("← BACK") }
        Text("Theme Studio", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Customize the launcher accent. Preview your colors, choose a preset or adjust RGB channels, then apply.", color = VoidMuted, fontSize = 13.sp)
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = VoidSurface)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("LIVE PREVIEW", fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, color = accent)
                Text("VoidCraft Launcher", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("Your launcher accent will use this color.", color = VoidMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { (name, color) ->
                        OutlinedButton(onClick = { red = color.red; green = color.green; blue = color.blue }) {
                            Text(name, color = color, fontSize = 11.sp)
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Red · ${(red * 255).toInt()}", fontSize = 12.sp)
                    Slider(value = red, onValueChange = { red = it }, valueRange = 0f..1f)
                    Text("Green · ${(green * 255).toInt()}", fontSize = 12.sp)
                    Slider(value = green, onValueChange = { green = it }, valueRange = 0f..1f)
                    Text("Blue · ${(blue * 255).toInt()}", fontSize = 12.sp)
                    Slider(value = blue, onValueChange = { blue = it }, valueRange = 0f..1f)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("CUSTOM ACCENT", fontSize = 10.sp, color = VoidMuted)
                        Text("#%06X".format(accent.toArgb() and 0xFFFFFF), color = accent, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f).height(42.dp).background(accent, RoundedCornerShape(10.dp))) {}
                }
                Button(onClick = { onApply(accent.toArgb()) }, modifier = Modifier.fillMaxWidth()) { Text("APPLY THEME") }
                Text("Your custom accent is saved on this device and applied to the launcher's primary color.", color = VoidMuted, fontSize = 11.sp)
            }
        }
    }
}
