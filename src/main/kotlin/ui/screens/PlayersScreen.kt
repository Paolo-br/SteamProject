package org.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.ui.components.*
import org.example.ui.viewmodel.PlayersViewModel

/**
 * Écran Joueurs.
 *
 * Architecture ViewModel :
 * - PlayersViewModel gère l'état et le chargement
 * - Séparation UI / logique métier
 */
@Composable
fun PlayersScreen(
    onBack: () -> Unit
) {
    // 1. Initialisation du ViewModel
    val viewModel = remember { PlayersViewModel() }

    // 2. Récupération des états
    val isLoading by remember { derivedStateOf { viewModel.isLoading } }
    val selectedPlayerId by viewModel.selectedPlayerId

    // 3. Nettoyage à la sortie
    DisposableEffect(Unit) {
        onDispose {
            viewModel.onCleared()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // Bouton retour + Titre
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour",
                    tint = MaterialTheme.colors.primary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Joueurs",
                style = MaterialTheme.typography.h4,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Tableau principal des joueurs
            PlayerTable(
                modifier = Modifier.fillMaxWidth(),
                players = viewModel.players,
                onPlayerSelected = { playerId: String? ->
                    viewModel.selectPlayer(playerId)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Zone de détail du profil joueur
            PlayerDetailPanel(
                modifier = Modifier.fillMaxWidth(),
                selectedPlayerId = selectedPlayerId
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Zone de statistiques générales
            PlayerStatistics(
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

