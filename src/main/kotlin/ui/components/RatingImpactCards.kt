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
 * Zone d'impact des évaluations.
 * Affiche des cards de synthèse sur les types d'évaluations.
 *
 * Contient : Évaluations positives, neutres, négatives
 */
@Composable
fun RatingImpactCards(
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
            text = "Impact des évaluations",
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Divider(color = Color.LightGray, thickness = 1.dp)

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card positive (verte)
            ImpactCard(
                title = "Évaluations positives",
                backgroundColor = Color(0xFFE8F5E9),
                borderColor = Color(0xFFC8E6C9),
                modifier = Modifier.weight(1f)
            )

            // Card neutre (jaune/orange)
            ImpactCard(
                title = "Évaluations neutres",
                backgroundColor = Color(0xFFFFF8E1),
                borderColor = Color(0xFFFFE082),
                modifier = Modifier.weight(1f)
            )

            // Card négative (rouge)
            ImpactCard(
                title = "Évaluations négatives",
                backgroundColor = Color(0xFFFFEBEE),
                borderColor = Color(0xFFFFCDD2),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ImpactCard(
    title: String,
    backgroundColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(backgroundColor)
            .border(1.dp, borderColor)
            .padding(20.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Titre
        Text(
            text = title,
            style = MaterialTheme.typography.subtitle2,
            fontWeight = FontWeight.Bold,
            color = Color.DarkGray
        )

        // Placeholder pour le pourcentage principal
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(40.dp)
                .background(Color(0xFFE0E0E0))
        )

        // Placeholder pour les détails (ex: "4-5 étoiles")
        Box(
            modifier = Modifier
                .width(70.dp)
                .height(16.dp)
                .background(Color(0xFFE8E8E8))
        )
    }
}

