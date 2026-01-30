package org.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.ui.viewmodel.GameDetailViewModel
import org.example.model.DistributionPlatform
import org.example.ui.viewmodel.UiState

/**
 * Écran de détails d'un jeu
 *
 * Sections :
 * - Informations générales
 * - Éditeur
 * - Historique des versions et patchs
 * - Incidents
 * - Évaluations
 * - Prix actuel
 */
@Composable
fun GameDetailScreen(
    gameId: String,
    onBack: () -> Unit
) {
    // 1. Initialisation du ViewModel
    val viewModel = remember { GameDetailViewModel(gameId) }

    // 2. Récupération de l'état
    val uiState by viewModel.uiState

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
        // En-tête
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Retour",
                    tint = MaterialTheme.colors.primary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Détails du jeu",
                style = MaterialTheme.typography.h4,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Affichage selon l'état
        when (uiState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Text(
                    text = (uiState as UiState.Error).message,
                    color = MaterialTheme.colors.error
                )
            }
            is UiState.Success -> {
                val data = (uiState as UiState.Success).data

                // Section 1: Informations générales
                GameInfoSection(data)

                Spacer(modifier = Modifier.height(24.dp))

                // Section 2: Prix et note
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PriceCard(data.currentPrice, Modifier.weight(1f))
                    RatingCard(data.averageRating, data.ratings.size, Modifier.weight(1f))
                    IncidentCard(data.incidentCount, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Section 3: Historique des versions
                VersionHistorySection(data.versionHistory, data.patches)

                Spacer(modifier = Modifier.height(24.dp))

                // Section 4: Incidents
                IncidentsSection(data.incidents, data.game.name)

                Spacer(modifier = Modifier.height(24.dp))

                // Section 5: Évaluations
                RatingsSection(data.ratings)
            }
        }
    }
}

@Composable
private fun GameInfoSection(data: org.example.ui.viewmodel.GameDetailData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        backgroundColor = Color.White
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            // Titre du jeu
            Text(
                text = data.game.name,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Informations principales
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                InfoItem("Console", data.game.hardwareSupport ?: "N/A")
                val distName = data.game.distributionPlatformId?.let { DistributionPlatform.fromId(it)?.name ?: it } ?: "N/A"
                InfoItem("Distribution", distName)
                InfoItem("Genre", data.game.genre ?: "N/A")
                InfoItem("Année", data.game.releaseYear?.toString() ?: "N/A")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Éditeur
            if (data.publisher != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Éditeur",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = data.publisher.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Fondé en ${data.publisher.foundedYear} • ${data.publisher.gamesPublished} jeux publiés",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            // Description removed: descriptions are not available in dataset

            // Statistiques de ventes
            if (data.game.salesGlobal != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Statistiques de ventes",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Ventes par région: NA, EU, JP, Other (calculé), Global
                val na = data.game.salesNA ?: 0.0
                val eu = data.game.salesEU ?: 0.0
                val jp = data.game.salesJP ?: 0.0
                val global = data.game.salesGlobal ?: 0.0
                val other = maxOf(0.0, global - (na + eu + jp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SalesItem("NA", na, Modifier.weight(1f))
                    SalesItem("EU", eu, Modifier.weight(1f))
                    SalesItem("JP", jp, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SalesItem("Other", other, Modifier.weight(1f))
                    SalesItem("Global", global, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SalesItem(region: String, value: Double?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        backgroundColor = Color(0xFFF5F5F5)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = region,
                fontSize = 12.sp,
                color = Color.Gray
            )
            Text(
                text = "${String.format("%.2f", value ?: 0.0)}M",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary
            )
        }
    }
}

@Composable
private fun PriceCard(price: Double?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        elevation = 2.dp,
        backgroundColor = Color(0xFFE3F2FD)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Prix",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Text(
                text = if (price == 0.0) "Gratuit" else "${String.format("%.2f", price ?: 0.0)} €",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary
            )
        }
    }
}

@Composable
private fun RatingCard(rating: Double, count: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        elevation = 2.dp,
        backgroundColor = Color(0xFFFFF9C4)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Note moyenne",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Text(
                text = String.format("%.1f / 5", rating),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFB300)
            )
            Text(
                text = "$count évaluations",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun IncidentCard(count: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        elevation = 2.dp,
        backgroundColor = if (count > 100) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Incidents",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Text(
                text = count.toString(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (count > 100) Color(0xFFF44336) else Color(0xFF4CAF50)
            )
            Text(
                text = if (count > 100) "Critique" else "Normal",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun VersionHistorySection(
    versions: List<org.example.model.GameVersion>,
    patches: List<org.example.model.Patch>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        backgroundColor = Color.White
    ) {
                Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Historique des versions",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (versions.isEmpty() && patches.isEmpty()) {
                    Text(
                        text = "Aucune information sur les versions",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                } else {
                    Column(modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        // Afficher les patchs récents
                        if (patches.isNotEmpty()) {
                            Text(text = "Patches récents", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            patches.forEach { patch ->
                                PatchItem(patch)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        // Afficher les versions
                        if (versions.isNotEmpty()) {
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            versions.forEach { version ->
                                VersionItem(version)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun PatchItem(patch: org.example.model.Patch) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(
                    text = "${patch.oldVersion} → ${patch.newVersion}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = patch.type.name,
                    fontSize = 11.sp,
                    color = Color.White,
                    modifier = Modifier
                        .background(MaterialTheme.colors.primary, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Text(
                text = patch.description,
                fontSize = 12.sp,
                color = Color.Gray
            )
            Text(
                text = patch.releaseDate,
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun VersionItem(version: org.example.model.GameVersion) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = version.versionNumber,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Text(
                text = version.releaseDate,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        Text(
            text = version.description,
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun IncidentsSection(incidents: List<org.example.model.Incident>, gameName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        backgroundColor = Color.White
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Historique des incidents",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (incidents.isEmpty()) {
                Text(
                    text = "Aucun incident enregistré",
                    color = Color(0xFF4CAF50),
                    fontSize = 14.sp
                )
            } else {
                incidents.forEach { incident ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = incident.date,
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "${incident.count} incidents",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (incident.count > 50) Color(0xFFF44336) else Color(0xFFFF9800)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun RatingsSection(ratings: List<org.example.model.Rating>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        backgroundColor = Color.White
    ) {
        Column(
                modifier = Modifier.padding(16.dp)
            ) {
            Text(
                text = "Évaluations des joueurs",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

                if (ratings.isEmpty()) {
                    Text(
                        text = "Aucune évaluation disponible",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                } else {
                    // Show all ratings inside a scrollable area with limited height
                    Column(modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                        ratings.forEach { rating ->
                            RatingItem(rating)
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
        }
    }
}

@Composable
private fun RatingItem(rating: org.example.model.Rating) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = rating.username,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Row {
                repeat(rating.rating) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Text(
            text = rating.comment,
            fontSize = 13.sp,
            color = Color.DarkGray
        )
        Text(
            text = rating.date,
            fontSize = 11.sp,
            color = Color.Gray
        )
    }
}

