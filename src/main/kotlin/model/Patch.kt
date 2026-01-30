package org.example.model

import kotlinx.serialization.Serializable

/**
 * Représente un patch/mise à jour pour un jeu.
 */
@Serializable
data class Patch(
    val id: String,
    val gameId: String,
    val gameName: String,
    val platform: String,
    val oldVersion: String,
    val newVersion: String,
    val type: PatchType,
    val description: String,
    val changes: List<Change> = emptyList(),
    val sizeInMB: Int,
    val releaseDate: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Type de modification dans un patch.
 */
@Serializable
enum class PatchType {
    FIX,            // Correction de bugs
    ADD,            // Ajout de fonctionnalités
    OPTIMIZATION    // Optimisations
}

/**
 * Représente un changement spécifique dans un patch.
 */
@Serializable
data class Change(
    val type: PatchType,
    val description: String
)

