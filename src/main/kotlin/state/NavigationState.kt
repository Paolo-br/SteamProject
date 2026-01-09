package org.example.state

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import org.example.ui.navigation.Screen

/**
 * État global de la navigation.
 * Utilise Compose State pour la réactivité.
 */
class NavigationState {
    private val _currentScreen: MutableState<Screen> = mutableStateOf(Screen.Home)
    val currentScreen: Screen get() = _currentScreen.value

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun navigateBack() {
        _currentScreen.value = Screen.Home
    }
}

