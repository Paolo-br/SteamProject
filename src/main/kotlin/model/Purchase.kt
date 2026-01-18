package org.example.model

/**
 * Représente un achat effectué par un joueur.
 */
data class Purchase(
    val purchaseId: String,
    val gameId: String,
    val gameName: String,
    val playerId: String,
    val playerUsername: String,
    val pricePaid: Double,
    val platform: String,
    val timestamp: Long,
    val isDlc: Boolean = false,
    val dlcId: String? = null
)

/**
 * Représente un DLC (contenu téléchargeable).
 */
data class DLC(
    val id: String,
    val parentGameId: String,
    val name: String,
    val description: String,
    val price: Double,
    val minGameVersion: String?, // Version minimale requise
    val releaseDate: String,
    val sizeInMB: Long,
    val standalone: Boolean = false // Peut être joué sans le jeu principal
)

/**
 * Facteurs de prix pour l'algorithme qualité/demande.
 */
data class PriceFactors(
    val basePrice: Double,
    val qualityFactor: Double, // Basé sur les ratings (0.8 - 1.2)
    val demandFactor: Double,  // Basé sur les ventes (0.9 - 1.3)
    val promotionFactor: Double, // Réduction promotionnelle
    val finalPrice: Double
) {
    val isOnPromotion: Boolean get() = promotionFactor < 1.0
    val promotionPercent: Double get() = if (isOnPromotion) (1.0 - promotionFactor) * 100.0 else 0.0
}

/**
 * Statistiques d'achats.
 */
data class PurchaseStats(
    val totalPurchases: Int,
    val totalRevenue: Double
)
