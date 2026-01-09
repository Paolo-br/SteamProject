package org.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.ui.viewmodel.IncidentsViewModel
import org.example.ui.viewmodel.UiState

/**
 * Écran Incidents & Crashs
 *
 * Fonctionnalités :
 * - Affichage des incidents par jeu
 * - Statistiques agrégées
 * - Mise à jour temps réel via Kafka
 * - Alertes critiques
 */
@Composable
fun IncidentsCrashsScreen(
    onBack: () -> Unit
) {
    // 1. Initialisation du ViewModel
    val viewModel = remember { IncidentsViewModel() }

    // 2. Récupération de l'état
    val uiState by viewModel.uiState
    val realtimeEvents by viewModel.realtimeEvents

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
                text = "Incidents & Crashs",
                style = MaterialTheme.typography.h4,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            if (realtimeEvents.isNotEmpty()) {
                Badge(
                    backgroundColor = MaterialTheme.colors.error,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "${realtimeEvents.size} nouveaux",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
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
                IncidentStatisticsSection(data.statistics)

                Spacer(modifier = Modifier.height(24.dp))

                // Alertes temps réel
                if (realtimeEvents.isNotEmpty()) {
                    RealtimeAlertsSection(realtimeEvents)
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Tableau des incidents
                IncidentsTableSection(data.gameIncidents)
            }
        }
    }
}

@Composable
private fun IncidentStatisticsSection(statistics: org.example.ui.viewmodel.IncidentStatistics) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        StatCard(
            title = "Total incidents",
            value = statistics.totalIncidents.toString(),
            color = MaterialTheme.colors.error,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Jeux affectés",
            value = statistics.gamesAffected.toString(),
            color = Color(0xFFFF9800),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Moyenne par jeu",
            value = String.format("%.1f", statistics.averageIncidentsPerGame),
            color = Color(0xFF2196F3),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Jeux critiques",
            value = statistics.criticalGames.toString(),
            color = Color(0xFFF44336),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
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
                color = color
            )
        }
    }
}

@Composable
private fun RealtimeAlertsSection(events: List<org.example.model.IncidentAggregatedEvent>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        backgroundColor = Color(0xFFFFF3E0)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Alertes",
                    tint = Color(0xFFFF9800)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "🔴 Alertes en temps réel (Kafka)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            events.take(5).forEach { event ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = event.gameName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "${event.platform} - ${event.incidentCount} incidents",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Text(
                        text = "Gravité: ${String.format("%.1f", event.averageSeverity)}",
                        fontSize = 12.sp,
                        color = Color(0xFFE65100)
                    )
                }
                Divider()
            }
        }
    }
}

@Composable
private fun IncidentsTableSection(gameIncidents: List<org.example.ui.viewmodel.GameIncidents>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        shape = RoundedCornerShape(8.dp),
        backgroundColor = Color.White
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // En-tête
            Text(
                text = "Incidents par jeu (${gameIncidents.size} jeux)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            Divider()

            // En-têtes du tableau
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8F9FA))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableHeaderCell("Jeu", Modifier.weight(2f))
                TableHeaderCell("Plateforme", Modifier.weight(1f))
                TableHeaderCell("Version", Modifier.weight(1f))
                TableHeaderCell("Incidents", Modifier.weight(1f))
                TableHeaderCell("Statut", Modifier.weight(1f))
            }

            Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)

            // Données
            if (gameIncidents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aucun incident enregistré",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            } else {
                Column {
                    gameIncidents.take(20).forEach { incident ->
                        IncidentRow(incident)
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun TableHeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF333333)
    )
}

@Composable
private fun IncidentRow(incident: org.example.ui.viewmodel.GameIncidents) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Nom du jeu
        Text(
            text = incident.gameName,
            modifier = Modifier.weight(2f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        // Plateforme
        Text(
            text = incident.platform,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            color = Color.Gray
        )

        // Version
        Text(
            text = incident.currentVersion,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            color = Color.Gray
        )

        // Nombre d'incidents
        Text(
            text = incident.totalIncidents.toString(),
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                incident.totalIncidents > 200 -> Color(0xFFF44336)
                incident.totalIncidents > 100 -> Color(0xFFFF9800)
                else -> Color(0xFF4CAF50)
            }
        )

        // Statut
        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    color = when {
                        incident.totalIncidents > 200 -> Color(0xFFFFEBEE)
                        incident.totalIncidents > 100 -> Color(0xFFFFF3E0)
                        else -> Color(0xFFE8F5E9)
                    },
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = when {
                    incident.totalIncidents > 200 -> "Critique"
                    incident.totalIncidents > 100 -> "Élevé"
                    else -> "Normal"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    incident.totalIncidents > 200 -> Color(0xFFC62828)
                    incident.totalIncidents > 100 -> Color(0xFFEF6C00)
                    else -> Color(0xFF2E7D32)
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

