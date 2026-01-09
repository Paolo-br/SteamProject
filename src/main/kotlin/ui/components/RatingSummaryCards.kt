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
 * Zone de récapitulatif des évaluations.
 * Affiche des cards de synthèse (vides, prêtes pour backend).
 *
 * Contient : Total d'évaluations, Note moyenne, Évaluations ce mois
 */
@Composable
fun RatingSummaryCards(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Card 1 : Total d'évaluations
        SummaryCard(
            title = "Total d'évaluations",
            modifier = Modifier.weight(1f)
        )

        // Card 2 : Note moyenne
        SummaryCard(
            title = "Note moyenne",
            modifier = Modifier.weight(1f)
        )

        // Card 3 : Évaluations ce mois
        SummaryCard(
            title = "Évaluations ce mois",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryCard(
    title: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.White)
            .border(1.dp, Color(0xFFE0E0E0))
            .padding(24.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Titre
        Text(
            text = title,
            style = MaterialTheme.typography.subtitle1,
            color = Color.DarkGray,
            fontWeight = FontWeight.Medium
        )

        // Placeholder pour la valeur principale
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(48.dp)
                .background(Color(0xFFE0E0E0))
        )

        // Placeholder pour information complémentaire
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(20.dp)
                .background(Color(0xFFE8E8E8))
        )
    }
}
