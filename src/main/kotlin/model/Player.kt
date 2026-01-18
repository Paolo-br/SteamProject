package org.example.model

/**
 * Représente un joueur sur la plateforme.
 * Contient les informations protégées par le RGPD.
 */
data class Player(
    val id: String,
    val username: String,               // Pseudo unique (RGPD)
    val email: String,
    val registrationDate: String,
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

