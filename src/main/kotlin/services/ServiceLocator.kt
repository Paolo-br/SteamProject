package org.example.services

import org.example.services.api.DataService
import org.example.services.api.JavaBackedDataService
import org.example.model.*

/**
 * Service Locator pattern pour l'injection de dépendances.
 */
object ServiceLocator {
    /** Service de données de secours (retourne des listes vides). */
    private val mockDataService: DataService = object : DataService {
        override suspend fun getCatalog(): List<Game> = emptyList()
        override suspend fun getGame(gameId: String) = null
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
        override suspend fun addRating(gameId: String, rating: Rating) = false
        override suspend fun getPrice(gameId: String) = null

        // New methods default implementations
        override suspend fun filterByYear(year: Int?): List<Game> = emptyList()
        override suspend fun filterByGenre(genre: String?): List<Game> = emptyList()
    }

    /** Préfère le service Java-backed si disponible. */
    private val javaBacked: DataService? = try {
        JavaBackedDataService()
    } catch (ex: Throwable) {
        null
    }

    val dataService: DataService
        get() = javaBacked ?: mockDataService

    /** Initialise les services (à appeler au démarrage de l'application). */
    fun initialize() {
        val which = if (javaBacked != null) "JavaBackedDataService" else "MockDataService"
        println("Initialisation des services front-only (using $which)...")
    }

    /** Arrête proprement les services (à appeler à la fermeture de l'application). */
    fun shutdown() {
        println("Arrêt des services front-only")
    }
}
