package org.example.ui.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.example.model.Game
import org.example.model.IncidentAggregatedEvent
import org.example.services.ServiceLocator

/**
 * ViewModel pour l'écran Incidents & Crashs.
 *
 * Responsabilités :
 * - Charger les incidents de tous les jeux
 * - Écouter les événements Kafka en temps réel
 * - Mettre à jour automatiquement l'UI lors de nouveaux incidents
 * - Gérer les filtres et le tri
 */
class IncidentsViewModel : BaseViewModel() {

    private val _uiState: MutableState<UiState<IncidentsData>> = mutableStateOf(UiState.Loading)
    val uiState: State<UiState<IncidentsData>> = _uiState

    private val _realtimeEvents: MutableState<List<IncidentAggregatedEvent>> = mutableStateOf(emptyList())
    val realtimeEvents: State<List<IncidentAggregatedEvent>> = _realtimeEvents

    // Propriétés dérivées
    val isLoading: Boolean
        get() = _uiState.value.isLoading

    val incidentsData: IncidentsData?
        get() = _uiState.value.data

    val errorMessage: String?
        get() = _uiState.value.errorMessage

    init {
        loadIncidents()
        observeKafkaEvents()
    }

    /**
     * Charge les incidents depuis le service de données.
     */
    private fun loadIncidents() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            try {
                val dataService = ServiceLocator.dataService
                val games = dataService.getCatalog()

                // Collecter tous les incidents des jeux
                val incidentsByGame = mutableListOf<GameIncidents>()
                games.forEach { game ->
                    if ((game.incidentCount ?: 0) > 0) {
                        incidentsByGame.add(
                            GameIncidents(
                                gameId = game.id,
                                gameName = game.name,
                                platform = game.platform ?: "PC",
                                currentVersion = game.currentVersion ?: "1.0.0",
                                totalIncidents = game.incidentCount ?: 0,
                                incidentHistory = game.incidents ?: emptyList()
                            )
                        )
                    }
                }

                // Trier par nombre d'incidents décroissant
                val sortedIncidents = incidentsByGame.sortedByDescending { it.totalIncidents }

                // Calculer les statistiques
                val stats = calculateStatistics(sortedIncidents)

                _uiState.value = UiState.Success(
                    IncidentsData(
                        gameIncidents = sortedIncidents,
                        statistics = stats
                    )
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Erreur de chargement: ${e.message}")
            }
        }
    }

    /**
     * Écoute les événements Kafka en temps réel.
     */
    private fun observeKafkaEvents() {
        viewModelScope.launch {
            val kafkaService = ServiceLocator.kafkaService

            kafkaService.incidentEvents.collectLatest { event ->
                println(" Nouvel incident reçu: ${event.gameName} - ${event.incidentCount} incidents")

                // Ajouter l'événement à la liste des événements temps réel
                val currentEvents = _realtimeEvents.value.toMutableList()
                currentEvents.add(0, event)
                // Garder seulement les 20 derniers événements
                _realtimeEvents.value = currentEvents.take(20)

                // Mettre à jour les données complètes
                updateIncidentData(event)
            }
        }
    }

    /**
     * Met à jour les données d'incidents suite à un événement Kafka.
     */
    private fun updateIncidentData(event: IncidentAggregatedEvent) {
        val currentData = _uiState.value.data ?: return

        val updatedIncidents = currentData.gameIncidents.map { gameIncident ->
            if (gameIncident.gameId == event.gameId) {
                gameIncident.copy(
                    totalIncidents = gameIncident.totalIncidents + event.incidentCount
                )
            } else {
                gameIncident
            }
        }.sortedByDescending { it.totalIncidents }

        val updatedStats = calculateStatistics(updatedIncidents)

        _uiState.value = UiState.Success(
            IncidentsData(
                gameIncidents = updatedIncidents,
                statistics = updatedStats
            )
        )
    }

    /**
     * Calcule les statistiques des incidents.
     */
    private fun calculateStatistics(incidents: List<GameIncidents>): IncidentStatistics {
        if (incidents.isEmpty()) {
            return IncidentStatistics(
                totalIncidents = 0,
                gamesAffected = 0,
                averageIncidentsPerGame = 0.0,
                criticalGames = 0
            )
        }

        val total = incidents.sumOf { it.totalIncidents }
        val gamesAffected = incidents.size
        val average = total.toDouble() / gamesAffected

        // Jeux avec plus de 100 incidents = critiques
        val critical = incidents.count { it.totalIncidents > 100 }

        return IncidentStatistics(
            totalIncidents = total,
            gamesAffected = gamesAffected,
            averageIncidentsPerGame = average,
            criticalGames = critical
        )
    }

    /**
     * Rafraîchit les données.
     */
    fun refresh() {
        loadIncidents()
    }
}

/**
 * Données agrégées pour l'écran Incidents.
 */
data class IncidentsData(
    val gameIncidents: List<GameIncidents>,
    val statistics: IncidentStatistics
)

/**
 * Incidents d'un jeu spécifique.
 */
data class GameIncidents(
    val gameId: String,
    val gameName: String,
    val platform: String,
    val currentVersion: String,
    val totalIncidents: Int,
    val incidentHistory: List<org.example.model.Incident>
)

/**
 * Statistiques des incidents.
 */
data class IncidentStatistics(
    val totalIncidents: Int,
    val gamesAffected: Int,
    val averageIncidentsPerGame: Double,
    val criticalGames: Int
)

