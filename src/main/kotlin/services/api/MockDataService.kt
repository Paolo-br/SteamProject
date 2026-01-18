package org.example.services.api

import org.example.model.*

/**
 * Minimal, compile-safe stub of `MockDataService`.
 * Returns empty lists / nulls so it doesn't interfere with the running app.
 */
@Deprecated("Use JavaBackedDataService instead")
class MockDataService : DataService {
    override suspend fun getCatalog(): List<Game> = emptyList()
    override suspend fun getGame(gameId: String): Game? = null
    override suspend fun searchGames(query: String): List<Game> = emptyList()
    override suspend fun getGamesByPublisher(publisherId: String): List<Game> = emptyList()
    override suspend fun getPublishers(): List<Publisher> = emptyList()
    override suspend fun getPublisher(publisherId: String): Publisher? = null
    override suspend fun getPlayers(): List<Player> = emptyList()
    override suspend fun getPlayer(playerId: String): Player? = null
    override suspend fun getAllPatches(): List<Patch> = emptyList()
    override suspend fun getPatchesByGame(gameId: String): List<Patch> = emptyList()
    override suspend fun getRecentPatches(limit: Int): List<Patch> = emptyList()
    override suspend fun getRatings(gameId: String): List<Rating> = emptyList()
    override suspend fun addRating(gameId: String, rating: Rating): Boolean = false
    override suspend fun voteOnRating(ratingId: String, voterId: String, isHelpful: Boolean): Boolean = false
    override suspend fun getRating(ratingId: String): Rating? = null
    override suspend fun getComments(gameId: String): List<Comment> = emptyList()
    override suspend fun addComment(comment: Comment): Boolean = false
    override suspend fun getPrice(gameId: String): Double? = null
    override suspend fun filterByYear(year: Int?): List<Game> = emptyList()
    override suspend fun filterByGenre(genre: String?): List<Game> = emptyList()

    // ========== ACHATS ==========
    override suspend fun purchaseGame(playerId: String, gameId: String): Purchase? = null
    override suspend fun purchaseDLC(playerId: String, dlcId: String): Purchase? = null
    override suspend fun getPurchasesByPlayer(playerId: String): List<Purchase> = emptyList()
    override suspend fun getRecentPurchases(limit: Int): List<Purchase> = emptyList()
    override suspend fun getPurchaseStats(): PurchaseStats = PurchaseStats(0, 0.0)

    // ========== DLC ==========
    override suspend fun getDLCsForGame(gameId: String): List<DLC> = emptyList()
    override suspend fun getDLC(dlcId: String): DLC? = null
    override suspend fun canPurchaseDLC(playerId: String, dlcId: String): Boolean = false
    override suspend fun getAllDLCs(): List<DLC> = emptyList()

    // ========== PRIX AVANCÉS ==========
    override suspend fun getPriceFactors(gameId: String): PriceFactors? = null
    override suspend fun isOnPromotion(gameId: String): Boolean = false

    // ========== PLATEFORMES ==========
    override suspend fun getPlatforms(): List<String> = emptyList()
    override suspend fun filterByPlatform(platform: String?): List<Game> = emptyList()

    // Lightweight dynamic methods used by tests or FakeKafkaService (optional)
    fun addPatch(patch: Patch) {}
    fun updatePrice(gameId: String, newPrice: Double) {}
    fun updateIncidents(gameId: String, additionalIncidents: Int) {}
}


