package org.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import org.example.model.DLC
import org.example.model.Game
import org.example.services.ServiceLocator
import org.example.state.NavigationState
import org.example.ui.navigation.Screen

/**
 * Écran de gestion des DLC.
 * Affiche tous les DLC disponibles avec filtres par jeu.
 */
@Composable
fun DLCScreen(
    onBack: () -> Unit,
    navigationState: NavigationState
) {
    val dataService = ServiceLocator.dataService
    var allDLCs by remember { mutableStateOf<List<DLC>>(emptyList()) }
    var games by remember { mutableStateOf<List<Game>>(emptyList()) }
    var selectedGameId by remember { mutableStateOf<String?>(null) }
    var displayedDLCs by remember { mutableStateOf<List<DLC>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        isLoading = true
        allDLCs = dataService.getAllDLCs()
        games = dataService.getCatalog()
        displayedDLCs = allDLCs
        isLoading = false
    }

    LaunchedEffect(selectedGameId, searchQuery) {
        displayedDLCs = allDLCs.filter { dlc ->
            val matchesGame = selectedGameId == null || dlc.parentGameId == selectedGameId
            val matchesSearch = searchQuery.isBlank() || 
                dlc.name.contains(searchQuery, ignoreCase = true)
            matchesGame && matchesSearch
        }
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
                text = "Contenus Téléchargeables (DLC)",
                style = MaterialTheme.typography.h4,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DLCStatCard(
                title = "Total DLC",
                value = allDLCs.size.toString(),
                modifier = Modifier.weight(1f)
            )
            DLCStatCard(
                title = "DLC affichés",
                value = displayedDLCs.size.toString(),
                modifier = Modifier.weight(1f)
            )
            DLCStatCard(
                title = "Standalone",
                value = allDLCs.count { it.standalone }.toString(),
                modifier = Modifier.weight(1f)
            )
            DLCStatCard(
                title = "Prix moyen",
                value = String.format("%.2f€", allDLCs.map { it.price }.average().takeIf { !it.isNaN() } ?: 0.0),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Filtres
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Filtres",
                    style = MaterialTheme.typography.h6
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Recherche
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Rechercher un DLC") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    
                    // Sélection du jeu parent
                    var expanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = selectedGameId?.let { id ->
                                    games.find { it.id == id }?.name?.take(30) ?: "Jeu inconnu"
                                } ?: "Tous les jeux",
                                maxLines = 1
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(onClick = {
                                selectedGameId = null
                                expanded = false
                            }) {
                                Text("Tous les jeux")
                            }
                            Divider()
                            games.filter { game ->
                                allDLCs.any { it.parentGameId == game.id }
                            }.take(20).forEach { game ->
                                DropdownMenuItem(onClick = {
                                    selectedGameId = game.id
                                    expanded = false
                                }) {
                                    Text(game.name.take(40))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Liste des DLC
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (displayedDLCs.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aucun DLC trouvé",
                        style = MaterialTheme.typography.h6
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                displayedDLCs.forEach { dlc ->
                    DLCCard(
                        dlc = dlc,
                        parentGameName = games.find { it.id == dlc.parentGameId }?.name ?: "Jeu inconnu",
                        onGameClick = { 
                            navigationState.navigateTo(Screen.GameDetail(dlc.parentGameId))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DLCStatCard(
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
private fun DLCCard(
    dlc: DLC,
    parentGameName: String,
    onGameClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Badge type
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (dlc.standalone) Color(0xFF4CAF50) else MaterialTheme.colors.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (dlc.standalone) "SA" else "DLC",
                        style = MaterialTheme.typography.body2,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Infos DLC
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dlc.name,
                    style = MaterialTheme.typography.body1,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dlc.description,
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Text(
                        text = "Pour: ",
                        style = MaterialTheme.typography.caption
                    )
                    Text(
                        text = parentGameName,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.primary,
                        modifier = Modifier.clickable { onGameClick() }
                    )
                }
            }
            
            // Infos version et prix
            Column(horizontalAlignment = Alignment.End) {
                // Prix
                Text(
                    text = String.format("%.2f€", dlc.price),
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary
                )
                
                // Version requise
                if (!dlc.standalone && dlc.minGameVersion != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFFF3E0)
                    ) {
                        Text(
                            text = "v${dlc.minGameVersion}+",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.caption,
                            color = Color(0xFFE65100)
                        )
                    }
                }
                
                // Badge standalone
                if (dlc.standalone) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFE8F5E9)
                    ) {
                        Text(
                            text = "Standalone",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.caption,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
                
                // Taille
                Text(
                    text = "${dlc.sizeInMB} MB",
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
            }
        }
    }
}
