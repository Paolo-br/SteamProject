package org.example.model

import kotlinx.serialization.Serializable

/**
 * Modèle immuable représentant un commentaire laissé par un joueur.
 *
 * Cette `data class` Kotlin :
 * - génère automatiquement `toString()`, `hashCode()`, `equals()` et les accesseurs
 * - utilise des `val` pour encourager l'immuabilité
 * - peut contenir une validation dans un bloc `init` si nécessaire
 *
 * Les instances servent de transporteurs de données (DTO) dans la pipeline
 * d'ingestion et d'affichage de l'application.
 */
@Serializable
data class Comment(
    val id: String,
    val gameId: String,
    val playerId: String? = null,
    val text: String,
    val severity: Severity = Severity.LOW,
    val routed: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class Severity { LOW, MEDIUM, HIGH }
