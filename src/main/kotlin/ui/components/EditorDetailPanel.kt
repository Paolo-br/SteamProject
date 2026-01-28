package org.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.serialization.json.*

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
                        // Call REST projection for publisher's published games
                        val url = java.net.URL("http://localhost:8080/api/publishers/${pub.id}/games")
                        val text = url.readText()
                        val json = kotlinx.serialization.json.Json.parseToJsonElement(text)
                        if (json is kotlinx.serialization.json.JsonArray) {
                                json.mapNotNull { elem ->
                                    try {
                                        val obj = elem.jsonObject
                                        val id = obj["gameId"]?.jsonPrimitive?.content ?: return@mapNotNull null
                                        val name = obj["gameName"]?.jsonPrimitive?.content ?: ""
                                        val year = obj["releaseYear"]?.jsonPrimitive?.intOrNull
                                        val avg = obj["averageRating"]?.jsonPrimitive?.doubleOrNull
                                        val ratings = obj["ratings"]?.jsonArray?.mapNotNull { r ->
                                            try {
                                                val ro = r.jsonObject
                                                val rid = ro["id"]?.jsonPrimitive?.content
                                                val username = ro["username"]?.jsonPrimitive?.content ?: ""
                                                val playerId = ro["playerId"]?.jsonPrimitive?.contentOrNull
                                                val rating = ro["rating"]?.jsonPrimitive?.intOrNull ?: 0
                                                val comment = ro["comment"]?.jsonPrimitive?.contentOrNull ?: ""
                                                val date = ro["date"]?.jsonPrimitive?.content ?: ""
                                                org.example.model.Rating(id = rid, username = username, playerId = playerId, rating = rating, comment = comment, date = date)
                                            } catch (_: Exception) { null }
                                        } ?: emptyList()
                                        Game(id = id, name = name, releaseYear = year, averageRating = avg, ratings = ratings)
                                    } catch (_: Exception) { null }
                                }
                        } else emptyList()
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
        DetailRow("Score qualité:", pub.averageRating?.toString() ?: "-")
        // Compute average rating across published games (if available)
        val publishedAvg = games.mapNotNull { it.averageRating }.let { list ->
            if (list.isEmpty()) null else list.average()
        }
        DetailRow("Note moyenne (jeux publiés):", publishedAvg?.let { String.format("%.2f", it) } ?: "-")
        DetailRow("Plateformes:", pub.platforms.joinToString(", "))
        DetailRow("Genres:", pub.genres.joinToString(", "))

        // Examples removed as requested
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, modifier = Modifier.width(150.dp), color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.body1)
    }
}

