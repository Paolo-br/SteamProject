package org.example.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Composant affichant une notation sous forme d'étoiles.
 * @param rating Note sur 5 (ex: 4.5)
 * @param maxStars Nombre d'étoiles maximum (par défaut 5)
 */
@Composable
fun RatingStars(
    rating: Double,
    maxStars: Int = 5,
    modifier: Modifier = Modifier,
    starColor: Color = Color(0xFFFFA000)
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(maxStars) { index ->
            Icon(
                imageVector = if (index < rating.toInt()) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = null,
                tint = starColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

