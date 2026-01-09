package org.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tableau des incidents et crashs.
 * Tableau vide - seules les colonnes sont visibles.
 */
@Composable
fun IncidentsTable() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = 2.dp,
        shape = RoundedCornerShape(8.dp),
        backgroundColor = Color.White
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // En-têtes du tableau
            TableHeader()

            Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)

            // Zone vide pour les futures données
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucun incident disponible",
                    fontSize = 14.sp,
                    color = Color(0xFF999999),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * En-tête du tableau avec les colonnes.
 */
@Composable
fun TableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8F9FA))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell("Horodatage", Modifier.weight(1.2f))
        HeaderCell("Jeu", Modifier.weight(1.5f))
        HeaderCell("Version", Modifier.weight(0.8f))
        HeaderCell("Plateforme", Modifier.weight(0.8f))
        HeaderCell("Gravité", Modifier.weight(0.8f))
        HeaderCell("Description", Modifier.weight(2f))
    }
}

/**
 * Cellule d'en-tête du tableau.
 */
@Composable
fun HeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF333333)
    )
}

