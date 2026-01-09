package org.example.model

/**
 * Modèle représentant un éditeur de jeux vidéo.
 */
data class Publisher(
    val id: String,
    val name: String,
    val foundedYear: Int? = null,
    val platforms: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val gamesPublished: Int = 0,
    val activeGames: Int = 0,
    val totalIncidents: Int = 0,
    val averageRating: Double? = null
)



