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

/**
 * Tableau principal affichant la liste des editeurs.
 * Structure vide prete pour l'integration backend.
 * Affiche uniquement les en-tetes de colonnes.
 */
@Composable
fun EditorTable(
    modifier: Modifier = Modifier,
    onEditorSelected: ((String?) -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color.LightGray)
            .background(Color.White)
    ) {
        EditorTableHeader()

        Divider(color = Color.LightGray, thickness = 1.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(Color(0xFFFAFAFA)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "En attente des donnees...",
                style = MaterialTheme.typography.body1,
                color = Color.Gray
            )
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

