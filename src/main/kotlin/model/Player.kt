package org.example.model

/**
 * Représente un joueur inscrit sur une plateforme de distribution.
 * Contient les informations protégées par le RGPD.
 * 
 * Ceci reflète la réalité du marché où un joueur a un compte sur chaque plateforme
 * et possède des jeux distincts sur chacune.
 */
data class Player(
    val id: String,
    val username: String,               // Pseudo unique (RGPD)
    val email: String,
    val registrationDate: String,
    
    /**
     * Identifiant de la plateforme de distribution sur laquelle le joueur est inscrit.
     */
    val distributionPlatformId: String? = null,
    
    // === Champs RGPD ===
    val firstName: String? = null,      // Prénom (RGPD)
    val lastName: String? = null,       // Nom (RGPD)
    val dateOfBirth: String? = null,    // Date de naissance ISO 8601 (RGPD)
    val gdprConsent: Boolean = false,   // Consentement RGPD accepté
    val gdprConsentDate: String? = null,// Date du consentement RGPD
    // === Bibliothèque et statistiques ===
    val library: List<GameOwnership> = emptyList(),
    val totalPlaytime: Int? = null,     // en heures, rempli via Kafka
    val lastEvaluationDate: String? = null,
    val evaluationsCount: Int? = null
) {
    /**
     * Retourne la plateforme de distribution associée à ce joueur.
     * Par défaut Steam si non spécifiée.
     */
    fun getDistributionPlatform(): DistributionPlatform {
        return if (distributionPlatformId != null) {
            DistributionPlatform.fromId(distributionPlatformId) ?: DistributionPlatform.STEAM
        } else {
            DistributionPlatform.STEAM
        }
    }
}

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

