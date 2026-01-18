package org.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.model.Game
import org.example.services.ServiceLocator
import org.example.state.NavigationState
import org.example.ui.navigation.Screen

/**
 * Écran de filtrage par plateforme.
 * Affiche les plateformes disponibles et permet de filtrer les jeux.
 */
@Composable
fun PlatformsScreen(
    onBack: () -> Unit,
    navigationState: NavigationState
) {
    val dataService = ServiceLocator.dataService
    var platforms by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedPlatform by remember { mutableStateOf<String?>(null) }
    var games by remember { mutableStateOf<List<Game>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        platforms = dataService.getPlatforms()
        games = dataService.getCatalog()
        isLoading = false
    }

    LaunchedEffect(selectedPlatform) {
        isLoading = true
        games = dataService.filterByPlatform(selectedPlatform)
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // Bouton retour + Titre
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour",
                    tint = MaterialTheme.colors.primary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Jeux par Plateforme",
                style = MaterialTheme.typography.h4,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Filtres de plateformes
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Sélectionner une plateforme",
                    style = MaterialTheme.typography.h6
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Bouton "Toutes"
                    item {
                        PlatformChip(
                            platform = "Toutes",
                            isSelected = selectedPlatform == null,
                            onClick = { selectedPlatform = null }
                        )
                    }
                    
                    items(platforms) { platform ->
                        PlatformChip(
                            platform = platform,
                            isSelected = selectedPlatform == platform,
                            onClick = { selectedPlatform = platform }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard(
                title = "Plateformes",
                value = platforms.size.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Jeux affichés",
                value = games.size.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Filtre actif",
                value = selectedPlatform ?: "Aucun",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Liste des jeux
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (selectedPlatform != null) 
                            "Jeux sur $selectedPlatform (${games.size})" 
                        else 
                            "Tous les jeux (${games.size})",
                        style = MaterialTheme.typography.h6
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        games.take(100).forEach { game ->
                            GamePlatformRow(
                                game = game,
                                onClick = {
                                    navigationState.navigateTo(Screen.GameDetail(game.id))
                                }
                            )
                        }
                        
                        if (games.size > 100) {
                            Text(
                                text = "... et ${games.size - 100} autres jeux",
                                style = MaterialTheme.typography.caption,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformChip(
    platform: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colors.primary else Color.LightGray
    val textColor = if (isSelected) Color.White else Color.Black

    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor
    ) {
        Text(
            text = platform,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = textColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.caption
            )
        }
    }
}

@Composable
private fun GamePlatformRow(
    game: Game,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colors.surface,
        elevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Plateforme badge
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = getPlatformColor(game.platform ?: "")
            ) {
                Text(
                    text = game.platform ?: "?",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.caption,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Infos du jeu
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.name,
                    style = MaterialTheme.typography.body1,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${game.genre ?: "N/A"} - ${game.releaseYear ?: "?"} - ${game.publisherName ?: "?"}",
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
            }
            
            // Ventes globales
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format("%.2fM", game.salesGlobal ?: 0.0),
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ventes",
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
            }
        }
    }
}

/**
 * Retourne une couleur basée sur la plateforme.
 */
private fun getPlatformColor(platform: String): Color {
    return when (platform.uppercase()) {
        "PS4", "PS3", "PS2", "PS", "PSP", "PSV" -> Color(0xFF003087) // PlayStation blue
        "XONE", "X360", "XB" -> Color(0xFF107C10) // Xbox green
        "WII", "WIIU", "DS", "3DS", "N64", "GBA", "GC", "NES", "SNES", "GB" -> Color(0xFFE60012) // Nintendo red
        "PC" -> Color(0xFF1B2838) // Steam dark
        else -> Color.DarkGray
    }
}
