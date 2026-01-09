package org.example.ui.components
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
@Composable
fun RecentPatchActivity(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().border(1.dp, Color.LightGray).background(Color.White).padding(24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            Icon(imageVector = Icons.Default.Build, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(24.dp))
            Text(text = "Activité récente des correctifs", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
        }
        Divider(color = Color.LightGray, thickness = 1.dp)
        Spacer(modifier = Modifier.height(24.dp))
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) { ActivityItemPlaceholder() }
        }
    }
}
@Composable
private fun ActivityItemPlaceholder() {
    Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFFAFAFA)).border(1.dp, Color(0xFFE8E8E8)).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.width(180.dp).height(20.dp).background(Color(0xFFE0E0E0)))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.width(80.dp).height(14.dp).background(Color(0xFFE8E8E8)))
                Box(modifier = Modifier.width(60.dp).height(14.dp).background(Color(0xFFE8E8E8)))
                Box(modifier = Modifier.width(70.dp).height(14.dp).background(Color(0xFFE8E8E8)))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(80.dp).height(24.dp).background(Color(0xFFE0E0E0)))
            Box(modifier = Modifier.width(60.dp).height(16.dp).background(Color(0xFFE8E8E8)))
        }
    }
}
