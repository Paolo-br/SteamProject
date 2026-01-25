package org.example.ui.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import org.example.model.Publisher
import org.example.services.ServiceLocator

/**
 * ViewModel pour l'écran Éditeurs.
 *
 * Responsabilités :
 * - Charger la liste des éditeurs depuis les projections REST (ProjectionDataService)
 * - Exposer l'état de chargement (isLoading)
 * - Gérer la sélection d'un éditeur
 * - Fournir les données aux composants UI
 *
 * Architecture découplée : change le service sans toucher l'UI.
 */
class EditorsViewModel : BaseViewModel() {

    // État privé mutable
    private val _uiState: MutableState<UiState<List<Publisher>>> = mutableStateOf(UiState.Loading)

    // État public en lecture seule
    val uiState: State<UiState<List<Publisher>>> = _uiState

    // Éditeur sélectionné
    private val _selectedEditorId: MutableState<String?> = mutableStateOf(null)
    val selectedEditorId: State<String?> = _selectedEditorId

    // Propriétés dérivées pour faciliter l'accès depuis l'UI
    val isLoading: Boolean
        get() = _uiState.value.isLoading

    val publishers: List<Publisher>
        get() = _uiState.value.data ?: emptyList()

    val errorMessage: String?
        get() = _uiState.value.errorMessage

    init {
        loadPublishers()
    }

    /**
     * Charge les éditeurs depuis le service.
     */
    private fun loadPublishers() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            try {
                val dataService = ServiceLocator.dataService
                val publishers = dataService.getPublishers()
                _uiState.value = UiState.Success(publishers)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Erreur de chargement: ${e.message}")
            }
        }
    }

    /**
     * Sélectionne un éditeur.
     */
    fun selectEditor(editorId: String?) {
        _selectedEditorId.value = editorId
    }

    /**
     * Recharge les données.
     */
    fun refresh() {
        loadPublishers()
    }
}

