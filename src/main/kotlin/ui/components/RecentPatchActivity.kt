package org.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.model.Patch
@Composable
fun RecentPatchActivity(modifier: Modifier = Modifier, patches: List<Patch> = emptyList()) {
    Column(modifier = modifier.fillMaxWidth().border(1.dp, Color.LightGray).background(Color.White).padding(24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            Icon(imageVector = Icons.Default.Build, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(24.dp))
            Text(text = "Activité récente des correctifs", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
        }
        Divider(color = Color.LightGray, thickness = 1.dp)
        Spacer(modifier = Modifier.height(24.dp))

        if (patches.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = "-", style = MaterialTheme.typography.body2, color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                items(patches) { p ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "${p.gameName} — ${p.newVersion}", style = MaterialTheme.typography.subtitle2)
                            Text(text = "${p.platform} • ${p.type.name}", style = MaterialTheme.typography.caption, color = Color.Gray)
                        }
                        Text(text = p.releaseDate, style = MaterialTheme.typography.caption, color = Color.Gray)
                    }
                    Divider(color = Color(0xFFF0F0F0), thickness = 1.dp)
                }
            }
        }
    }
}
@Composable
private fun ActivityItemPlaceholder() {
    // kept for compatibility; no longer used
}
