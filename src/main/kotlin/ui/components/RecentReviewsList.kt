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
 * Liste des commentaires récents.
 * Affiche des placeholders pour les commentaires d'évaluation.
 */
@Composable
fun RecentReviewsList(
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
            text = "Commentaires récents",
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Divider(color = Color.LightGray, thickness = 1.dp)

        Spacer(modifier = Modifier.height(24.dp))

        // Liste de placeholders de commentaires
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            repeat(3) {
                ReviewItemPlaceholder()
            }
        }
    }
}

@Composable
private fun ReviewItemPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFAFAFA))
            .border(1.dp, Color(0xFFE8E8E8))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // En-tête : pseudo, plateforme, étoiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.width(100.dp).height(20.dp).background(Color(0xFFE0E0E0)))
                Box(modifier = Modifier.width(40.dp).height(20.dp).background(Color(0xFFD0D0D0)))
            }
            Box(modifier = Modifier.width(100.dp).height(20.dp).background(Color(0xFFE0E0E0)))
        }

        // Nom du jeu
        Box(modifier = Modifier.width(150.dp).height(16.dp).background(Color(0xFFE8E8E8)))

        // Texte du commentaire
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.fillMaxWidth(0.95f).height(14.dp).background(Color(0xFFF0F0F0)))
            Box(modifier = Modifier.fillMaxWidth(0.88f).height(14.dp).background(Color(0xFFF0F0F0)))
        }

        // Pied : date, likes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.width(80.dp).height(14.dp).background(Color(0xFFE8E8E8)))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.width(50.dp).height(16.dp).background(Color(0xFFE0E0E0)))
                Box(modifier = Modifier.width(40.dp).height(16.dp).background(Color(0xFFE0E0E0)))
            }
        }
    }
}

