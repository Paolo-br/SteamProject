package org.example.ui.components
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
@Composable
fun PatchSummaryCards(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        PatchTypeCard(title = "Corrections", modifier = Modifier.weight(1f))
        PatchTypeCard(title = "Ajouts", modifier = Modifier.weight(1f))
        PatchTypeCard(title = "Optimisations", modifier = Modifier.weight(1f))
    }
}
@Composable
private fun PatchTypeCard(title: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(Color.White).border(1.dp, Color(0xFFE0E0E0)).padding(24.dp),
        horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()) {
            Text(text = title, style = MaterialTheme.typography.subtitle1, color = Color.DarkGray, fontWeight = FontWeight.Medium)
            Icon(imageVector = Icons.Default.Build, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(24.dp))
        }
        Box(modifier = Modifier.width(80.dp).height(48.dp).background(Color(0xFFE0E0E0)))
        Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(Color(0xFFF0F0F0)))
        Box(modifier = Modifier.width(100.dp).height(16.dp).background(Color(0xFFE8E8E8)))
    }
}
