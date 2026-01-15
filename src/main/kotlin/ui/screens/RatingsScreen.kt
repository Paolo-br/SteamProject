package org.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import org.example.ui.viewmodel.RatingsViewModel
import org.example.ui.viewmodel.UiState

/**
 * Écran Évaluations
 *
 * Fonctionnalités :
 * - Affichage de toutes les évaluations
 * - Statistiques agrégées
 * - Formulaire pour poster une nouvelle évaluation
 * - Distribution des notes
 */
@Composable
fun RatingsScreen(
    onBack: () -> Unit
) {
    // 1. Initialisation du ViewModel
    val viewModel = remember { RatingsViewModel() }

    // 2. Récupération de l'état
    val uiState by viewModel.uiState
    val selectedGameId by viewModel.selectedGameId

    // 3. État local pour le formulaire
    var showAddRatingDialog by remember { mutableStateOf(false) }

    // 4. Nettoyage à la sortie
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
                text = "Évaluations",
                style = MaterialTheme.typography.h4,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = { showAddRatingDialog = true }) {
                Text("Ajouter une évaluation")
            }
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

                // Statistiques
                RatingStatisticsSection(data.statistics)

                Spacer(modifier = Modifier.height(24.dp))

                // Distribution des notes
                RatingDistributionSection(data.statistics.distribution)

                Spacer(modifier = Modifier.height(24.dp))

                // Liste des évaluations
                RatingsListSection(data.ratings)
            }
        }
    }

    // Dialogue pour ajouter une évaluation
    if (showAddRatingDialog) {
        AddRatingDialog(
            games = (uiState as? UiState.Success)?.data?.games ?: emptyList(),
            onDismiss = { showAddRatingDialog = false },
            onSubmit = { gameId, score, comment, username ->
                viewModel.submitRating(gameId, score, comment, username)
                showAddRatingDialog = false
            }
        )
    }
}

@Composable
private fun RatingStatisticsSection(statistics: org.example.ui.viewmodel.RatingStatistics) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        StatCard(
            title = "Total d'évaluations",
            value = statistics.totalRatings.toString(),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Note moyenne",
            value = String.format("%.1f / 5", statistics.averageRating),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Évaluations ce mois",
            value = statistics.ratingsThisMonth.toString(),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        elevation = 2.dp,
        backgroundColor = Color.White
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = Color.Gray
            )
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary
            )
        }
    }
}

@Composable
private fun RatingDistributionSection(distribution: Map<Int, Int>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        backgroundColor = Color.White
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Distribution des notes",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            val maxCount = (distribution.values.maxOrNull() ?: 1).coerceAtLeast(1)

            (5 downTo 1).forEach { stars ->
                val count = distribution[stars] ?: 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$stars ⭐", modifier = Modifier.width(60.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .background(Color(0xFFE0E0E0))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(count.toFloat() / maxCount.toFloat())
                                .background(MaterialTheme.colors.primary)
                        )
                    }

                    Text(
                        text = count.toString(),
                        modifier = Modifier.width(50.dp).padding(start = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun RatingsListSection(ratings: List<org.example.ui.viewmodel.RatingWithGame>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        backgroundColor = Color.White
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Évaluations récentes (${ratings.size})",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (ratings.isEmpty()) {
                Text(
                    text = "Aucune évaluation disponible",
                    color = Color.Gray,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                ratings.take(10).forEach { ratingWithGame ->
                    RatingItem(ratingWithGame)
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun RatingItem(ratingWithGame: org.example.ui.viewmodel.RatingWithGame) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = ratingWithGame.gameName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Par ${ratingWithGame.rating.username}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Row {
                repeat(ratingWithGame.rating.rating) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = ratingWithGame.rating.comment,
            fontSize = 13.sp,
            color = Color.DarkGray
        )
        Text(
            text = ratingWithGame.rating.date,
            fontSize = 11.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun AddRatingDialog(
    games: List<org.example.model.Game>,
    onDismiss: () -> Unit,
    onSubmit: (gameId: String, score: Int, comment: String, username: String) -> Unit
) {
    var selectedGameId by remember { mutableStateOf(games.firstOrNull()?.id ?: "") }
    var username by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(5) }
    var comment by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter une évaluation") },
        text = {
            Column {
                // Sélection du jeu
                Text("Jeu", fontSize = 12.sp, color = Color.Gray)
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            games.find { it.id == selectedGameId }?.name ?: "Sélectionner un jeu",
                            maxLines = 1
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        games.take(10).forEach { game ->
                            DropdownMenuItem(onClick = {
                                selectedGameId = game.id
                                expanded = false
                            }) {
                                Text(game.name)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Nom d'utilisateur
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Votre nom") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Note
                Text("Note : $rating / 5", fontSize = 14.sp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (1..5).forEach { star ->
                        IconButton(onClick = { rating = star }) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = "$star étoiles",
                                tint = if (star <= rating) Color(0xFFFFB300) else Color(0xFFE0E0E0)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Commentaire
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Commentaire") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedGameId.isNotEmpty() && username.isNotEmpty()) {
                        onSubmit(selectedGameId, rating, comment, username)
                    }
                },
                enabled = selectedGameId.isNotEmpty() && username.isNotEmpty()
            ) {
                Text("Soumettre")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

