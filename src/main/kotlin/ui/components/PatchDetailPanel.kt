package org.example.ui.components
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
@Composable
fun PatchDetailPanel(modifier: Modifier = Modifier, selectedPatchId: String? = null) {
    Column(modifier = modifier.border(1.dp, Color.LightGray).background(Color.White).padding(24.dp)) {
        Text(text = "Détail du correctif", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        Divider(color = Color.LightGray, thickness = 1.dp)
        Spacer(modifier = Modifier.height(24.dp))
        if (selectedPatchId == null) {
            // When none is selected, show a simple directive
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = "Sélectionnez un correctif", style = MaterialTheme.typography.subtitle1, color = Color.Gray)
            }
        } else {
            Text(text = "Détails du correctif $selectedPatchId", color = Color.Gray)
        }
    }
}
@Composable
private fun PatchDetailPlaceholder() {
    // kept for compatibility but no longer used
}
private val labels = listOf("Informations générales", "Notes de version", "Fichiers modifiés", "Statistiques de téléchargement")
@Composable
private fun DetailSection(label: String) {
    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFFAFAFA)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold, color = Color.DarkGray)
        repeat(2) {
            Box(modifier = Modifier.fillMaxWidth(0.9f).height(14.dp).background(Color(0xFFE0E0E0)))
        }
    }
}
