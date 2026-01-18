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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
            val playerState by produceState<Player?>(initialValue = null, selectedPlayerId) {
                value = try {
                    ServiceLocator.dataService.getPlayer(selectedPlayerId!!)
                } catch (_: Exception) { null }
            }

            if (playerState == null) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text(text = "Chargement...", color = Color.Gray)
                }
            } else {
                PlayerDetailContent(playerState!!)
            }
        }
    }
}

@Composable
private fun PlayerDetailContent(player: Player) {
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
        DetailRow("Jeux possédés:", (player.library.size).toString())
        DetailRow("Temps de jeu (total):", player.totalPlaytime?.toString()?.plus(" h") ?: "-")
        DetailRow("Dernière évaluation:", player.lastEvaluationDate ?: "-")
        DetailRow("Nombre évaluations:", player.evaluationsCount?.toString() ?: "-")

        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Historique des jeux (extrait)", style = MaterialTheme.typography.subtitle2, color = Color.DarkGray)
        Spacer(modifier = Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            player.library.take(4).forEach {
                val playDisplay = if (it.playtime > 0) "${it.playtime}h" else "-"
                Text(text = "• ${it.gameName} ($playDisplay)")
            }
            if (player.library.isEmpty()) Text(text = "Aucun jeu dans la bibliothèque", color = Color.Gray)
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
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            DetailRow("Inscription:", "-")
            DetailRow("Jeux:", "-")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, modifier = Modifier.width(140.dp), color = Color.Gray)
        Text(text = value)
    }
}

