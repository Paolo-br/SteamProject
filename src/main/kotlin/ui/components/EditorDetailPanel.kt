package org.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.model.Game
import org.example.model.Publisher
import org.example.services.ServiceLocator

/**
 * Panneau de detail d'un editeur selectionne.
 * Charge le `Publisher` via `ServiceLocator` et affiche les métriques basiques.
 */
@Composable
fun EditorDetailPanel(
    modifier: Modifier = Modifier,
    selectedEditorId: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color.LightGray)
            .background(Color.White)
            .padding(24.dp)
    ) {
        Text(
            text = "Détails éditeur",
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (selectedEditorId == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .background(Color(0xFFFAFAFA)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sélectionnez un éditeur dans le tableau ci‑dessus",
                    style = MaterialTheme.typography.body1,
                    color = Color.Gray
                )
            }
        } else {
            val pubState by produceState<Publisher?>(initialValue = null, selectedEditorId) {
                value = try {
                    ServiceLocator.dataService.getPublisher(selectedEditorId!!)
                } catch (_: Exception) { null }
            }

            val gamesState by produceState(initialValue = emptyList<Game>(), pubState) {
                value = pubState?.let { pub ->
                    try {
                        // Récupère le catalogue et filtre par publisherName pour compatibilité
                        ServiceLocator.dataService.getCatalog().filter { it.publisherName == pub.name }
                    } catch (_: Exception) { emptyList() }
                } ?: emptyList()
            }

            if (pubState == null) {
                Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    Text(text = "Chargement...", color = Color.Gray)
                }
            } else {
                PublisherDetailContent(pub = pubState!!, games = gamesState)
            }
        }
    }
}

@Composable
private fun PublisherDetailContent(pub: Publisher, games: List<Game>) {
    Column(modifier = Modifier.fillMaxWidth().height(400.dp)) {
        DetailRow("Nom:", pub.name ?: "-")
        DetailRow("Année fondation:", pub.foundedYear?.toString() ?: "-")
        DetailRow("Jeux publiés:", pub.gamesPublished.toString())
        DetailRow("Jeux actifs:", pub.activeGames.toString())
        DetailRow("Incidents totaux:", pub.totalIncidents?.toString() ?: "-")
        DetailRow("Note moyenne:", pub.averageRating?.toString() ?: "-")
        DetailRow("Plateformes:", pub.platforms.joinToString(", "))
        DetailRow("Genres:", pub.genres.joinToString(", "))

        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Exemples de jeux (${games.size}) :", style = MaterialTheme.typography.subtitle2, color = Color.DarkGray)
        Spacer(modifier = Modifier.height(8.dp))
        Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            games.take(6).forEach { g ->
                Text(text = "• ${g.name} (${g.releaseYear ?: "?"})", color = Color.Black)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, modifier = Modifier.width(150.dp), color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.body1)
    }
}

