package org.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Zone de filtres pour la page Incidents & Crashs.
 * Filtres non fonctionnels - UI uniquement.
 */
@Composable
fun IncidentsFilters() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = 2.dp,
        shape = RoundedCornerShape(8.dp),
        backgroundColor = Color.White
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Rangée de filtres
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Filtre Jeu
                FilterDropdown(
                    label = "Jeu",
                    placeholder = "Tous les jeux",
                    modifier = Modifier.weight(1f)
                )

                // Filtre Plateforme
                FilterDropdown(
                    label = "Plateforme",
                    placeholder = "Toutes les plateformes",
                    modifier = Modifier.weight(1f)
                )

                // Filtre Gravité
                FilterDropdown(
                    label = "Gravité",
                    placeholder = "Toutes les gravités",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Composant de filtre dropdown (non fonctionnel).
 */
@Composable
fun FilterDropdown(
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF333333),
            modifier = Modifier.padding(bottom = 4.dp)
        )

        OutlinedButton(
            onClick = { /* Non fonctionnel */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, Color(0xFFCCCCCC)),
            colors = ButtonDefaults.outlinedButtonColors(
                backgroundColor = Color.White
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = placeholder,
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = Color(0xFF666666)
                )
            }
        }
    }
}

