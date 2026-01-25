package org.example.ui.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import org.example.services.ServiceLocator

/**
 * ViewModel pour l'écran Incidents & Crashs.
 *
 * Version front-only :
 * - Charge les incidents via les projections REST (ProjectionDataService)
 * - Ne dépend plus d'événements Kafka temps réel
 */
class IncidentsViewModel : BaseViewModel() {

    private val _uiState: MutableState<UiState<IncidentsData>> = mutableStateOf(UiState.Loading)
    val uiState: State<UiState<IncidentsData>> = _uiState

    // Propriétés dérivées
    val isLoading: Boolean
        get() = _uiState.value.isLoading

    val incidentsData: IncidentsData?
        get() = _uiState.value.data

    val errorMessage: String?
        get() = _uiState.value.errorMessage

    init {
        loadIncidents()
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
                                platform = game.hardwareSupport ?: "PC",
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

