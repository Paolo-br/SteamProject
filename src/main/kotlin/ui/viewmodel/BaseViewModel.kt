package org.example.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * ViewModel de base pour Compose Desktop.
 *
 * Gère le cycle de vie et fournit :
 * - Un scope de coroutines pour les opérations asynchrones
 * - Un pattern d'état générique pour le chargement/erreur/données
 * - Nettoyage automatique des ressources
 *
 */
abstract class BaseViewModel {

    /**
     * Scope de coroutines attaché au ViewModel.
     * Annulé automatiquement lors du onCleared().
     */
    protected val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Nettoyage des ressources.
     * Appelé lorsque le ViewModel n'est plus utilisé.
     */
    open fun onCleared() {
        viewModelScope.cancel()
    }
}

/**
 * État UI générique pour les écrans de liste.
 * Encapsule les 3 états possibles : Loading, Success, Error.
 */
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

/**
 * Extensions pour simplifier les conversions d'état.
 */
val <T> UiState<T>.isLoading: Boolean
    get() = this is UiState.Loading

val <T> UiState<T>.data: T?
    get() = (this as? UiState.Success)?.data

val <T> UiState<T>.errorMessage: String?
    get() = (this as? UiState.Error)?.message

