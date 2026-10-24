package com.voidcraft.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class CosmeticOption(val id: String, val name: String, val description: String, val color: Color)

private val cosmeticOptions = listOf(
    CosmeticOption("void-aura", "Void Aura", "Signature violet energy effect.", Color(0xFF9C7BFF)),
    CosmeticOption("neon-mint", "Neon Mint", "Bright mint profile effect.", Color(0xFF63E8C1)),
    CosmeticOption("arctic-ice", "Arctic Ice", "Cool cyan profile effect.", Color(0xFF63E8ED)),
    CosmeticOption("solar-ember", "Solar Ember", "Warm orange profile effect.", Color(0xFFFF966B))
)

@Composable
fun CosmeticStoreScreen(
    equippedId: String?,
    onEquip: (String) -> Unit,
    onUnequip: () -> Unit,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedButton(onClick = onBack) { Text("← BACK") }
        Text("Cosmetic Store", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Fabric 1.21.1 client cosmetics. Equip a colored halo rendered on player models by the VoidCraft Cosmetics Fabric mod.", color = VoidMuted, fontSize = 13.sp)
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = VoidSurface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("IN-GAME COSMETIC", color = equippedId?.let { id -> cosmeticOptions.find { it.id == id }?.color } ?: VoidMuted, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.height(56.dp).background(equippedId?.let { id -> cosmeticOptions.find { it.id == id }?.color } ?: Color(0xFF30364B), CircleShape).padding(8.dp)) {
                        Box(Modifier.height(40.dp).background(Color(0xFFF4F5FF), CircleShape).padding(5.dp)) {
                            Box(Modifier.height(30.dp).background(equippedId?.let { id -> cosmeticOptions.find { it.id == id }?.color } ?: Color(0xFF30364B), CircleShape))
                        }
                    }
                    Column {
                        Text("VoidCraft Player", fontWeight = FontWeight.Bold)
                        Text(equippedId?.let { id -> cosmeticOptions.find { it.id == id }?.name }?.let { "Equipped: $it" } ?: "No cosmetic equipped", color = VoidMuted, fontSize = 12.sp)
                    }
                }
                OutlinedButton(onClick = onUnequip, enabled = equippedId != null) { Text("UNEQUIP COSMETIC") }
            }
        }
        cosmeticOptions.forEach { option ->
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = VoidSurface)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.height(46.dp).background(option.color, CircleShape).padding(7.dp)) {
                        Box(Modifier.height(32.dp).background(Color(0xFFF4F5FF), CircleShape).padding(4.dp)) {
                            Box(Modifier.height(24.dp).background(option.color, CircleShape))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(option.name, fontWeight = FontWeight.Bold)
                        Text(option.description, color = VoidMuted, fontSize = 12.sp)
                    }
                    if (equippedId == option.id) {
                        Text("EQUIPPED", color = option.color, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    } else {
                        Button(onClick = { onEquip(option.id) }) { Text("EQUIP") }
                    }
                }
            }
        }
        Text("Requires Minecraft 1.21.1 with Fabric Loader, Fabric API, and the VoidCraft Cosmetics mod installed in that instance. This launcher saves the equip selection for the next launch. The effect is client-rendered; other players see it only if they also have a compatible mod.", color = VoidMuted, fontSize = 11.sp)
    }
}
