package org.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Panneau de detail d'un editeur selectionne.
 * Affiche un etat vide/placeholder en attendant la selection
 * et les donnees backend.
 */
@Composable
fun EditorDetailPanel(
    modifier: Modifier = Modifier,
    selectedEditorId: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color.LightGray)
            .background(Color.White)
            .padding(24.dp)
    ) {
        Text(
            text = "Jeux - Details editeur",
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (selectedEditorId == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .background(Color(0xFFFAFAFA)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Selectionnez un editeur dans le tableau ci-dessus",
                    style = MaterialTheme.typography.body1,
                    color = Color.Gray
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .background(Color(0xFFFAFAFA))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailPlaceholder("Titre du jeu")
                DetailPlaceholder("Plateformes")
                DetailPlaceholder("Dernier correctif")
                DetailPlaceholder("Nombre d'incidents")

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "Details en attente des donnees backend...",
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun DetailPlaceholder(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.Medium,
            color = Color.Gray,
            modifier = Modifier.width(150.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(20.dp)
                .background(Color(0xFFE0E0E0))
        )
    }
}

