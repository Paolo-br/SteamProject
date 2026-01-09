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
fun PatchTable(modifier: Modifier = Modifier, onPatchSelected: ((String?) -> Unit)? = null) {
    Column(modifier = modifier.fillMaxWidth().border(1.dp, Color.LightGray).background(Color.White)) {
        PatchTableHeader()
        Divider(color = Color.LightGray, thickness = 1.dp)
        Box(modifier = Modifier.fillMaxWidth().height(400.dp).background(Color(0xFFFAFAFA)), contentAlignment = Alignment.Center) {
            Text(text = "En attente des données correctifs...", style = MaterialTheme.typography.body1, color = Color.Gray)
        }
    }
}
@Composable
private fun PatchTableHeader() {
    Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFF5F5F5)).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TableHeaderCell("Correctif", weight = 0.18f)
        TableHeaderCell("Jeu", weight = 0.15f)
        TableHeaderCell("Version", weight = 0.10f)
        TableHeaderCell("Type", weight = 0.10f)
        TableHeaderCell("Plateformes", weight = 0.12f)
        TableHeaderCell("Taille", weight = 0.10f)
        TableHeaderCell("Date", weight = 0.12f)
        TableHeaderCell("Téléchargements", weight = 0.13f)
    }
}
@Composable
private fun RowScope.TableHeaderCell(text: String, weight: Float) {
    Text(text = text, modifier = Modifier.weight(weight), style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold, color = Color.DarkGray)
}
