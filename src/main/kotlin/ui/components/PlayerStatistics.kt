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
 * Zone de statistiques générales des joueurs.
 * Affiche des cards vides avec emplacements pour KPIs.
 * Aucune donnée réelle - uniquement la structure UI.
 *
 * Contient : Joueurs actifs, Temps moyen/joueur, Jeux moyens/joueur, Évaluations/mois
 */
@Composable
fun PlayerStatistics(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color.LightGray)
            .background(Color.White)
            .padding(24.dp)
    ) {
        Text(
            text = "Statistiques des joueurs",
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Divider(color = Color.LightGray, thickness = 1.dp)

        Spacer(modifier = Modifier.height(24.dp))

        // Grid de 4 statistiques principales
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatisticCard(
                title = "Joueurs actifs",
                modifier = Modifier.weight(1f)
            )

            StatisticCard(
                title = "Temps moyen/joueur",
                modifier = Modifier.weight(1f)
            )

            StatisticCard(
                title = "Jeux moyens/joueur",
                modifier = Modifier.weight(1f)
            )

            StatisticCard(
                title = "Évaluations/mois",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatisticCard(
    title: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xFFFAFAFA))
            .border(1.dp, Color(0xFFE0E0E0))
            .padding(20.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Titre de la statistique
        Text(
            text = title,
            style = MaterialTheme.typography.subtitle2,
            color = Color.DarkGray,
            fontWeight = FontWeight.Medium
        )

        // Placeholder pour la valeur principale
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(32.dp)
                .background(Color(0xFFE0E0E0))
        )

        // Placeholder pour une sous-information
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(16.dp)
                .background(Color(0xFFE8E8E8))
        )
    }
}

