package org.example.model

/**
 * Représente un joueur sur la plateforme.
 */
data class Player(
    val id: String,
    val username: String,
    val email: String,
    val registrationDate: String,
    val library: List<GameOwnership> = emptyList(),
    val totalPlaytime: Int? = null, // en heures, rempli via Kafka
    val lastEvaluationDate: String? = null,
    val evaluationsCount: Int? = null
)

/**
 * Représente la possession d'un jeu par un joueur.
 */
data class GameOwnership(
    val gameId: String,
    val gameName: String,
    val purchaseDate: String,
    val playtime: Int = 0, // en heures
    val lastPlayed: String? = null,
    val pricePaid: Double = 0.0
)

