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
 * Composant affichant la distribution des notes.
 * Affiche un graphique en barres horizontales (vide).
 *
 * Prêt pour afficher : barres de progression par nombre d'étoiles
 */
@Composable
fun RatingDistribution(
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
            text = "Distribution des notes",
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Divider(color = Color.LightGray, thickness = 1.dp)

        Spacer(modifier = Modifier.height(24.dp))

        // Zone pour le graphique de distribution
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Placeholder pour chaque niveau d'étoiles (5 à 1)
            repeat(5) { index ->
                RatingBarPlaceholder(stars = 5 - index)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Zone pour le graphique à barres horizontales
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color(0xFFFAFAFA))
                .border(1.dp, Color(0xFFE0E0E0)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Graphique de distribution",
                style = MaterialTheme.typography.body1,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun RatingBarPlaceholder(stars: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Indicateur d'étoiles (placeholder)
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(20.dp)
                .background(Color(0xFFE0E0E0))
        )

        // Barre de progression (placeholder)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(24.dp)
                .padding(horizontal = 16.dp)
                .background(Color(0xFFF0F0F0))
        )

        // Valeur numérique (placeholder)
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(20.dp)
                .background(Color(0xFFE0E0E0))
        )
    }
}

