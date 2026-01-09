package org.example.ui.navigation

/**
 * Définition des écrans de l'application.
 * Sealed class = navigation type-safe.
 */
sealed class Screen {
    data object Home : Screen()
    data object Catalog : Screen()
    data class GameDetail(val gameId: String) : Screen()
    data object Editors : Screen()
    data object Players : Screen()
    data object Patches : Screen()
    data object Ratings : Screen()
    data object IncidentsCrashs : Screen()
}