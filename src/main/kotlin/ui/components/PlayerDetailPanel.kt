package org.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.material.Card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.model.Player
import org.example.services.ServiceLocator

/**
 * Panneau de détail du profil joueur.
 * Charge le `Player` via `ServiceLocator` et affiche les informations basiques.
 */
@Composable
fun PlayerDetailPanel(
    modifier: Modifier = Modifier,
    selectedPlayerId: String? = null
) {
    Column(
        modifier = modifier
            .border(1.dp, Color.LightGray)
            .background(Color.White)
            .padding(24.dp)
    ) {
        Text(
            text = "Profil joueur",
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Divider(color = Color.LightGray, thickness = 1.dp)

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedPlayerId == null) {
            PlayerDetailPlaceholder()
        } else {
            var playerState by remember { mutableStateOf<Player?>(null) }
            var libraryState by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
            var sessionsState by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }

            // Poll player info once and poll library periodically
            LaunchedEffect(selectedPlayerId) {
                if (selectedPlayerId == null) {
                    playerState = null
                    libraryState = emptyList()
                } else {
                    // initial load of player
                    playerState = try {
                        ServiceLocator.dataService.getPlayer(selectedPlayerId!!)
                    } catch (_: Exception) { null }

                    // periodic polling of player's library via REST endpoint (every 2s)
                            while (true) {
                        try {
                            val url = java.net.URL("http://localhost:8080/api/players/${selectedPlayerId}/library")
                            val txt = withContext(Dispatchers.IO) { url.readText() }
                            val mapper = ObjectMapper()
                            val node = mapper.readTree(txt)
                            if (node != null && node.isArray) {
                                val list = mutableListOf<Map<String, Any?>>()
                                for (n in node) {
                                    val m = mutableMapOf<String, Any?>()
                                    m["gameId"] = n.get("gameId")?.asText()
                                    m["gameName"] = n.get("gameName")?.asText()
                                    m["purchaseDate"] = n.get("purchaseDate")?.asText()
                                    m["playtime"] = n.get("playtime")?.asInt(0)
                                    val platId = n.get("platform")?.asText()
                                    val platName = platId?.let { org.example.model.DistributionPlatform.fromId(it)?.name ?: it }
                                    m["platform"] = platName
                                    list.add(m)
                                }
                                libraryState = list
                            } else {
                                libraryState = emptyList()
                            }
                        } catch (_: Exception) {
                            // ignore network errors and keep previous state
                        }
                        // also fetch recent sessions
                        try {
                            val sUrl = java.net.URL("http://localhost:8080/api/players/${selectedPlayerId}/sessions")
                            val sTxt = withContext(Dispatchers.IO) { sUrl.readText() }
                            val sNode = ObjectMapper().readTree(sTxt)
                            if (sNode != null && sNode.isArray) {
                                val sl = mutableListOf<Map<String, Any?>>()
                                for (n in sNode) {
                                    val m = mutableMapOf<String, Any?>()
                                    m["sessionId"] = n.get("sessionId")?.asText()
                                    m["gameId"] = n.get("gameId")?.asText()
                                    m["gameName"] = n.get("gameName")?.asText()
                                    m["duration"] = n.get("duration")?.asInt(0)
                                    m["timestamp"] = n.get("timestamp")?.asLong(0)
                                    sl.add(m)
                                }
                                // update sessions state so UI can render recent sessions
                                sessionsState = sl
                            }
                        } catch (_: Exception) {}
                        kotlinx.coroutines.delay(2000)
                    }
                }
            }

            
            if (playerState == null) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text(text = "Chargement...", color = Color.Gray)
                }
            } else {
                // display player details and sessions
                PlayerDetailContent(playerState!!, libraryState, sessionsState)
            }
        }
    }
}

@Composable
private fun PlayerDetailContent(player: Player, libraryState: List<Map<String, Any?>>, sessionsState: List<Map<String, Any?>>) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFEEEEEE)), contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Color.Gray)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = player.username, style = MaterialTheme.typography.h6)
                Text(text = player.email ?: "-", style = MaterialTheme.typography.body2, color = Color.Gray)
            }
        }

        // Informations personnelles (RGPD)
        DetailRow("Prénom:", player.firstName ?: "-")
        DetailRow("Nom:", player.lastName ?: "-")
        DetailRow("Date naissance:", player.dateOfBirth ?: "-")
        DetailRow("Consentement RGPD:", if (player.gdprConsent) "Oui" else "Non")
        DetailRow("Date consentement:", player.gdprConsentDate ?: "-")

        // Statistiques & historique
        DetailRow("Inscription:", player.registrationDate ?: "-")
        DetailRow("Jeux possédés:", libraryState.size.toString())
        DetailRow("Temps de jeu (total):", player.totalPlaytime?.toString()?.plus(" h") ?: "-")
        DetailRow("Dernière évaluation:", player.lastEvaluationDate ?: "-")
        DetailRow("Nombre évaluations:", player.evaluationsCount?.toString() ?: "-")

        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Bibliothèque", style = MaterialTheme.typography.subtitle2, color = Color.DarkGray)
        Spacer(modifier = Modifier.height(4.dp))

        // Table: Jeu | Plateforme | Temps de jeu
        Card(modifier = Modifier.fillMaxWidth(), elevation = 1.dp) {
            Column {
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5))
                    .padding(12.dp)) {
                    Text(text = "Jeu", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold)
                    Text(text = "Plateforme", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(text = "Temps de jeu", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                }

                if (libraryState.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(text = "Aucun jeu dans la bibliothèque", color = Color.Gray)
                    }
                } else {
                    Column {
                        libraryState.forEach { entry ->
                            Row(modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(text = (entry["gameName"] as? String) ?: "-", modifier = Modifier.weight(2f))
                                Text(text = (entry["platform"] as? String) ?: "-", modifier = Modifier.weight(1f))
                                val play = when (val p = entry["playtime"]) {
                                    is Int -> p
                                    is Number -> p.toInt()
                                    else -> 0
                                }
                                Text(text = if (play > 0) "${play} h" else "-", modifier = Modifier.weight(1f))
                            }
                            Divider()
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text(text = "Sessions récentes", style = MaterialTheme.typography.subtitle2, color = Color.DarkGray)
    Spacer(modifier = Modifier.height(4.dp))

    Card(modifier = Modifier.fillMaxWidth(), elevation = 1.dp) {
        Column {
            Row(modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F5))
                .padding(12.dp)) {
                Text(text = "Jeu", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold)
                Text(text = "Durée", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(text = "Date", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            }

            if (sessionsState.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(text = "Aucune session enregistrée", color = Color.Gray)
                }
            } else {
                Column {
                    sessionsState.forEach { s ->
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(text = (s["gameName"] as? String) ?: "-", modifier = Modifier.weight(2f))
                            val dur = when (val d = s["duration"]) { is Int -> d; is Number -> d.toInt(); else -> 0 }
                            Text(text = if (dur > 0) "${dur} min" else "-", modifier = Modifier.weight(1f))
                            val ts = s["timestamp"] as? Long
                            val date = ts?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() } ?: "-"
                            Text(text = date, modifier = Modifier.weight(1f))
                        }
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerDetailPlaceholder() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Sélectionnez un joueur", style = MaterialTheme.typography.subtitle1, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, modifier = Modifier.width(140.dp), color = Color.Gray)
        Text(text = value)
    }
}

