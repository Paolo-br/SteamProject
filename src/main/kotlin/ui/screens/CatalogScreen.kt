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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.ui.components.SearchBar
import org.example.model.Game
import org.example.ui.viewmodel.CatalogViewModel

/**
 * Écran Catalogue avec tableau de jeux.
 *
 * Architecture ViewModel :
 * - CatalogViewModel gère l'état et le chargement
 * - Écoute les changements de prix en temps réel (Kafka)
 * - Recherche/filtrage réactif
 */
@Composable
fun CatalogScreen(
    onBack: () -> Unit,
    onNavigate: (org.example.ui.navigation.Screen) -> Unit
) {
    // 1. Initialisation du ViewModel
    val viewModel = remember { CatalogViewModel() }

    // 2. Récupération des états
    val searchQuery by viewModel.searchQuery
    val isLoading by remember { derivedStateOf { viewModel.isLoading } }
    val errorMessage by remember { derivedStateOf { viewModel.errorMessage } }
    val filteredGames by remember { derivedStateOf { viewModel.filteredGames } }
    val selectedGameId by viewModel.selectedGameId
    val selectedGame by remember { derivedStateOf { viewModel.selectedGame } }

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
            .background(Color.White)
            .padding(24.dp)
    ) {
        // Bouton retour + Titre
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
                text = "Jeux",
                style = MaterialTheme.typography.h4,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Barre de recherche
        SearchBar(
            query = searchQuery,
            onQueryChange = { viewModel.updateSearchQuery(it) },
            placeholder = "Rechercher un jeu..."
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Contrôles rapides : filtre année / genre
        var yearText by remember { mutableStateOf("") }
        var genreText by remember { mutableStateOf("") }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = yearText,
                onValueChange = { yearText = it },
                label = { Text("Année") },
                modifier = Modifier.width(120.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                val y = yearText.toIntOrNull()
                viewModel.filterByYear(y)
            }) { Text("Filtrer année") }

            Spacer(modifier = Modifier.width(12.dp))

            TextField(
                value = genreText,
                onValueChange = { genreText = it },
                label = { Text("Genre") },
                modifier = Modifier.width(160.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { viewModel.filterByGenre(genreText.ifBlank { null }) }) { Text("Filtrer genre") }

            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))
        

        // Indicateur de chargement ou erreur
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage ?: "Erreur inconnue",
                        color = Color.Red,
                        style = MaterialTheme.typography.body1
                    )
                }
            }
            else -> {
                // Tableau de jeux — au clic on navigue vers l'écran de détails
                GameTable(
                    games = filteredGames,
                    selectedGameId = selectedGameId,
                    onGameSelected = { gameId ->
                        if (gameId != null) {
                            onNavigate(org.example.ui.navigation.Screen.GameDetail(gameId))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun GameTable(
    games: List<Game>,
    selectedGameId: String?,
    onGameSelected: (String?) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
        elevation = 2.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            // En-tête du tableau
            TableHeader()

            Divider()

            // Contenu du tableau
            if (games.isEmpty()) {
                // Message TODO si pas de données
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "En attente des données du backend",
                            style = MaterialTheme.typography.body2,
                            color = Color(0xFF9E9E9E)
                        )
                    }
                }
            } else {
                // Lignes du tableau
                LazyColumn {
                    items(games) { game ->
                        TableRow(
                            game = game,
                            isSelected = game.id == selectedGameId,
                            onClick = {
                                onGameSelected(if (game.id == selectedGameId) null else game.id)
                            }
                        )
                        Divider(color = Color(0xFFE0E0E0))
                    }
                }
            }
        }
    }
}

@Composable
private fun TableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell("Nom du jeu", weight = 2f)
        HeaderCell("Plateforme", weight = 1.5f)
        HeaderCell("Genre", weight = 0.7f)
        HeaderCell("Version\nactuelle", weight = 1f)
        HeaderCell("Note\nmoyenne", weight = 1f)
        HeaderCell("Incidents", weight = 0.8f)
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.subtitle2,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = Color(0xFF616161)
    )
}

@Composable
private fun TableRow(
    game: Game,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) Color(0xFFE3F2FD) else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = game.name,
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.body1,
            fontWeight = FontWeight.Medium
        )

        Text(
            text = game.hardwareSupport ?: "N/A",
            modifier = Modifier.weight(1.5f),
            style = MaterialTheme.typography.body2,
            color = Color.Gray
        )

        GenreBadge(
            genre = game.genre ?: "Autre",
            modifier = Modifier.weight(0.7f)
        )

        Text(
            text = game.currentVersion ?: "N/A",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.body2,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⭐",
                style = MaterialTheme.typography.body2
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = game.averageRating?.toString() ?: "N/A",
                style = MaterialTheme.typography.body2
            )
        }

        Text(
            text = game.incidentCount?.toString() ?: "0",
            modifier = Modifier.weight(0.8f),
            style = MaterialTheme.typography.body2,
            color = if ((game.incidentCount ?: 0) > 20) Color.Red else Color.Black,
            fontWeight = if ((game.incidentCount ?: 0) > 20) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun GenreBadge(genre: String, modifier: Modifier = Modifier) {
    val backgroundColor = when (genre) {
        "RPG" -> Color(0xFFE3F2FD)
        "MMORPG" -> Color(0xFFE1F5FE)
        "Action" -> Color(0xFFE8F5E9)
        "Course" -> Color(0xFFFFF3E0)
        "Battle Royale" -> Color(0xFFE8EAF6)
        "Stratégie" -> Color(0xFFF3E5F5)
        else -> Color(0xFFF5F5F5)
    }

    val textColor = when (genre) {
        "RPG" -> Color(0xFF1976D2)
        "MMORPG" -> Color(0xFF0288D1)
        "Action" -> Color(0xFF388E3C)
        "Course" -> Color(0xFFF57C00)
        "Battle Royale" -> Color(0xFF3F51B5)
        "Stratégie" -> Color(0xFF7B1FA2)
        else -> Color(0xFF616161)
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = backgroundColor
        ) {
            Text(
                text = genre,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                style = MaterialTheme.typography.caption,
                color = textColor,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}


