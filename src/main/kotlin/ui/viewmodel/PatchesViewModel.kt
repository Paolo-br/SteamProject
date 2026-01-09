package org.example.ui.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.example.model.Patch
import org.example.services.ServiceLocator

/**
 * ViewModel pour l'écran Patchs.
 *
 * Responsabilités :
 * - Charger l'historique des patchs
 * - Écouter les nouveaux patchs en temps réel (Kafka)
 * - Gérer la sélection d'un patch
 * - Mettre à jour automatiquement l'UI
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
        observePatchEvents()
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
     * Écoute les nouveaux patchs en temps réel (Kafka).
     */
    private fun observePatchEvents() {
        viewModelScope.launch {
            val kafkaService = ServiceLocator.kafkaService

            kafkaService.patchEvents.collectLatest { event ->
                println("🆕 Nouveau patch reçu: ${event.gameName} ${event.newVersion}")
                // Recharger les patchs pour afficher le nouveau
                loadPatches()
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

