package org.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.ui.navigation.Screen

/**
 * Écran d'accueil
 */
@Composable
fun HomeScreen(
    onNavigate: (Screen) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Barre latérale de navigation
        Sidebar(onNavigate = onNavigate)

        // Contenu principal
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "SteamProject",
                style = MaterialTheme.typography.h3,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Projet de JVM X Data",
                style = MaterialTheme.typography.subtitle1,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "Sélectionnez une section dans le menu",
                style = MaterialTheme.typography.body1
            )
        }
    }
}

@Composable
private fun Sidebar(
    onNavigate: (Screen) -> Unit
) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .background(Color(0xFFF5F5F5))
            .padding(16.dp)
    ) {
        // Titre
        Text(
            text = "SteamProject",
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        Text(
            text = "Projet de JVM X Data",
            style = MaterialTheme.typography.caption,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Divider()

        Spacer(modifier = Modifier.height(16.dp))

        // Éléments du menu
        MenuItem(
            icon = Icons.Default.PlayArrow,
            label = "Jeux",
            onClick = { onNavigate(Screen.Catalog) }
        )

        MenuItem(
            icon = Icons.Default.AccountCircle,
            label = "Editeurs",
            onClick = { onNavigate(Screen.Editors) }
        )

        MenuItem(
            icon = Icons.Default.Person,
            label = "Joueurs",
            onClick = { onNavigate(Screen.Players) }
        )

        MenuItem(
            icon = Icons.Default.Warning,
            label = "Incidents & Crashs",
            onClick = { onNavigate(Screen.IncidentsCrashs) }
        )

        MenuItem(
            icon = Icons.Default.Star,
            label = "Évaluations",
            onClick = { onNavigate(Screen.Ratings) }
        )

        MenuItem(
            icon = Icons.Default.Build,
            label = "Correctifs / Patches",
            onClick = { onNavigate(Screen.Patches) }
        )

        MenuItem(
            icon = Icons.Default.Phone,
            label = "Par Plateforme",
            onClick = { onNavigate(Screen.Platforms) }
        )

        MenuItem(
            icon = Icons.Default.Add,
            label = "DLC",
            onClick = { onNavigate(Screen.DLC) }
        )

        MenuItem(
            icon = Icons.Default.Check,
            label = "Achats",
            onClick = { onNavigate(Screen.Purchases) }
        )
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colors.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.body1,
            fontSize = 16.sp
        )
    }
}




