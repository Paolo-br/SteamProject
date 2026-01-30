package org.example.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Modèle représentant un éditeur de jeux vidéo.
 * 
 * Statistiques calculées par Kafka Streams (PublisherStatsStreams) :
 * - qualityScore : Score de qualité (0-100) basé sur les notes moyennes et le nombre d'incidents
 * - reactivity : Score de réactivité (0-100) basé sur le temps de réponse aux incidents
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Publisher(
    val id: String = "",
    val name: String = "",
    val foundedYear: Int? = null,
    val platforms: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val gamesPublished: Int = 0,
    val activeGames: Int = 0,
    val totalIncidents: Int? = null,
    val patchCount: Int? = null,
    val averageRating: Double? = null,
    val reactivity: Int? = null,
    val qualityScore: Double? = null
)



