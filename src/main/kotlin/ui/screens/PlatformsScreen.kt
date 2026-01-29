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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.model.DistributionPlatform
import org.example.model.Game
import org.example.services.api.PlatformStats
import org.example.services.ServiceLocator
import org.example.state.NavigationState
import org.example.ui.navigation.Screen
import kotlinx.serialization.json.*

/**
 * Écran de filtrage par plateforme de distribution.
 * 
 */
@Composable
fun PlatformsScreen(
    onBack: () -> Unit,
    navigationState: NavigationState
) {
    val dataService = ServiceLocator.dataService
    
    // État pour les plateformes de distribution (et non les supports matériels)
    var distributionPlatforms by remember { mutableStateOf<List<DistributionPlatform>>(emptyList()) }
    var platformStats by remember { mutableStateOf<Map<DistributionPlatform, PlatformStats>>(emptyMap()) }
    var selectedPlatform by remember { mutableStateOf<DistributionPlatform?>(null) }
    var games by remember { mutableStateOf<List<Game>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Chargement initial des plateformes de distribution
    LaunchedEffect(Unit) {
        isLoading = true
        distributionPlatforms = dataService.getDistributionPlatforms()
        platformStats = dataService.getDistributionPlatformStats()
        games = dataService.getCatalog()
        isLoading = false
    }

    // Filtrage par plateforme de distribution sélectionnée
    LaunchedEffect(selectedPlatform) {
        isLoading = true
        val result: List<Game> = try {
            // Capture local value to avoid smart-cast issues on delegated property
            val sp = selectedPlatform
            if (sp == null) {
                dataService.filterByDistributionPlatform(null)
            } else {
                dataService.filterByDistributionPlatform(sp.id)
            }
        } catch (_: Exception) {
            emptyList()
        }
        games = result
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
            Column {
                Text(
                    text = "Plateformes de Distribution",
                    style = MaterialTheme.typography.h4,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Steam, PS Store, Xbox Store...",
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Cartes des plateformes de distribution
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Sélectionner une plateforme de distribution",
                    style = MaterialTheme.typography.h6
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Bouton "Toutes"
                    item {
                        DistributionPlatformCard(
                            platform = null,
                            isSelected = selectedPlatform == null,
                            stats = null,
                            onClick = { selectedPlatform = null }
                        )
                    }
                    
                    // Cartes pour chaque plateforme de distribution
                    items(distributionPlatforms) { platform ->
                        DistributionPlatformCard(
                            platform = platform,
                            isSelected = selectedPlatform?.id == platform.id,
                            stats = platformStats[platform],
                            onClick = { selectedPlatform = platform }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Statistiques globales
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard(
                title = "Plateformes",
                value = distributionPlatforms.size.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Jeux affichés",
                value = games.size.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Plateforme active",
                value = selectedPlatform?.name ?: "Toutes",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Informations sur la plateforme sélectionnée
        selectedPlatform?.let { platform ->
            PlatformInfoCard(platform = platform, stats = platformStats[platform])
            Spacer(modifier = Modifier.height(16.dp))
        }

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
                    val title = selectedPlatform?.let { "Jeux sur ${it.name} (${games.size})" } ?: "Tous les jeux (${games.size})"
                    Text(
                        text = title,
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

/**
 * Carte représentant une plateforme de distribution.
 * Affiche le nom, l'icône et les statistiques de la plateforme.
 */
@Composable
private fun DistributionPlatformCard(
    platform: DistributionPlatform?,
    isSelected: Boolean,
    stats: PlatformStats?,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        getPlatformColor(platform?.id ?: "all")
    } else {
        Color.LightGray
    }
    val textColor = if (isSelected) Color.White else Color.Black

    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() },
        elevation = if (isSelected) 8.dp else 2.dp,
        backgroundColor = backgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = platform?.name ?: "Toutes",
                style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            if (stats != null) {
                Text(
                    text = "${stats.gameCount} jeux",
                    style = MaterialTheme.typography.caption,
                    color = textColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * Carte d'information détaillée sur une plateforme de distribution.
 */
@Composable
private fun PlatformInfoCard(
    platform: DistributionPlatform,
    stats: PlatformStats?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp,
        backgroundColor = getPlatformColor(platform.id).copy(alpha = 0.1f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = getPlatformIcon(platform.id),
                    contentDescription = platform.name,
                    tint = getPlatformColor(platform.id),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = platform.name,
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold
                    )
                    platform.description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.caption,
                            color = Color.Gray
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Divider()
            Spacer(modifier = Modifier.height(12.dp))
            
            // Supports matériels pris en charge
            Text(
                text = "Supports matériels pris en charge :",
                style = MaterialTheme.typography.caption,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(platform.supportedHardware) { hardware ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.LightGray
                    ) {
                        Text(
                            text = hardware,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.caption
                        )
                    }
                }
            }
            
            if (platform.offersFreeGames) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF4CAF50)
                ) {
                    Text(
                        text = "Propose des jeux gratuits",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.caption,
                        color = Color.White
                    )
                }
            }
        }
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

/**
 * Ligne représentant un jeu avec son support matériel.
 * 
*/
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
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = getHardwareSupportColor(game.hardwareSupport ?: "")
            ) {
                Text(
                    text = game.hardwareSupport ?: "?",
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
                Row {
                    val genreText = game.genre ?: "N/A"
                    Text(
                        text = "$genreText ",
                        style = MaterialTheme.typography.caption,
                        color = Color.Gray
                    )
                }
            }
            
            // Ventes globales
                Column(horizontalAlignment = Alignment.End) {
                val salesText = game.salesGlobal?.let { v ->
                    // if it's an integer-valued double, show without decimals
                    if (kotlin.math.abs(v - v.toLong()) < 0.000001) {
                        "%d".format(v.toLong())
                    } else {
                        String.format("%.2f", v)
                    }
                } ?: "-"
                Text(
                    text = salesText,
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
 * Retourne une couleur pour une plateforme de distribution.
 * Chaque grande plateforme a sa couleur de marque.
 */
private fun getPlatformColor(platformId: String?): Color {
    return when (platformId?.lowercase()) {
        "steam" -> Color(0xFF1B2838)         // Steam dark blue
        "psn" -> Color(0xFF003087)           // PlayStation blue
        "xbox" -> Color(0xFF107C10)          // Xbox green
        "nintendo" -> Color(0xFFE60012)      // Nintendo red
        "all" -> Color(0xFF6200EE)           // Material purple
        else -> Color.DarkGray
    }
}

/**
 * Retourne une icône pour une plateforme de distribution.
 * Utilise des icônes Material Icons disponibles.
 */
private fun getPlatformIcon(platformId: String?): ImageVector {
    return when (platformId?.lowercase()) {
        "steam" -> Icons.Default.List       // Steam comme plateforme principale
        "psn" -> Icons.Default.List         // PlayStation
        "xbox" -> Icons.Default.List        // Xbox
        "nintendo" -> Icons.Default.List    // Nintendo
        else -> Icons.Default.List
    }
}

/**
 * Retourne une couleur basée sur le support matériel (hardware).
 * 
*/
private fun getHardwareSupportColor(hardware: String): Color {
    return when (hardware.uppercase()) {
        // Sony hardware
        "PS4", "PS3", "PS2", "PS", "PS5", "PSP", "PSV" -> Color(0xFF003087)
        // Microsoft hardware
        "XONE", "X360", "XB", "XS" -> Color(0xFF107C10)
        // Nintendo hardware
        "WII", "WIIU", "DS", "3DS", "N64", "GBA", "GC", "NES", "SNES", "GB", "NS" -> Color(0xFFE60012)
        // PC
        "PC" -> Color(0xFF1B2838)
        // Autres
        else -> Color.DarkGray
    }
}
