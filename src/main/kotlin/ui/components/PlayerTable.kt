package org.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import org.example.model.Player
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Tableau principal affichant la liste des joueurs.
 * Structure vide prête pour l'intégration backend.
 * Affiche uniquement les en-têtes de colonnes.
 *
 * Colonnes : Pseudo, Date d'inscription, Jeux possédés, Temps de jeu total, Dernière évaluation
 */
@Composable
fun PlayerTable(
    modifier: Modifier = Modifier,
    players: List<Player> = emptyList(),
    onPlayerSelected: ((String?) -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp),
        elevation = 2.dp
    ) {
        Column {
            PlayerTableHeader()
            Divider()

            if (players.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFAFAFA)), contentAlignment = Alignment.Center) {
                    Text(text = "En attente des données joueurs...", style = MaterialTheme.typography.body2, color = Color.Gray)
                }
            } else {
                LazyColumn {
                    items(players) { player ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlayerSelected?.invoke(player.id) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = player.username, modifier = Modifier.weight(0.20f))
                            Text(text = player.registrationDate ?: "-", modifier = Modifier.weight(0.20f))
                            Text(text = (player.library.size).toString(), modifier = Modifier.weight(0.15f))
                            Text(text = player.totalPlaytime?.toString()?.plus("h") ?: "-", modifier = Modifier.weight(0.20f))
                            Text(text = player.lastEvaluationDate ?: "-", modifier = Modifier.weight(0.25f))
                        }
                        Divider(color = Color(0xFFEFEFEF))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TableHeaderCell("Pseudo", weight = 0.20f)
        TableHeaderCell("Date d'inscription", weight = 0.20f)
        TableHeaderCell("Jeux possédés", weight = 0.15f)
        TableHeaderCell("Temps de jeu total", weight = 0.20f)
        TableHeaderCell("Dernière évaluation", weight = 0.25f)
    }
}

@Composable
private fun RowScope.TableHeaderCell(
    text: String,
    weight: Float
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.subtitle2,
        fontWeight = FontWeight.Bold,
        color = Color.DarkGray
    )
}

