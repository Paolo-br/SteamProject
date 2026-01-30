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
import org.example.services.ServiceLocator
import org.example.model.Player
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Zone de statistiques générales des joueurs.
 * Affiche des cards vides avec emplacements pour KPIs.
 * Aucune donnée réelle - uniquement la structure UI.
 *
 * Contient : Joueurs actifs, Temps moyen/joueur, Jeux moyens/joueur, Évaluations/mois
 */
@Composable
fun PlayerStatistics(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color.LightGray)
            .background(Color.White)
            .padding(24.dp)
    ) {
        Text(
            text = "Statistiques des joueurs",
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Divider(color = Color.LightGray, thickness = 1.dp)

        Spacer(modifier = Modifier.height(24.dp))

        // Charger les joueurs via le DataService Java-backed (si présent)
        val playersState by produceState<List<Player>?>(initialValue = null) {
            value = try {
                ServiceLocator.dataService.getPlayers()
            } catch (_: Exception) { null }
        }

        val players = playersState ?: emptyList()

        val joueursActifs = players.size

        val tempsMoyen: String = players.mapNotNull { it.totalPlaytime }
            .takeIf { it.isNotEmpty() }
            ?.let { vals -> (vals.sum().toDouble() / vals.size).let { "${it.toInt()} h" } }
            ?: "-"

        val jeuxMoyens: String = if (players.isEmpty()) "-" else String.format("%.1f", players.map { it.library.size }.average())

        val evaluationsMoyennes: String = players.mapNotNull { it.evaluationsCount }
            .takeIf { it.isNotEmpty() }
            ?.let { vals -> String.format("%.1f", vals.average()) }
            ?: "-"

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatisticCard(title = "Joueurs actifs", value = joueursActifs.toString(), modifier = Modifier.weight(1f))
            StatisticCard(title = "Temps moyen/joueur", value = tempsMoyen, modifier = Modifier.weight(1f))
            StatisticCard(title = "Jeux moyens/joueur", value = jeuxMoyens, modifier = Modifier.weight(1f))
            StatisticCard(title = "Évaluations/mois", value = evaluationsMoyennes, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatisticCard(
    title: String,
    value: String = "-",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xFFFAFAFA))
            .border(1.dp, Color(0xFFE0E0E0))
            .padding(20.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Titre de la statistique
        Text(
            text = title,
            style = MaterialTheme.typography.subtitle2,
            color = Color.DarkGray,
            fontWeight = FontWeight.Medium
        )

        // Valeur principale
        Text(text = value, style = MaterialTheme.typography.h6)

        
    }
}

