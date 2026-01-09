package org.example

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.example.services.ServiceLocator
import org.example.state.NavigationState
import org.example.ui.navigation.Screen
import org.example.ui.screens.*

/**
 * Point d'entrée de l'application Steam Project.
 * Compose Desktop + Navigation simple + Services Mock.
 */
fun main() = application {
    // Initialiser les services au démarrage
    ServiceLocator.initialize()

    Window(
        onCloseRequest = {
            // Arrêter proprement les services avant de quitter
            ServiceLocator.shutdown()
            exitApplication()
        },
        title = "Steam Project - Plateforme de Jeux Vidéo"
    ) {
        MaterialTheme {
            App()
        }
    }
}

/**
 * Composant racine de l'application.
 * Gère la navigation entre les écrans.
 */
@Composable
fun App() {
    val navigationState = remember { NavigationState() }

    // Routeur simple basé sur currentScreen
    when (val screen = navigationState.currentScreen) {
        is Screen.Home -> HomeScreen(
            onNavigate = { navigationState.navigateTo(it) }
        )

        is Screen.Catalog -> CatalogScreen(
            onBack = { navigationState.navigateBack() }
        )

        is Screen.Editors -> EditorsScreen(
            onBack = { navigationState.navigateBack() }
        )

        is Screen.Players -> PlayersScreen(
            onBack = { navigationState.navigateBack() }
        )

        is Screen.IncidentsCrashs -> IncidentsCrashsScreen(
            onBack = { navigationState.navigateBack() }
        )

        is Screen.Patches -> PatchesScreen(
            onBack = { navigationState.navigateBack() }
            // games = emptyList() // Sera rempli par les données du backend
        )

        is Screen.Ratings -> RatingsScreen(
            onBack = { navigationState.navigateBack() }
        )

        is Screen.GameDetail -> GameDetailScreen(
            gameId = screen.gameId,
            onBack = { navigationState.navigateBack() }
        )
    }
}

/**
 * Écran générique pour pages non implémentées.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.h4
        )

        Text(
            text = "Écran factice - Jour 1",
            style = MaterialTheme.typography.body1
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = onBack) {
            Text("← Retour")
        }
    }
}