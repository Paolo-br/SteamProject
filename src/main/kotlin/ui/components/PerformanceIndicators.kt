package org.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.model.Publisher

/**
 * Indicateurs de performance (KPI) des editeurs.
 * Affiche les métriques de qualité, réactivité et nombre de jeux publiés.
 */
@Composable
fun PerformanceIndicators(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
    ) {
        Text(
            text = "Indicateurs de performance",
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Fetch publishers to compute top metrics
            val publishersState by produceState(initialValue = emptyList<Publisher>()) {
                value = try {
                    org.example.services.ServiceLocator.dataService.getPublishers()
                } catch (_: Exception) { emptyList() }
            }

            // Meilleur score qualité (basé sur averageRating qui contient le qualityScore)
            val topByQuality = publishersState
                .filter { it.averageRating != null && it.averageRating!! > 0 }
                .maxByOrNull { it.averageRating ?: 0.0 }

            // Meilleure réactivité
            val topByReactivity = publishersState
                .filter { it.reactivity != null && it.reactivity!! > 0 }
                .maxByOrNull { it.reactivity ?: 0 }

            // Plus de jeux publiés
            val topByGames = publishersState.maxByOrNull { it.gamesPublished }

            PerformanceCard(
                title = "Meilleur score qualité",
                backgroundColor = Color(0xFFE8F5E9),
                textColor = Color(0xFF2E7D32),
                mainText = topByQuality?.name ?: "-",
                subText = topByQuality?.averageRating?.let { String.format("%.1f / 100", it) } ?: "-",
                modifier = Modifier.weight(1f)
            )

            PerformanceCard(
                title = "Meilleure réactivité",
                backgroundColor = Color(0xFFE3F2FD),
                textColor = Color(0xFF1565C0),
                mainText = topByReactivity?.name ?: "-",
                subText = topByReactivity?.reactivity?.let { "$it%" } ?: "-",
                modifier = Modifier.weight(1f)
            )

            PerformanceCard(
                title = "Plus de jeux publiés",
                backgroundColor = Color(0xFFF3E5F5),
                textColor = Color(0xFF6A1B9A),
                mainText = topByGames?.name ?: "-",
                subText = topByGames?.gamesPublished?.toString() ?: "-",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PerformanceCard(
    title: String,
    backgroundColor: Color,
    textColor: Color,
    mainText: String,
    subText: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(180.dp)
            .background(backgroundColor, shape = RoundedCornerShape(8.dp))
            .border(1.dp, backgroundColor.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.subtitle2,
            color = textColor,
            fontWeight = FontWeight.Medium
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(Color.White.copy(alpha = 0.6f), shape = RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = mainText,
                style = MaterialTheme.typography.h5,
                color = textColor,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = subText,
                style = MaterialTheme.typography.caption,
                color = textColor
            )
        }
    }
}

