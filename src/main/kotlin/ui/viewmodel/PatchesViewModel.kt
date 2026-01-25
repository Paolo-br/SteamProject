package org.example.ui.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import org.example.model.Patch
import org.example.services.ServiceLocator

/**
 * ViewModel pour l'écran Patchs.
 *
 * Version front-only :
 * - Charge l'historique des patchs via projection REST (ProjectionDataService)
 * - Ne dépend plus d'événements temps réel Kafka
 */
class PatchesViewModel : BaseViewModel() {

    private val _uiState: MutableState<UiState<List<Patch>>> = mutableStateOf(UiState.Loading)
    val uiState: State<UiState<List<Patch>>> = _uiState

    private val _selectedPatchId: MutableState<String?> = mutableStateOf(null)
    val selectedPatchId: State<String?> = _selectedPatchId

    // Propriétés dérivées
    val isLoading: Boolean
        get() = _uiState.value.isLoading

    val patches: List<Patch>
        get() = _uiState.value.data ?: emptyList()

    val errorMessage: String?
        get() = _uiState.value.errorMessage

    init {
        loadPatches()
    }

    /**
     * Charge l'historique des patchs.
     */
    private fun loadPatches() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            try {
                val dataService = ServiceLocator.dataService
                val patches = dataService.getAllPatches()
                _uiState.value = UiState.Success(patches)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Erreur de chargement: ${e.message}")
            }
        }
    }

    /**
     * Sélectionne un patch.
     */
    fun selectPatch(patchId: String?) {
        _selectedPatchId.value = patchId
    }

    /**
     * Recharge les patchs.
     */
    fun refresh() {
        loadPatches()
    }
}
