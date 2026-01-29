package org.example.ui.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import org.example.model.Game
import org.example.model.Rating
import org.example.services.ServiceLocator

/**
 * ViewModel pour l'écran Évaluations.
 *
 * Responsabilités :
 * - Charger toutes les évaluations de tous les jeux
 * - Permettre de poster une nouvelle évaluation
 * - Gérer l'état de chargement et d'erreur
 * - Calculer les statistiques d'évaluations
 */
class RatingsViewModel : BaseViewModel() {

    private val _uiState: MutableState<UiState<RatingsData>> = mutableStateOf(UiState.Loading)
    val uiState: State<UiState<RatingsData>> = _uiState

    private val _selectedGameId: MutableState<String?> = mutableStateOf(null)
    val selectedGameId: State<String?> = _selectedGameId

    // Propriétés dérivées
    val isLoading: Boolean
        get() = _uiState.value.isLoading

    val ratingsData: RatingsData?
        get() = _uiState.value.data

    val errorMessage: String?
        get() = _uiState.value.errorMessage

    init {
        loadRatings()
    }

    /**
     * Charge toutes les évaluations de tous les jeux.
     */
    private fun loadRatings() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            try {
                val dataService = ServiceLocator.dataService
                val games = dataService.getCatalog()

                // Collecter toutes les évaluations
                val allRatings = mutableListOf<RatingWithGame>()
                games.forEach { game ->
                    game.ratings?.forEach { rating ->
                        allRatings.add(
                            RatingWithGame(
                                gameId = game.id,
                                gameName = game.name,
                                rating = rating
                            )
                        )
                    }
                }

                // Calculer les statistiques
                val stats = calculateStatistics(allRatings)

                _uiState.value = UiState.Success(
                    RatingsData(
                        ratings = allRatings,
                        games = games,
                        statistics = stats
                    )
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Erreur de chargement: ${e.message}")
            }
        }
    }

    /**
     * Calcule les statistiques à partir des évaluations.
     */
    private fun calculateStatistics(ratings: List<RatingWithGame>): RatingStatistics {
        if (ratings.isEmpty()) {
            return RatingStatistics(
                totalRatings = 0,
                averageRating = 0.0,
                distribution = mapOf(1 to 0, 2 to 0, 3 to 0, 4 to 0, 5 to 0),
                ratingsThisMonth = 0
            )
        }

        val total = ratings.size
        val average = ratings.map { it.rating.rating }.average()

        // Distribution par étoile
        val distribution = (1..5).associateWith { stars ->
            ratings.count { it.rating.rating == stars }
        }

        // Évaluations de ce mois : compter les évaluations dont la date
        // correspond au mois et à l'année courants.
        val now = java.time.ZonedDateTime.now()
        val thisMonthCount = ratings.count { rwg ->
            try {
                val inst = java.time.Instant.parse(rwg.rating.date)
                val z = inst.atZone(java.time.ZoneId.systemDefault())
                z.month == now.month && z.year == now.year
            } catch (_: Exception) {
                // Si la date n'est pas au format ISO, tenter un parse plus permissif
                try {
                    val fmt = java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
                    val z = java.time.ZonedDateTime.parse(rwg.rating.date, fmt)
                    z.month == now.month && z.year == now.year
                } catch (_: Exception) {
                    false
                }
            }
        }

        return RatingStatistics(
            totalRatings = total,
            averageRating = average,
            distribution = distribution,
            ratingsThisMonth = thisMonthCount
        )
    }

    /**
     * Soumet une nouvelle évaluation.
     */
    // Rating submission removed: ratings are created only via events.

    /**
     * Sélectionne un jeu pour afficher ses évaluations.
     */
    fun selectGame(gameId: String?) {
        _selectedGameId.value = gameId
    }

    /**
     * Rafraîchit les données.
     */
    fun refresh() {
        loadRatings()
    }
}

/**
 * Données agrégées pour l'écran Ratings.
 */
data class RatingsData(
    val ratings: List<RatingWithGame>,
    val games: List<Game>,
    val statistics: RatingStatistics
)

/**
 * Évaluation enrichie avec les informations du jeu.
 */
data class RatingWithGame(
    val gameId: String,
    val gameName: String,
    val rating: Rating
)

/**
 * Statistiques des évaluations.
 */
data class RatingStatistics(
    val totalRatings: Int,
    val averageRating: Double,
    val distribution: Map<Int, Int>, // Nombre d'évaluations par note (1-5)
    val ratingsThisMonth: Int
)

