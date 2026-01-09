package org.example.services.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.model.*
import org.steamproject.model.Game as JavaGame
import org.steamproject.service.GameDataService
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Adapter qui réutilise la `GameDataService` Java (chargée depuis classpath)
 * et mappe les objets Java `org.steamproject.model.Game` vers
 * les data classes Kotlin `org.example.model.Game`.
 */
class JavaBackedDataService(private val resourcePath: String = "/data/vgsales.csv") : DataService {

    private val javaService = GameDataService(resourcePath)

    private fun map(javaGame: JavaGame): Game {
        val publisherId = javaGame.getPublisher() ?: ""
        val idBytes = (javaGame.getName() + "|" + (javaGame.getPlatform() ?: "")).toByteArray(StandardCharsets.UTF_8)
        val id = UUID.nameUUIDFromBytes(idBytes).toString()

        return Game(
            id = id,
            name = javaGame.getName() ?: "",
            platform = javaGame.getPlatform(),
            genre = javaGame.getGenre(),
            publisherId = if (publisherId.isNotBlank()) publisherId.lowercase().replace(" ", "_") else null,
            publisherName = javaGame.getPublisher(),
            releaseYear = javaGame.getYear(),
            currentVersion = "1.0.0",
            price = null,
            averageRating = null,
            incidentCount = null,
            salesNA = javaGame.getNaSales(),
            salesEU = javaGame.getEuSales(),
            salesJP = javaGame.getJpSales(),
            salesOther = javaGame.getOtherSales(),
            salesGlobal = javaGame.getGlobalSales(),
            description = null,
            versions = emptyList(),
            incidents = emptyList(),
            ratings = emptyList()
        )
    }

    override suspend fun getCatalog(): List<Game> = withContext(Dispatchers.IO) {
        javaService.getAll().stream().map { map(it) }.toList()
    }

    override suspend fun getGame(gameId: String): Game? = withContext(Dispatchers.IO) {
        javaService.getAll().stream().map { map(it) }.filter { it.id == gameId }.findFirst().orElse(null)
    }

    override suspend fun searchGames(query: String): List<Game> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext javaService.getAll().stream().map { map(it) }.toList()
        val q = query.lowercase()
        javaService.getAll().stream()
            .map { map(it) }
            .filter { (it.name.lowercase().contains(q) || (it.genre ?: "").lowercase().contains(q) || (it.publisherName ?: "").lowercase().contains(q)) }
            .toList()
    }

    override suspend fun getGamesByPublisher(publisherId: String): List<Game> = withContext(Dispatchers.IO) {
        javaService.getAll().stream().map { map(it) }.filter { it.publisherId == publisherId }.toList()
    }

    override suspend fun getPublishers(): List<Publisher> = withContext(Dispatchers.IO) {
        javaService.getAll().mapNotNull { g ->
            g.getPublisher() ?: null
        }.distinct().map { name -> Publisher(id = name.lowercase().replace(" ", "_"), name = name) }
    }

    override suspend fun getPublisher(publisherId: String): Publisher? = withContext(Dispatchers.IO) {
        getPublishers().firstOrNull { it.id == publisherId }
    }

    override suspend fun getPlayers(): List<Player> = emptyList()
    override suspend fun getPlayer(playerId: String): Player? = null
    override suspend fun getAllPatches(): List<Patch> = emptyList()
    override suspend fun getPatchesByGame(gameId: String): List<Patch> = emptyList()
    override suspend fun getRecentPatches(limit: Int): List<Patch> = emptyList()
    override suspend fun getRatings(gameId: String): List<Rating> = emptyList()
    override suspend fun addRating(gameId: String, rating: Rating): Boolean = false
    override suspend fun getPrice(gameId: String): Double? = null

    override suspend fun filterByYear(year: Int?): List<Game> = withContext(Dispatchers.IO) {
        javaService.filterByYear(year).map { map(it) }
    }

    override suspend fun filterByGenre(genre: String?): List<Game> = withContext(Dispatchers.IO) {
        javaService.filterByGenre(genre).map { map(it) }
    }

}
