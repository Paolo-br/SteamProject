package org.example.model

/**
 * Modèle de données pour un jeu.
 */
data class Game(
    val id: String,
    val name: String,
    
    /**
     * Support matériel sur lequel le jeu s'exécute (PC, PS4, Wii, etc.)
     */
    val hardwareSupport: String? = null,
    
    /**
     * Identifiant de la plateforme de distribution (steam, epic, psn, xbox, nintendo).
     * Représente le magasin/service où le jeu est vendu.
     */
    val distributionPlatformId: String? = null,
    
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
) {
    /**
     * Propriété de compatibilité : retourne le support matériel.
     * @deprecated Utiliser hardwareSupport à la place.
     */
    @Deprecated("Utiliser hardwareSupport pour le support matériel ou distributionPlatformId pour la plateforme de distribution", 
                ReplaceWith("hardwareSupport"))
    val platform: String? get() = hardwareSupport
    
    /**
     * Retourne la plateforme de distribution associée à ce jeu.
     * Si aucune n'est explicitement définie, l'infère à partir du support matériel.
     */
    fun getDistributionPlatform(): DistributionPlatform {
        return if (distributionPlatformId != null) {
            DistributionPlatform.fromId(distributionPlatformId) ?: DistributionPlatform.STEAM
        } else {
            // Inférence basée sur le support matériel pour compatibilité données existantes
            DistributionPlatform.inferFromHardwareCode(hardwareSupport)
        }
    }
}

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

