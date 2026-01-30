package org.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.ThumbUp
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
 * - Système de votes d'utilité (utile / pas utile)
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
    val votingInProgress by viewModel.votingInProgress
    val currentPlayerId by viewModel.currentPlayerId

    // 3. Sélection du joueur courant (pour les tests, utilise le premier joueur disponible)
    LaunchedEffect(uiState) {
        if (currentPlayerId == null && uiState is UiState.Success) {
            // Récupère le premier joueur disponible depuis les évaluations
            val data = (uiState as UiState.Success).data
            val firstPlayerId = data.ratings.firstOrNull()?.rating?.playerId
            if (firstPlayerId != null) {
                viewModel.setCurrentPlayer(firstPlayerId)
            }
        }
    }

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
                RatingsListSection(
                    ratings = data.ratings,
                    currentPlayerId = currentPlayerId,
                    votingInProgress = votingInProgress,
                    onVote = { reviewId, isHelpful -> viewModel.voteOnReview(reviewId, isHelpful) },
                    onRemoveVote = { reviewId -> viewModel.removeVote(reviewId) }
                )
            }
        }
    }

    // Ratings are created only via events; no UI to create them here.
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
private fun RatingsListSection(
    ratings: List<org.example.ui.viewmodel.RatingWithGame>,
    currentPlayerId: String?,
    votingInProgress: Set<String>,
    onVote: (String, Boolean) -> Unit,
    onRemoveVote: (String) -> Unit
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
                    RatingItem(
                        ratingWithGame = ratingWithGame,
                        currentPlayerId = currentPlayerId,
                        isVoting = votingInProgress.contains(ratingWithGame.rating.id),
                        onVote = onVote,
                        onRemoveVote = onRemoveVote
                    )
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun RatingItem(
    ratingWithGame: org.example.ui.viewmodel.RatingWithGame,
    currentPlayerId: String?,
    isVoting: Boolean,
    onVote: (String, Boolean) -> Unit,
    onRemoveVote: (String) -> Unit
) {
    val rating = ratingWithGame.rating
    val reviewId = rating.id ?: ""
    
    // Vérifie si le joueur courant a déjà voté et quel vote
    val hasVoted = currentPlayerId != null && rating.hasVoted(currentPlayerId)
    // Note: on ne peut pas savoir le sens du vote depuis hasVoted(), 
    // mais l'UI va se rafraîchir après le vote
    
    // Le joueur ne peut pas voter sur sa propre évaluation
    val isOwnReview = currentPlayerId != null && rating.playerId == currentPlayerId
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
        
        // Section votes d'utilité
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Affichage des votes
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (rating.totalVotes > 0) {
                    val helpfulPercent = rating.helpfulnessScore.toInt()
                    Text(
                        text = "$helpfulPercent% utile",
                        fontSize = 11.sp,
                        color = if (helpfulPercent >= 50) Color(0xFF4CAF50) else Color(0xFFF44336),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "(${rating.totalVotes} vote${if (rating.totalVotes > 1) "s" else ""})",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                } else {
                    Text(
                        text = "Aucun vote",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
            
            // Boutons de vote (seulement si un joueur est connecté et ce n'est pas sa propre évaluation)
            if (currentPlayerId != null && !isOwnReview && reviewId.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isVoting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        // Bouton "Utile"
                        VoteButton(
                            icon = if (hasVoted) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            count = rating.helpfulVotes,
                            contentDescription = "Utile",
                            tint = Color(0xFF4CAF50),
                            onClick = { onVote(reviewId, true) }
                        )
                        
                        // Bouton "Pas utile"
                        VoteButton(
                            icon = Icons.Filled.Clear,
                            count = rating.notHelpfulVotes,
                            contentDescription = "Pas utile",
                            tint = Color(0xFFF44336),
                            onClick = { onVote(reviewId, false) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoteButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFFF5F5F5)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = count.toString(),
                fontSize = 12.sp,
                color = Color.DarkGray
            )
        }
    }
}



