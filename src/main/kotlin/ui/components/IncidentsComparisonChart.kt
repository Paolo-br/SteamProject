package org.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Graphe comparatif des incidents par editeur.
 * Affiche une structure vide avec axes et grille,
 * prete pour l'integration des donnees backend.
 */
@Composable
fun IncidentsComparisonChart(
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
            text = "Comparaison des incidents par editeur",
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .background(Color(0xFFFAFAFA))
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize().padding(32.dp)
            ) {
                val width = size.width
                val height = size.height

                drawLine(
                    color = Color.Gray,
                    start = Offset(0f, 0f),
                    end = Offset(0f, height),
                    strokeWidth = 2f
                )

                drawLine(
                    color = Color.Gray,
                    start = Offset(0f, height),
                    end = Offset(width, height),
                    strokeWidth = 2f
                )

                for (i in 0..4) {
                    val y = height * i / 4
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.3f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                }

                val barWidth = 60f
                val spacing = (width - barWidth * 5) / 6

                for (i in 0..4) {
                    val x = spacing + (barWidth + spacing) * i
                    drawRect(
                        color = Color(0xFFE0E0E0),
                        topLeft = Offset(x, height * 0.7f),
                        size = Size(barWidth, height * 0.3f)
                    )
                }
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Graphe en attente des donnees...",
                    style = MaterialTheme.typography.body2,
                    color = Color.Gray
                )
            }
        }
    }
}

