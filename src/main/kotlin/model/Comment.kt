package org.example.model

import kotlinx.serialization.Serializable

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
