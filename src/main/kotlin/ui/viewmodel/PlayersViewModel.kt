package org.example.ui.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import org.example.model.Player
import org.example.services.ServiceLocator

/**
 * ViewModel pour l'écran Joueurs.
 *
 * Responsabilités :
 * - Charger la liste des joueurs
 * - Gérer la sélection d'un joueur
 * - Exposer l'état de chargement
 */
class PlayersViewModel : BaseViewModel() {

    private val _uiState: MutableState<UiState<List<Player>>> = mutableStateOf(UiState.Loading)
    val uiState: State<UiState<List<Player>>> = _uiState

    private val _selectedPlayerId: MutableState<String?> = mutableStateOf(null)
    val selectedPlayerId: State<String?> = _selectedPlayerId

    // Propriétés dérivées
    val isLoading: Boolean
        get() = _uiState.value.isLoading

    val players: List<Player>
        get() = _uiState.value.data ?: emptyList()

    val errorMessage: String?
        get() = _uiState.value.errorMessage

    init {
        loadPlayers()
        // Periodic polling to refresh players list from backend (every 3s)
        viewModelScope.launch {
            while (true) {
                try {
                    loadPlayers()
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(3000)
            }
        }
    }

    /**
     * Charge les joueurs depuis le service.
     */
    private fun loadPlayers() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            try {
                val dataService = ServiceLocator.dataService
                val players = dataService.getPlayers()
                _uiState.value = UiState.Success(players)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Erreur de chargement: ${e.message}")
            }
        }
    }

    /**
     * Sélectionne un joueur.
     */
    fun selectPlayer(playerId: String?) {
        _selectedPlayerId.value = playerId
    }

    /**
     * Recharge les données.
     */
    fun refresh() {
        loadPlayers()
    }
}

