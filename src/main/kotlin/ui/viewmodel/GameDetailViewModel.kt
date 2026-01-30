package org.example.ui.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import org.example.model.*
import org.example.services.ServiceLocator

/**
 * ViewModel pour l'écran de détails d'un jeu.
 *
 * Responsabilités :
 * - Charger toutes les informations d'un jeu spécifique
 * - Charger l'éditeur du jeu
 * - Charger l'historique des patchs
 * - Charger les incidents
 * - Charger les évaluations
 * - Exposer un état agrégé pour l'UI
 */
class GameDetailViewModel(private val gameId: String) : BaseViewModel() {

    private val _uiState: MutableState<UiState<GameDetailData>> = mutableStateOf(UiState.Loading)
    val uiState: State<UiState<GameDetailData>> = _uiState

    // Propriétés dérivées
    val isLoading: Boolean
        get() = _uiState.value.isLoading

    val gameDetail: GameDetailData?
        get() = _uiState.value.data

    val errorMessage: String?
        get() = _uiState.value.errorMessage

    init {
        loadGameDetails()
    }

    /**
     * Charge toutes les informations du jeu.
     */
    private fun loadGameDetails() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            try {
                val dataService = ServiceLocator.dataService

                // Charger le jeu
                var game = dataService.getGame(gameId)
                    ?: throw IllegalArgumentException("Jeu non trouvé")

                // Si la projection ne fournit pas une année valide (null ou 0), récupérer depuis le CSV (fallback)
                if (game.releaseYear == null || game.releaseYear == 0) {
                    try {
                        val fallback = org.example.services.api.JavaBackedDataService().getGame(gameId)
                        if (fallback != null && fallback.releaseYear != null) {
                            game = game.copy(releaseYear = fallback.releaseYear)
                        }
                    } catch (_: Exception) { /* best-effort */ }
                }

                // Charger l'éditeur
                val publisher = game.publisherId?.let {
                    dataService.getPublisher(it)
                }

                // Charger les patchs
                val patches = dataService.getPatchesByGame(gameId)

                // Charger le prix actuel
                val currentPrice = dataService.getPrice(gameId)

                // Construire les données complètes
                _uiState.value = UiState.Success(
                    GameDetailData(
                        game = game,
                        publisher = publisher,
                        patches = patches,
                        currentPrice = currentPrice,
                        versionHistory = game.versions ?: emptyList(),
                        incidents = game.incidents ?: emptyList(),
                        ratings = game.ratings ?: emptyList(),
                        incidentCount = game.incidentCount ?: 0,
                        averageRating = game.averageRating ?: 0.0
                    )
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Erreur de chargement: ${e.message}")
            }
        }
    }

    /**
     * Rafraîchit les données du jeu.
     */
    fun refresh() {
        loadGameDetails()
    }

    /**
     * Soumet une nouvelle évaluation pour ce jeu.
     */
    // Rating submission removed: ratings are created only via events.
}

/**
 * Données complètes pour l'écran de détails d'un jeu.
 */
data class GameDetailData(
    val game: Game,
    val publisher: Publisher?,
    val patches: List<Patch>,
    val currentPrice: Double?,
    val versionHistory: List<GameVersion>,
    val incidents: List<Incident>,
    val ratings: List<Rating>,
    val incidentCount: Int,
    val averageRating: Double
)

