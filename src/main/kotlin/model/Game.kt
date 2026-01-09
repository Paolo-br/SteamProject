package org.example.model

/**
 * Modèle de données pour un jeu.
 * Sera rempli par les données du backend.
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
 */
data class Rating(
    val username: String,
    val rating: Int, // 1-5
    val comment: String,
    val date: String
)

