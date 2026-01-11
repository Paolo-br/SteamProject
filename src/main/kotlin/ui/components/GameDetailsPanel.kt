package org.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.model.Game
import org.example.model.GameVersion
import org.example.model.Incident
import org.example.model.Rating

/**
 * Composant pour afficher les détails d'un jeu avec des onglets.
 * Structure basée sur les maquettes fournies.
 */
@Composable
fun GameDetailsPanel(
    game: Game,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        "Informations générales",
        "Historique des versions",
        "Timeline des incidents",
        "Évaluations"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = 4.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Titre du jeu
            Text(
                text = game.name,
                style = MaterialTheme.typography.h4,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Onglets
            TabRow(
                selectedTabIndex = selectedTab,
                backgroundColor = Color(0xFFEEEEEE),
                contentColor = Color.Black,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Contenu des onglets
            when (selectedTab) {
                0 -> GeneralInfoTab(game)
                1 -> VersionHistoryTab(game.versions)
                2 -> IncidentsTimelineTab(game.incidents)
                3 -> RatingsTab(game.ratings)
            }
        }
    }
}

/**
 * Onglet des informations générales.
 */
@Composable
private fun GeneralInfoTab(game: Game) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
            // Colonne gauche
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                InfoItem(label = "Plateforme", value = game.platform ?: "N/A")
                InfoItem(label = "Année", value = game.releaseYear?.toString() ?: "N/A")
                InfoItem(label = "Nombre d'incidents", value = game.incidentCount?.toString() ?: "N/A")

                // Note moyenne (déplacée sous le nombre d'incidents)
                Column {
                    Text(
                        text = "Note moyenne",
                        style = MaterialTheme.typography.body2,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = game.averageRating?.let { String.format("%.1f / 5", it) } ?: "N/A",
                            style = MaterialTheme.typography.h6
                        )
                    }
                }
            }

        Spacer(modifier = Modifier.width(48.dp))

        // Colonne droite
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            InfoItem(label = "Genre", value = game.genre ?: "N/A")

            InfoItem(label = "Éditeur", value = game.publisherName ?: "N/A")

            // Ventes régionales
            Column {
                Text(
                    text = "Ventes (M)",
                    style = MaterialTheme.typography.body2,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoItem(label = "NA_Sales", value = String.format("%.2f", game.salesNA ?: 0.0))
                    InfoItem(label = "EU_Sales", value = String.format("%.2f", game.salesEU ?: 0.0))
                    InfoItem(label = "JP_Sales", value = String.format("%.2f", game.salesJP ?: 0.0))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoItem(label = "Other_Sales", value = String.format("%.2f", game.salesOther ?: 0.0))
                    InfoItem(label = "Global_Sales", value = String.format("%.2f", game.salesGlobal ?: 0.0))
                }
            }

            // (Note déplacée sous le nombre d'incidents)
        }
    }
}

/**
 * Composant pour afficher un item d'information.
 */
@Composable
private fun InfoItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.body2,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.h6
        )
    }
}

/**
 * Onglet de l'historique des versions.
 */
@Composable
private fun VersionHistoryTab(versions: List<GameVersion>?) {
    if (versions.isNullOrEmpty()) {
        Text(
            text = "Aucune version disponible",
            style = MaterialTheme.typography.body1,
            color = Color.Gray,
            modifier = Modifier.padding(16.dp)
        )
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            versions.forEach { version ->
                VersionItem(version)
            }
        }
    }
}

/**
 * Composant pour afficher un item de version.
 */
@Composable
private fun VersionItem(version: GameVersion) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Numéro de version
        Text(
            text = version.versionNumber,
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(4.dp))
                .padding(12.dp)
        )

        // Description et date
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = version.description,
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = version.releaseDate,
                style = MaterialTheme.typography.body2,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * Onglet de la timeline des incidents.
 */
@Composable
private fun IncidentsTimelineTab(incidents: List<Incident>?) {
    if (incidents.isNullOrEmpty()) {
        Text(
            text = "Aucun incident enregistré",
            style = MaterialTheme.typography.body1,
            color = Color.Gray,
            modifier = Modifier.padding(16.dp)
        )
    } else {
        // TODO: Implémenter un graphique avec une bibliothèque comme Compose Charts
        // Pour l'instant, affichage simple sous forme de liste
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Timeline des incidents (graphique à implémenter)",
                style = MaterialTheme.typography.body2,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            incidents.forEach { incident ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5), RoundedCornerShape(4.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = incident.date)
                    Text(text = "${incident.count} incidents", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Onglet des évaluations.
 */
@Composable
private fun RatingsTab(ratings: List<Rating>?) {
    if (ratings.isNullOrEmpty()) {
        Text(
            text = "Aucune évaluation disponible",
            style = MaterialTheme.typography.body1,
            color = Color.Gray,
            modifier = Modifier.padding(16.dp)
        )
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            ratings.forEach { rating ->
                RatingItem(rating)
            }
        }
    }
}

/**
 * Composant pour afficher une évaluation.
 */
@Composable
private fun RatingItem(rating: Rating) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // En-tête avec nom d'utilisateur et étoiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = rating.username,
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold
            )

            // Étoiles
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(5) { index ->
                    Icon(
                        imageVector = if (index < rating.rating) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Commentaire
        Text(
            text = rating.comment,
            style = MaterialTheme.typography.body1
        )

        // Date
        Text(
            text = rating.date,
            style = MaterialTheme.typography.body2,
            color = Color.Gray
        )

        Divider(modifier = Modifier.padding(top = 8.dp))
    }
}

