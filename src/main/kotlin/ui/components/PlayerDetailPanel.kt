package org.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Panneau de détail du profil joueur.
 * Affiche un placeholder lorsqu'aucun joueur n'est sélectionné.
 * Prêt pour afficher les informations détaillées d'un joueur sélectionné.
 */
@Composable
fun PlayerDetailPanel(
    modifier: Modifier = Modifier,
    selectedPlayerId: String? = null
) {
    Column(
        modifier = modifier
            .border(1.dp, Color.LightGray)
            .background(Color.White)
            .padding(24.dp)
    ) {
        Text(
            text = "Profil joueur",
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Divider(color = Color.LightGray, thickness = 1.dp)

        Spacer(modifier = Modifier.height(24.dp))

        if (selectedPlayerId == null) {
            // État vide - Placeholder
            PlayerDetailPlaceholder()
        } else {
            // Zone pour afficher les détails du joueur sélectionné
            // À implémenter avec les données backend
            Text(
                text = "Détails du joueur $selectedPlayerId",
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun PlayerDetailPlaceholder() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Sélectionnez un joueur",
            style = MaterialTheme.typography.subtitle1,
            fontWeight = FontWeight.Medium,
            color = Color.Gray
        )

        Text(
            text = "Profil public",
            style = MaterialTheme.typography.caption,
            color = Color.LightGray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Sections d'informations vides
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            InfoSection("Date d'inscription")
            InfoSection("Jeux possédés")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            InfoSection("Temps de jeu total")
            InfoSection("Évaluations")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section dernières évaluations
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Dernières évaluations",
                style = MaterialTheme.typography.subtitle2,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray
            )

            // Placeholders pour évaluations
            repeat(2) {
                ReviewPlaceholder()
            }
        }
    }
}

@Composable
private fun InfoSection(label: String) {
    Column(
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(20.dp)
                .background(Color(0xFFF0F0F0))
        )
    }
}

@Composable
private fun ReviewPlaceholder() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFAFAFA))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(14.dp)
                    .background(Color(0xFFE0E0E0))
            )
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(12.dp)
                    .background(Color(0xFFE8E8E8))
            )
        }

        Box(
            modifier = Modifier
                .width(60.dp)
                .height(16.dp)
                .background(Color(0xFFE0E0E0))
        )
    }
}

