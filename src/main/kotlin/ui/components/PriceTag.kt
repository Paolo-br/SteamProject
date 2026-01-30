package org.example.ui.components

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

/**
 * Composant affichant un prix formaté.
 * @param price Prix du jeu
 */
@Composable
fun PriceTag(
    price: Double,
    modifier: Modifier = Modifier
) {
    Text(
        text = String.format("%.2f€", price),
        style = MaterialTheme.typography.body1,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

