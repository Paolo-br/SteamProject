package org.example.services.api

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.model.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.Duration

/**
 * Projection-backed `DataService` implementation.
 *
 * Calls the local projection REST endpoints (Streams / Purchase REST). When the
 * projection service is unavailable, falls back to `MockDataService` to remain
 * non-breaking for the UI.
 */
class ProjectionDataService(private val restBaseUrl: String = "http://localhost:8080") : DataService {
    private val fallback: DataService = MockDataService()
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()
    private val mapper = jacksonObjectMapper()

    private fun safeGet(path: String): String? {
        return try {
            val req = HttpRequest.newBuilder().uri(URI.create(restBaseUrl + path)).GET().build()
            val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() == 200) resp.body() else null
        } catch (_: Exception) { null }
    }

    private fun tryFetchCatalogFromRest(): List<Game>? {
        val body = safeGet("/api/catalog") ?: return null
        return try {
            val node = mapper.readTree(body)
            if (!node.isArray) return emptyList()
            val out = mutableListOf<Game>()
            for (n in node) {
                val id = n.get("gameId")?.asText() ?: continue
                val name = n.get("gameName")?.asText() ?: ""
                val hw = n.get("platform")?.asText()
                val releaseYear = n.get("releaseYear")?.asInt() ?: null
                val publisherId = n.get("publisherId")?.asText()?.takeIf { it.isNotBlank() }
                val inferredDist = if (hw != null) DistributionPlatform.inferFromHardwareCode(hw) else DistributionPlatform.STEAM
                out.add(Game(
                    id = id,
                    name = name,
                    hardwareSupport = hw,
                    distributionPlatformId = inferredDist.id,
                    genre = null,
                    publisherId = publisherId,
                    publisherName = null,
                    releaseYear = releaseYear,
                    currentVersion = null,
                    price = null,
                    averageRating = null,
                    incidentCount = null,
                    salesNA = null,
                    salesEU = null,
                    salesJP = null,
                    salesOther = null,
                    salesGlobal = null,
                    description = null,
                    versions = emptyList(),
                    incidents = emptyList(),
                    ratings = emptyList()
                ))
            }
            out
        } catch (_: Exception) { null }
    }

    override suspend fun getCatalog(): List<Game> = withContext(Dispatchers.IO) {
        tryFetchCatalogFromRest() ?: emptyList()
    }

    override suspend fun getGame(gameId: String): Game? = withContext(Dispatchers.IO) {
        tryFetchCatalogFromRest()?.firstOrNull { it.id == gameId }
    }

    override suspend fun searchGames(query: String): List<Game> = withContext(Dispatchers.IO) {
        val rest = tryFetchCatalogFromRest() ?: return@withContext emptyList()
        if (query.isBlank()) return@withContext rest
        val q = query.lowercase()
        rest.filter { (it.name.lowercase().contains(q) || (it.genre ?: "").lowercase().contains(q) || (it.publisherName ?: "").lowercase().contains(q)) }
    }

    override suspend fun getGamesByPublisher(publisherId: String): List<Game> = withContext(Dispatchers.IO) {
        tryFetchCatalogFromRest()?.filter { it.publisherId == publisherId } ?: emptyList()
    }

    override suspend fun getPublishers(): List<Publisher> = withContext(Dispatchers.IO) {
        // Prefer ingestion-backed publishers but update counts from projection when available
        try {
            // Try publishers list from projection
            val body = safeGet("/api/publishers-list") ?: return@withContext fallback.getPublishers()
            val counts: Map<String, Int> = mapper.readValue(body, object : TypeReference<Map<String, Int>>() {})
            // Merge with fallback ingestion publishers
            val base = fallback.getPublishers()
            base.map { p -> p.copy(gamesPublished = counts[p.id] ?: p.gamesPublished) }
        } catch (_: Exception) { fallback.getPublishers() }
    }

    override suspend fun getPublisher(publisherId: String): Publisher? = withContext(Dispatchers.IO) {
        getPublishers().firstOrNull { it.id == publisherId }
    }

    override suspend fun getPlayers(): List<Player> = withContext(Dispatchers.IO) {
        try {
            val body = safeGet("/api/players") ?: return@withContext emptyList()
            val nodes: JsonNode = mapper.readTree(body)
            val out = mutableListOf<Player>()
            if (nodes.isArray) {
                for (n in nodes) {
                    val id = n.get("id")?.asText() ?: continue
                    val username = n.get("username")?.asText() ?: ""
                    val email = n.get("email")?.asText() ?: ""
                    val reg = n.get("registrationDate")?.asText() ?: ""
                    val libBody = safeGet("/api/players/$id/library")
                    val library = mutableListOf<GameOwnership>()
                    if (libBody != null) {
                        val libNodes = mapper.readTree(libBody)
                        if (libNodes.isArray) {
                            for (ln in libNodes) {
                                val gid = ln.get("gameId")?.asText() ?: continue
                                val gname = ln.get("gameName")?.asText() ?: ""
                                val purchaseDate = ln.get("purchaseDate")?.asText() ?: Instant.now().toString()
                                val playtime = ln.get("playtime")?.asInt() ?: 0
                                val pricePaid = ln.get("pricePaid")?.asDouble() ?: 0.0
                                library.add(GameOwnership(gameId = gid, gameName = gname, purchaseDate = purchaseDate, playtime = playtime, lastPlayed = null, pricePaid = pricePaid))
                            }
                        }
                    }
                    out.add(Player(id = id, username = username, email = email, registrationDate = reg, library = library))
                }
            }
            out
        } catch (_: Exception) { emptyList() }
    }

    override suspend fun getPlayer(playerId: String): Player? = withContext(Dispatchers.IO) {
        val players = getPlayers()
        players.firstOrNull { it.id == playerId }
    }

    override suspend fun getAllPatches(): List<Patch> = fallback.getAllPatches()
    override suspend fun getPatchesByGame(gameId: String): List<Patch> = fallback.getPatchesByGame(gameId)
    override suspend fun getRecentPatches(limit: Int): List<Patch> = fallback.getRecentPatches(limit)
    override suspend fun getRatings(gameId: String): List<Rating> = fallback.getRatings(gameId)
    override suspend fun addRating(gameId: String, rating: Rating): Boolean = fallback.addRating(gameId, rating)
    override suspend fun voteOnRating(ratingId: String, voterId: String, isHelpful: Boolean): Boolean = fallback.voteOnRating(ratingId, voterId, isHelpful)
    override suspend fun getRating(ratingId: String): Rating? = fallback.getRating(ratingId)
    override suspend fun getComments(gameId: String): List<Comment> = fallback.getComments(gameId)
    override suspend fun addComment(comment: Comment): Boolean = fallback.addComment(comment)
    override suspend fun getPrice(gameId: String): Double? = fallback.getPrice(gameId)
    override suspend fun filterByYear(year: Int?): List<Game> = tryFetchCatalogFromRest() ?: fallback.filterByYear(year)
    override suspend fun filterByGenre(genre: String?): List<Game> = tryFetchCatalogFromRest() ?: fallback.filterByGenre(genre)

    override suspend fun purchaseGame(playerId: String, gameId: String): Purchase? = fallback.purchaseGame(playerId, gameId)
    override suspend fun purchaseDLC(playerId: String, dlcId: String): Purchase? = fallback.purchaseDLC(playerId, dlcId)

    override suspend fun getPurchasesByPlayer(playerId: String): List<Purchase> = withContext(Dispatchers.IO) {
        try {
            val libBody = safeGet("/api/players/$playerId/library") ?: return@withContext emptyList()
            val libNodes = mapper.readTree(libBody)
            val out = mutableListOf<Purchase>()
            if (libNodes.isArray) {
                for (n in libNodes) {
                    val gid = n.get("gameId")?.asText() ?: continue
                    val gname = n.get("gameName")?.asText() ?: ""
                    val purchaseDate = n.get("purchaseDate")?.asText() ?: Instant.now().toString()
                    val pricePaid = n.get("pricePaid")?.asDouble() ?: 0.0
                    val timestamp = try { Instant.parse(purchaseDate).toEpochMilli() } catch (_: Exception) { System.currentTimeMillis() }
                    out.add(Purchase(purchaseId = "$playerId-$gid", gameId = gid, gameName = gname, playerId = playerId, playerUsername = "", pricePaid = pricePaid, platform = "", timestamp = timestamp, isDlc = false, dlcId = null))
                }
            }
            out
        } catch (_: Exception) { emptyList() }
    }

    override suspend fun getRecentPurchases(limit: Int): List<Purchase> = fallback.getRecentPurchases(limit)
    override suspend fun getPurchaseStats(): PurchaseStats = fallback.getPurchaseStats()
    override suspend fun getDLCsForGame(gameId: String): List<DLC> = fallback.getDLCsForGame(gameId)
    override suspend fun getDLC(dlcId: String): DLC? = fallback.getDLC(dlcId)
    override suspend fun canPurchaseDLC(playerId: String, dlcId: String): Boolean = fallback.canPurchaseDLC(playerId, dlcId)
    override suspend fun getAllDLCs(): List<DLC> = fallback.getAllDLCs()
    override suspend fun getPriceFactors(gameId: String): PriceFactors? = fallback.getPriceFactors(gameId)
    override suspend fun isOnPromotion(gameId: String): Boolean = fallback.isOnPromotion(gameId)

    override suspend fun getPlatforms(): List<String> = withContext(Dispatchers.IO) {
        tryFetchCatalogFromRest()?.mapNotNull { it.hardwareSupport }?.distinct()?.sorted() ?: fallback.getPlatforms()
    }

    override suspend fun filterByPlatform(platform: String?): List<Game> = withContext(Dispatchers.IO) {
        val rest = tryFetchCatalogFromRest() ?: return@withContext fallback.filterByPlatform(platform)
        if (platform.isNullOrBlank()) return@withContext rest
        rest.filter { it.hardwareSupport?.equals(platform, ignoreCase = true) == true }
    }

    override suspend fun getDistributionPlatforms(): List<DistributionPlatform> = fallback.getDistributionPlatforms()
    override suspend fun filterByDistributionPlatform(platformId: String?): List<Game> = tryFetchCatalogFromRest() ?: fallback.filterByDistributionPlatform(platformId)
    override suspend fun getDistributionPlatformStats(): Map<DistributionPlatform, PlatformStats> = fallback.getDistributionPlatformStats()
    override suspend fun getHardwareSupports(): List<String> = getPlatforms()
    override suspend fun filterByHardwareSupport(hardwareCode: String?): List<Game> = filterByPlatform(hardwareCode)
}
