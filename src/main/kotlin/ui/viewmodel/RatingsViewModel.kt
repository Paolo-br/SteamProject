package org.example.ui.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import org.example.model.Game
import org.example.model.Rating
import org.example.services.ServiceLocator
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * ViewModel pour l'écran Évaluations.
 *
 * Responsabilités :
 * - Charger toutes les évaluations de tous les jeux
 * - Permettre de voter sur l'utilité des évaluations
 * - Gérer l'état de chargement et d'erreur
 * - Calculer les statistiques d'évaluations
 */
class RatingsViewModel : BaseViewModel() {

    private val _uiState: MutableState<UiState<RatingsData>> = mutableStateOf(UiState.Loading)
    val uiState: State<UiState<RatingsData>> = _uiState

    private val _selectedGameId: MutableState<String?> = mutableStateOf(null)
    val selectedGameId: State<String?> = _selectedGameId
    
    // État de vote en cours (pour afficher le loading sur le bouton)
    private val _votingInProgress: MutableState<Set<String>> = mutableStateOf(emptySet())
    val votingInProgress: State<Set<String>> = _votingInProgress
    
    // ID du joueur courant (normalement provient de l'authentification)
    private val _currentPlayerId: MutableState<String?> = mutableStateOf(null)
    val currentPlayerId: State<String?> = _currentPlayerId
    
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()
    private val restBaseUrl = "http://localhost:8080"

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
     * Définit l'ID du joueur courant pour permettre les votes.
     */
    fun setCurrentPlayer(playerId: String?) {
        _currentPlayerId.value = playerId
    }

    /**
     * Vote "utile" ou "pas utile" sur une évaluation.
     * 
     * @param reviewId Identifiant de l'évaluation
     * @param isHelpful true pour "utile", false pour "pas utile"
     */
    fun voteOnReview(reviewId: String, isHelpful: Boolean) {
        val playerId = _currentPlayerId.value
        if (playerId.isNullOrEmpty()) {
            println("Cannot vote: no current player set")
            return
        }
        
        viewModelScope.launch {
            try {
                // Marque le vote comme en cours
                _votingInProgress.value = _votingInProgress.value + reviewId
                
                val url = "$restBaseUrl/api/reviews/$reviewId/vote?playerId=$playerId&isHelpful=$isHelpful"
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build()
                
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                
                if (response.statusCode() in 200..299) {
                    println("Vote submitted successfully: reviewId=$reviewId, isHelpful=$isHelpful")
                    // Rafraîchit les données après un court délai pour laisser Kafka Streams traiter
                    kotlinx.coroutines.delay(500)
                    loadRatings()
                } else {
                    println("Vote failed with status ${response.statusCode()}: ${response.body()}")
                }
            } catch (e: Exception) {
                println("Error voting on review: ${e.message}")
            } finally {
                _votingInProgress.value = _votingInProgress.value - reviewId
            }
        }
    }

    /**
     * Supprime le vote d'un joueur sur une évaluation.
     */
    fun removeVote(reviewId: String) {
        val playerId = _currentPlayerId.value
        if (playerId.isNullOrEmpty()) return
        
        viewModelScope.launch {
            try {
                _votingInProgress.value = _votingInProgress.value + reviewId
                
                val url = "$restBaseUrl/api/reviews/$reviewId/vote?playerId=$playerId"
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .DELETE()
                    .build()
                
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                
                if (response.statusCode() in 200..299) {
                    kotlinx.coroutines.delay(500)
                    loadRatings()
                }
            } catch (e: Exception) {
                println("Error removing vote: ${e.message}")
            } finally {
                _votingInProgress.value = _votingInProgress.value - reviewId
            }
        }
    }

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

