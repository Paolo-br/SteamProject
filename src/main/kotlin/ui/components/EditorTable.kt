package org.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.Divider
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.model.Publisher

/**
 * Tableau principal affichant la liste des editeurs.
 * Structure vide prete pour l'integration backend.
 * Affiche uniquement les en-tetes de colonnes.
 */
@Composable
fun EditorTable(
    modifier: Modifier = Modifier,
    publishers: List<Publisher> = emptyList(),
    onEditorSelected: ((String?) -> Unit)? = null,
    visibleRows: Int = 5
) {
    // Approximate heights for header and rows; tweak if you change paddings
    val headerHeight = 56.dp
    val rowHeight = 48.dp
    val totalHeight = headerHeight + (rowHeight * visibleRows)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight),
        elevation = 2.dp
    ) {
        Column {
            EditorTableHeader()
            Divider()

            if (publishers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFFAFAFA)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "En attente des donnees...", style = MaterialTheme.typography.body2, color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(publishers) { pub ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditorSelected?.invoke(pub.id) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = pub.name ?: "-", modifier = Modifier.weight(0.25f))
                            Text(text = pub.gamesPublished.toString(), modifier = Modifier.weight(0.15f))
                            Text(text = pub.totalIncidents?.toString() ?: "-", modifier = Modifier.weight(0.15f))
                            Text(text = pub.patchCount?.toString() ?: "-", modifier = Modifier.weight(0.15f))
                            // Affiche le score qualité calculé par Kafka Streams
                            val qualityDisplay = pub.qualityScore?.let { 
                                String.format("%.1f", it) 
                            } ?: pub.averageRating?.let { 
                                String.format("%.1f", it) 
                            } ?: "-"
                            Text(text = qualityDisplay, modifier = Modifier.weight(0.15f))
                            val react = pub.reactivity?.let { "${it}%" } ?: "-"
                            Text(text = react, modifier = Modifier.weight(0.15f))
                        }
                        Divider(color = Color(0xFFEFEFEF))
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TableHeaderCell("Editeur", weight = 0.25f)
        TableHeaderCell("Jeux publies", weight = 0.15f)
        TableHeaderCell("Incidents totaux", weight = 0.15f)
        TableHeaderCell("Correctifs recents", weight = 0.15f)
        TableHeaderCell("Score qualite", weight = 0.15f)
        TableHeaderCell("Reactivite", weight = 0.15f)
    }
}

@Composable
private fun RowScope.TableHeaderCell(
    text: String,
    weight: Float
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.subtitle2,
        fontWeight = FontWeight.Bold,
        color = Color.DarkGray
    )
}

