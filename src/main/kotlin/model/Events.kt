package org.example.model

import kotlinx.serialization.Serializable

/**
 * Événement de publication d'un nouveau patch.
 */
@Serializable
data class PatchPublishedEvent(
    val gameId: String,
    val gameName: String,
    val platform: String,
    val oldVersion: String,
    val newVersion: String,
    val changeLog: String,
    val changes: List<Change> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Événement de mise à jour de prix.
 */
@Serializable
data class PriceUpdateEvent(
    val gameId: String,
    val gameName: String,
    val oldPrice: Double,
    val newPrice: Double,
    val reason: PriceChangeReason,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Raison du changement de prix.
 */
@Serializable
enum class PriceChangeReason {
    QUALITY_IMPROVEMENT,    // Amélioration de la qualité
    HIGH_DEMAND,            // Forte demande
    GOOD_REVIEWS,           // Bonnes évaluations
    PROMOTION,              // Promotion
    STANDARD_ADJUSTMENT     // Ajustement standard
}

/**
 * Événement d'incident agrégé (optionnel).
 */
@Serializable
data class IncidentAggregatedEvent(
    val gameId: String,
    val gameName: String,
    val platform: String,
    val incidentCount: Int,
    val averageSeverity: Double,
    val timestamp: Long = System.currentTimeMillis()
)


/**
 * Événement de nouvelle évaluation (rating) par un joueur.
 * Utile pour la page Ratings et l'activité temps réel.
 */
@Serializable
data class NewRatingEvent(
    val gameId: String,
    val gameName: String,
    val playerId: String,
    val playerUsername: String,
    val rating: Int, // 1-5 étoiles
    val comment: String? = null,
    val playtime: Int, // heures de jeu
    val isRecommended: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Événement d'achat d'un jeu par un joueur.
 * Permet de suivre les ventes en temps réel et mettre à jour les bibliothèques.
 */
@Serializable
data class GamePurchaseEvent(
    val purchaseId: String,
    val gameId: String,
    val gameName: String,
    val playerId: String,
    val playerUsername: String,
    val pricePaid: Double,
    val platform: String,
    val region: SalesRegion,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class SalesRegion {
    NA,     // North America
    EU,     // Europe
    JP,     // Japan
    OTHER   // Reste du monde
}

/**
 * Événement de session de jeu d'un joueur.
 * Permet de tracker l'activité et le temps de jeu.
 */
@Serializable
data class GameSessionEvent(
    val sessionId: String,
    val gameId: String,
    val gameName: String,
    val playerId: String,
    val playerUsername: String,
    val sessionDuration: Int, // en minutes
    val sessionType: SessionType,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class SessionType {
    FIRST_LAUNCH,       // Première session
    CASUAL,             // Session normale
    MARATHON,           // Session longue (>4h)
    POST_PATCH          // Session après un patch
}

/**
 * Événement de crash/incident détaillé.
 * Plus granulaire que IncidentAggregatedEvent.
 */
@Serializable
data class CrashReportEvent(
    val crashId: String,
    val gameId: String,
    val gameName: String,
    val playerId: String,
    val gameVersion: String,
    val platform: String,
    val severity: CrashSeverity,
    val errorType: String,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class CrashSeverity {
    MINOR,      // Petit bug
    MODERATE,   // Bug gênant
    CRITICAL,   // Crash fatal
    GAME_BREAKING // Jeu injouable
}

/**
 * Événement de pic de joueurs simultanés.
 * Utile pour détecter les moments d'affluence.
 */
@Serializable
data class PlayerPeakEvent(
    val gameId: String,
    val gameName: String,
    val currentPlayers: Int,
    val peakType: PeakType,
    val comparedToAverage: Double, // % de différence avec la moyenne
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class PeakType {
    NEW_RECORD,         // Nouveau record
    DAILY_PEAK,         // Pic journalier
    POST_PATCH_SURGE,   // Afflux après patch
    VIRAL_TREND         // Tendance virale
}

/**
 * Événement de nouveau jeu publié sur la plateforme.
 */
@Serializable
data class GameReleasedEvent(
    val gameId: String,
    val gameName: String,
    val publisherId: String,
    val publisherName: String,
    val platform: String,
    val genre: String,
    val initialPrice: Double,
    val releaseYear: Int,
    val initialVersion: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Événement de tendance/alerte de popularité.
 * Basé sur plusieurs métriques combinées.
 */
@Serializable
data class GameTrendingEvent(
    val gameId: String,
    val gameName: String,
    val trendType: TrendType,
    val metric: String, // "sales", "ratings", "players", etc.
    val changePercentage: Double,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class TrendType {
    RISING_STAR,        // En pleine ascension
    TOP_SELLER,         // Meilleures ventes
    HIGHLY_RATED,       // Très bien noté
    DECLINING,          // En baisse
    CONTROVERSIAL       // Avis mitigés
}

/**
 * Événement de dépassement de seuil de ventes.
 */
@Serializable
data class SalesMilestoneEvent(
    val gameId: String,
    val gameName: String,
    val milestone: Int, // Ex: 10000, 50000, 100000, 1000000
    val totalSales: Double,
    val region: SalesRegion? = null, // null = global
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Événement d'activité d'un éditeur.
 * Pour la page Editors.
 */
@Serializable
data class PublisherActivityEvent(
    val publisherId: String,
    val publisherName: String,
    val activityType: PublisherActivityType,
    val gameId: String? = null,
    val gameName: String? = null,
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class PublisherActivityType {
    NEW_GAME_RELEASED,
    PATCH_DEPLOYED,
    PRICE_CHANGE,
    GAME_RETIRED,
    ACHIEVEMENT_UNLOCKED // Ex: "1M ventes totales"
}

