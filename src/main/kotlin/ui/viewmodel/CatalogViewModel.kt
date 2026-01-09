package org.example.ui.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.example.model.Game
import org.example.services.ServiceLocator

/**
 * ViewModel pour l'écran Catalogue.
 *
 * Responsabilités :
 * - Charger le catalogue de jeux
 * - Gérer la recherche/filtrage
 * - Exposer l'état de chargement et d'erreur
 * - Écouter les changements de prix (Kafka)
 */
class CatalogViewModel : BaseViewModel() {

    private val _uiState: MutableState<UiState<List<Game>>> = mutableStateOf(UiState.Loading)
    val uiState: State<UiState<List<Game>>> = _uiState

    private val _searchQuery: MutableState<String> = mutableStateOf("")
    val searchQuery: State<String> = _searchQuery

    private val _selectedGameId: MutableState<String?> = mutableStateOf(null)
    val selectedGameId: State<String?> = _selectedGameId

    // Propriétés dérivées
    val isLoading: Boolean
        get() = _uiState.value.isLoading

    val games: List<Game>
        get() = _uiState.value.data ?: emptyList()

    val errorMessage: String?
        get() = _uiState.value.errorMessage

    /**
     * Jeux filtrés selon la recherche.
     */
    val filteredGames: List<Game>
        get() {
            val query = _searchQuery.value
            if (query.isBlank()) return games

            return games.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.genre?.contains(query, ignoreCase = true) == true ||
                it.publisherName?.contains(query, ignoreCase = true) == true
            }
        }

    init {
        loadCatalog()
        observePriceUpdates()
    }

    /**
     * Charge le catalogue de jeux.
     */
    private fun loadCatalog() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            try {
                val dataService = ServiceLocator.dataService
                val catalog = dataService.getCatalog()
                _uiState.value = UiState.Success(catalog)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Erreur de chargement: ${e.message}")
            }
        }
    }

    /**
     * Écoute les changements de prix en temps réel (Kafka).
     */
    private fun observePriceUpdates() {
        viewModelScope.launch {
            val kafkaService = ServiceLocator.kafkaService

            kafkaService.priceEvents.collectLatest { event ->
                println("Prix mis à jour: ${event.gameName} ${event.oldPrice}€ → ${event.newPrice}€")
                // Recharger le catalogue pour avoir les nouveaux prix
                loadCatalog()
            }
        }
    }

    /**
     * Met à jour la requête de recherche.
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Sélectionne un jeu pour afficher ses détails.
     */
    fun selectGame(gameId: String?) {
        _selectedGameId.value = gameId
    }

    /**
     * Récupère le jeu sélectionné.
     */
    val selectedGame: Game?
        get() = _selectedGameId.value?.let { id ->
            games.find { it.id == id }
        }

    /**
     * Recharge le catalogue.
     */
    fun refresh() {
        loadCatalog()
    }
}

