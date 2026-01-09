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
    override suspend fun getPrice(gameId: String): Double? = null

    // Lightweight dynamic methods used by tests or FakeKafkaService (optional)
    fun addPatch(patch: Patch) {}
    fun updatePrice(gameId: String, newPrice: Double) {}
    fun updateIncidents(gameId: String, additionalIncidents: Int) {}
}


