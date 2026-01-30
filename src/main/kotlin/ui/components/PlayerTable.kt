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
 * Colonnes : Pseudo, Prénom, Nom, Email, Inscription, Naissance, Consentement, Jeux possédés, Dernière évaluation
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
                            Text(text = player.username, modifier = Modifier.weight(0.16f))
                            Text(text = player.firstName ?: "-", modifier = Modifier.weight(0.12f))
                            Text(text = player.lastName ?: "-", modifier = Modifier.weight(0.12f))
                            Text(text = player.email ?: "-", modifier = Modifier.weight(0.20f))
                            Text(text = player.registrationDate ?: "-", modifier = Modifier.weight(0.12f))
                            Text(text = player.dateOfBirth ?: "-", modifier = Modifier.weight(0.12f))
                            Text(text = if (player.gdprConsent) "Oui" else "Non", modifier = Modifier.weight(0.07f))
                            Text(text = (player.library.size).toString(), modifier = Modifier.weight(0.08f))
                            Text(text = (player.evaluationsCount?.toString() ?: "0"), modifier = Modifier.weight(0.06f))
                            Text(text = player.lastEvaluationDate ?: "-", modifier = Modifier.weight(0.11f))
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
        TableHeaderCell("Pseudo", weight = 0.16f)
        TableHeaderCell("Prénom", weight = 0.12f)
        TableHeaderCell("Nom", weight = 0.12f)
        TableHeaderCell("Email", weight = 0.20f)
        TableHeaderCell("Inscription", weight = 0.12f)
        TableHeaderCell("Naissance", weight = 0.12f)
        TableHeaderCell("Consent.", weight = 0.07f)
        TableHeaderCell("Jeux", weight = 0.08f)
        TableHeaderCell("Nbr évalu.", weight = 0.06f)
        TableHeaderCell("Dernière éval.", weight = 0.11f)
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

