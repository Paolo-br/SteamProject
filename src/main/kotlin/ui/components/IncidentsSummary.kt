package org.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Zone de récapitulatif des incidents (cards d'indicateurs).
 * Aucune donnée réelle - UI uniquement.
 */
@Composable
fun IncidentsSummary() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Total incidents
        SummaryCard(
            title = "Total incidents",
            value = "-",
            color = Color(0xFFF5F5F5),
            borderColor = Color(0xFFE0E0E0),
            modifier = Modifier.weight(1f)
        )

        // Critiques
        SummaryCard(
            title = "Critiques",
            value = "-",
            color = Color(0xFFFFF5F5),
            borderColor = Color(0xFFFFCDD2),
            modifier = Modifier.weight(1f)
        )

        // Modérés
        SummaryCard(
            title = "Modérés",
            value = "-",
            color = Color(0xFFFFF8E1),
            borderColor = Color(0xFFFFE082),
            modifier = Modifier.weight(1f)
        )

        // Faibles
        SummaryCard(
            title = "Faibles",
            value = "-",
            color = Color(0xFFFFFDE7),
            borderColor = Color(0xFFFFF59D),
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Card individuelle pour afficher un indicateur de récapitulatif.
 */
@Composable
fun SummaryCard(
    title: String,
    value: String,
    color: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(120.dp),
        elevation = 1.dp,
        shape = RoundedCornerShape(8.dp),
        backgroundColor = color,
        border = BorderStroke(2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF666666)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333)
            )
        }
    }
}

