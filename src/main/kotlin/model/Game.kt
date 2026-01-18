package org.example.model

/**
 * Modèle de données pour un jeu.
 */
data class Game(
    val id: String,
    val name: String,
    val platform: String? = null,
    val genre: String? = null,
    val publisherId: String? = null,
    val publisherName: String? = null,
    val releaseYear: Int? = null,
    val currentVersion: String? = null,
    var price: Double? = null,
    val averageRating: Double? = null,
    val incidentCount: Int? = null,
    val salesNA: Double? = null,
    val salesEU: Double? = null,
    val salesJP: Double? = null,
    val salesOther: Double? = null,
    val salesGlobal: Double? = null,
    val description: String? = null,
    val versions: List<GameVersion>? = null,
    val incidents: List<Incident>? = null,
    val ratings: List<Rating>? = null
)

/**
 * Modèle pour l'historique des versions d'un jeu.
 */
data class GameVersion(
    val versionNumber: String,
    val description: String,
    val releaseDate: String
)

/**
 * Modèle pour un incident.
 */
data class Incident(
    val date: String,
    val count: Int
)

/**
 * Modèle pour une évaluation.
 * Inclut le système de votes d'utilité.
 */
data class Rating(
    val id: String? = null,          // Identifiant unique de l'évaluation
    val username: String,
    val playerId: String? = null,    // ID du joueur ayant évalué
    val rating: Int,                 // 1-5 étoiles
    val comment: String,
    val date: String,
    val playtime: Int = 0,           // Temps de jeu au moment de l'évaluation (heures)
    val isRecommended: Boolean = true, // Le joueur recommande-t-il le jeu ?
    // === Votes d'utilité ===
    val helpfulVotes: Int = 0,       // Nombre de votes "utile"
    val notHelpfulVotes: Int = 0,    // Nombre de votes "pas utile"
    val votedByPlayerIds: List<String> = emptyList() // IDs des joueurs ayant voté
) {
    /** Score d'utilité calculé (% de votes positifs) */
    val helpfulnessScore: Double
        get() = if (totalVotes > 0) (helpfulVotes.toDouble() / totalVotes) * 100 else 0.0
    
    /** Nombre total de votes */
    val totalVotes: Int
        get() = helpfulVotes + notHelpfulVotes
    
    /** Vérifie si un joueur a déjà voté sur cette évaluation */
    fun hasVoted(playerId: String): Boolean = votedByPlayerIds.contains(playerId)
}

