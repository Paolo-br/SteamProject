package org.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.model.Patch
@Composable
fun PatchTable(
    modifier: Modifier = Modifier,
    patches: List<Patch>,
    selectedPatchId: String? = null,
    onPatchSelected: ((String?) -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth().border(1.dp, Color.LightGray).background(Color.White)) {
        PatchTableHeader()
        Divider(color = Color.LightGray, thickness = 1.dp)

        if (patches.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(Color(0xFFFAFAFA)), contentAlignment = Alignment.Center) {
                Text(text = "En attente des données correctifs...", style = MaterialTheme.typography.body1, color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 400.dp)) {
                items(patches) { p ->
                    val isSelected = p.id == selectedPatchId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) Color(0xFFE8F4FF) else Color.Transparent)
                            .clickable { onPatchSelected?.invoke(p.id) }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = p.id.take(8), modifier = Modifier.weight(0.18f), style = MaterialTheme.typography.body2)
                        Text(text = p.gameName, modifier = Modifier.weight(0.15f), style = MaterialTheme.typography.body2)
                        Text(text = p.newVersion, modifier = Modifier.weight(0.10f), style = MaterialTheme.typography.body2)
                        Text(text = p.type.name, modifier = Modifier.weight(0.10f), style = MaterialTheme.typography.body2)
                        Text(text = p.platform, modifier = Modifier.weight(0.12f), style = MaterialTheme.typography.body2)
                        Text(text = "${p.sizeInMB} MB", modifier = Modifier.weight(0.10f), style = MaterialTheme.typography.body2)
                        Text(text = p.releaseDate, modifier = Modifier.weight(0.12f), style = MaterialTheme.typography.body2)
                        Text(text = "-", modifier = Modifier.weight(0.13f), style = MaterialTheme.typography.body2)
                    }
                    Divider(color = Color(0xFFF0F0F0), thickness = 1.dp)
                }
            }
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
