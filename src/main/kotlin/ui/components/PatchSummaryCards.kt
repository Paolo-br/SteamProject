package org.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PatchSummaryCards(modifier: Modifier = Modifier, fixes: Int = 0, adds: Int = 0, optimizations: Int = 0) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        PatchTypeCard(title = "Corrections", value = if (fixes == 0) "-" else fixes.toString(), modifier = Modifier.weight(1f))
        PatchTypeCard(title = "Ajouts", value = if (adds == 0) "-" else adds.toString(), modifier = Modifier.weight(1f))
        PatchTypeCard(title = "Optimisations", value = if (optimizations == 0) "-" else optimizations.toString(), modifier = Modifier.weight(1f))
    }
}
@Composable
private fun PatchTypeCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.background(Color.White).border(1.dp, Color(0xFFE0E0E0)).padding(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()) {
                Text(text = title, style = MaterialTheme.typography.subtitle1, color = Color.DarkGray, fontWeight = FontWeight.Medium)
                Icon(imageVector = Icons.Default.Build, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            }
            Text(text = value, style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
        }
    }
}
