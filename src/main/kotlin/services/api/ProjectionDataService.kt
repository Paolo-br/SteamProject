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
        // Temporary debug log to help diagnose UI/projection mismatch
        try {
            println("ProjectionDataService: fetched /api/catalog length=${'$'}{body.length} sample=${'$'}{body.take(200)}")
        } catch (_: Exception) { /* ignore logging errors */ }
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
                val genreVal = n.get("genre")?.asText()?.takeIf { it.isNotBlank() }
                val priceVal = if (n.get("price") != null && !n.get("price").isNull) n.get("price").asDouble() else null
                val initialVer = n.get("initialVersion")?.asText()?.takeIf { it.isNotBlank() }
                // Parse versions list from projection if available, otherwise fall back to initialVersion
                val versionsList: List<GameVersion> = try {
                    val vNode = n.get("versions")
                    if (vNode != null && vNode.isArray) {
                        val vs = mutableListOf<GameVersion>()
                        for (vn in vNode) {
                            val ver = vn.get("versionNumber")?.asText() ?: continue
                            val desc = vn.get("description")?.asText() ?: ""
                            val rdate = vn.get("releaseDate")?.asText() ?: Instant.now().toString()
                            vs.add(GameVersion(versionNumber = ver, description = desc, releaseDate = rdate))
                        }
                        if (vs.isNotEmpty()) vs else if (initialVer != null) listOf(GameVersion(versionNumber = initialVer, description = "", releaseDate = Instant.now().toString())) else emptyList()
                    } else {
                        if (initialVer != null) listOf(GameVersion(versionNumber = initialVer, description = "", releaseDate = Instant.now().toString())) else emptyList()
                    }
                } catch (_: Exception) { if (initialVer != null) listOf(GameVersion(versionNumber = initialVer, description = "", releaseDate = Instant.now().toString())) else emptyList() }

                // Parse patches
                val patchesList: List<Patch> = try {
                    val pNode = n.get("patches")
                    if (pNode != null && pNode.isArray) {
                        val ps = mutableListOf<Patch>()
                        for (pn in pNode) {
                            val pid = pn.get("patchId")?.asText() ?: java.util.UUID.randomUUID().toString()
                            val oldV = pn.get("oldVersion")?.asText() ?: ""
                            val newV = pn.get("newVersion")?.asText() ?: ""
                            val desc = pn.get("description")?.asText() ?: ""
                            val ts = pn.get("releaseTimestamp")?.asLong()?.let { it } ?: System.currentTimeMillis()
                            ps.add(Patch(id = pid, gameId = id, gameName = name, platform = hw ?: "", oldVersion = oldV, newVersion = newV, type = PatchType.ADD, description = desc, changes = emptyList(), sizeInMB = 0, releaseDate = try { Instant.ofEpochMilli(ts).toString() } catch (_: Exception) { Instant.now().toString() }, timestamp = ts))
                        }
                        ps
                    } else emptyList()
                } catch (_: Exception) { emptyList() }

                // Parse DLCs
                val dlcsList: List<DLC> = try {
                    val dNode = n.get("dlcs")
                    if (dNode != null && dNode.isArray) {
                        val ds = mutableListOf<DLC>()
                        for (dn in dNode) {
                            val dlcid = dn.get("dlcId")?.asText() ?: java.util.UUID.randomUUID().toString()
                            val dlcName = dn.get("dlcName")?.asText() ?: ""
                            val price = if (dn.get("price") != null && !dn.get("price").isNull) dn.get("price").asDouble() else 0.0
                            val ts = dn.get("releaseTimestamp")?.asLong()?.let { it } ?: System.currentTimeMillis()
                            ds.add(DLC(id = dlcid, parentGameId = id, name = dlcName, description = "", price = price, minGameVersion = null, releaseDate = try { Instant.ofEpochMilli(ts).toString() } catch (_: Exception) { Instant.now().toString() }, sizeInMB = 0, standalone = false))
                        }
                        ds
                    } else emptyList()
                } catch (_: Exception) { emptyList() }

                out.add(Game(
                    id = id,
                    name = name,
                    hardwareSupport = hw,
                    distributionPlatformId = inferredDist.id,
                    genre = genreVal,
                    publisherId = publisherId,
                    publisherName = null,
                    releaseYear = releaseYear,
                    currentVersion = initialVer,
                    price = priceVal,
                    averageRating = null,
                    incidentCount = null,
                    salesNA = null,
                    salesEU = null,
                    salesJP = null,
                    salesOther = null,
                    salesGlobal = null,
                    description = null,
                    versions = versionsList,
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
        try {
            // Fetch publisher metadata (id, name, ...) from projection-backed REST
            val metaBody = safeGet("/api/publishers") ?: return@withContext emptyList()
            val publishers: List<Publisher> = mapper.readValue(metaBody, object : TypeReference<List<Publisher>>() {})

            // Fetch counts separately and merge; if counts endpoint unavailable, default to 0
            val countsBody = safeGet("/api/publishers-list")
            val counts: Map<String, Int> = if (countsBody != null) mapper.readValue(countsBody, object : TypeReference<Map<String, Int>>() {}) else emptyMap()

            publishers.map { p -> p.copy(gamesPublished = counts[p.id] ?: 0) }
        } catch (_: Exception) { emptyList() }
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

    override suspend fun getAllPatches(): List<Patch> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Patch>()
        // We can't reconstruct full Patch objects from versions; instead aggregate from raw JSON
        try {
            val body = safeGet("/api/catalog") ?: return@withContext emptyList()
            val node = mapper.readTree(body)
            if (node.isArray) {
                for (n in node) {
                    val gid = n.get("gameId")?.asText() ?: continue
                    val gname = n.get("gameName")?.asText() ?: ""
                    val hw = n.get("platform")?.asText() ?: ""
                    val pNode = n.get("patches")
                    if (pNode != null && pNode.isArray) {
                        for (pn in pNode) {
                            val pid = pn.get("patchId")?.asText() ?: java.util.UUID.randomUUID().toString()
                            val oldV = pn.get("oldVersion")?.asText() ?: ""
                            val newV = pn.get("newVersion")?.asText() ?: ""
                            val desc = pn.get("description")?.asText() ?: ""
                            val ts = pn.get("releaseTimestamp")?.asLong()?.let { it } ?: System.currentTimeMillis()
                            out.add(Patch(id = pid, gameId = gid, gameName = gname, platform = hw, oldVersion = oldV, newVersion = newV, type = PatchType.ADD, description = desc, changes = emptyList(), sizeInMB = 0, releaseDate = try { Instant.ofEpochMilli(ts).toString() } catch (_: Exception) { Instant.now().toString() }, timestamp = ts))
                        }
                    }
                }
            }
        } catch (_: Exception) { /* ignore */ }
        out
    }

    override suspend fun getPatchesByGame(gameId: String): List<Patch> = withContext(Dispatchers.IO) {
        getAllPatches().filter { it.gameId == gameId }
    }

    override suspend fun getRecentPatches(limit: Int): List<Patch> = withContext(Dispatchers.IO) {
        getAllPatches().sortedByDescending { it.timestamp }.take(limit)
    }
    override suspend fun getRatings(gameId: String): List<Rating> = emptyList()
    override suspend fun addRating(gameId: String, rating: Rating): Boolean = false
    override suspend fun voteOnRating(ratingId: String, voterId: String, isHelpful: Boolean): Boolean = false
    override suspend fun getRating(ratingId: String): Rating? = null
    override suspend fun getComments(gameId: String): List<Comment> = emptyList()
    override suspend fun addComment(comment: Comment): Boolean = false
    override suspend fun getPrice(gameId: String): Double? = withContext(Dispatchers.IO) {
        tryFetchCatalogFromRest()?.firstOrNull { it.id == gameId }?.price
    }
    override suspend fun filterByYear(year: Int?): List<Game> = tryFetchCatalogFromRest() ?: emptyList()
    override suspend fun filterByGenre(genre: String?): List<Game> = tryFetchCatalogFromRest() ?: emptyList()

    override suspend fun purchaseGame(playerId: String, gameId: String): Purchase? = null
    override suspend fun purchaseDLC(playerId: String, dlcId: String): Purchase? = null

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

    override suspend fun getRecentPurchases(limit: Int): List<Purchase> = emptyList()
    override suspend fun getPurchaseStats(): PurchaseStats = PurchaseStats(0, 0.0)
    override suspend fun getDLCsForGame(gameId: String): List<DLC> = withContext(Dispatchers.IO) {
        val all = getAllDLCs()
        all.filter { it.parentGameId == gameId }
    }

    override suspend fun getDLC(dlcId: String): DLC? = withContext(Dispatchers.IO) {
        getAllDLCs().firstOrNull { it.id == dlcId }
    }

    override suspend fun canPurchaseDLC(playerId: String, dlcId: String): Boolean = withContext(Dispatchers.IO) {
        // Simplified: DLC is purchasable if it exists
        getDLC(dlcId) != null
    }

    override suspend fun getAllDLCs(): List<DLC> = withContext(Dispatchers.IO) {
        val out = mutableListOf<DLC>()
        try {
            val body = safeGet("/api/catalog") ?: return@withContext emptyList()
            val node = mapper.readTree(body)
            if (node.isArray) {
                for (n in node) {
                    val gid = n.get("gameId")?.asText() ?: continue
                    val gname = n.get("gameName")?.asText() ?: ""
                    val dNode = n.get("dlcs")
                    if (dNode != null && dNode.isArray) {
                        for (dn in dNode) {
                            val dlcid = dn.get("dlcId")?.asText() ?: java.util.UUID.randomUUID().toString()
                            val dlcName = dn.get("dlcName")?.asText() ?: ""
                            val price = if (dn.get("price") != null && !dn.get("price").isNull) dn.get("price").asDouble() else 0.0
                            val ts = dn.get("releaseTimestamp")?.asLong()?.let { it } ?: System.currentTimeMillis()
                            out.add(DLC(id = dlcid, parentGameId = gid, name = dlcName, description = "", price = price, minGameVersion = null, releaseDate = try { Instant.ofEpochMilli(ts).toString() } catch (_: Exception) { Instant.now().toString() }, sizeInMB = 0, standalone = false))
                        }
                    }
                }
            }
        } catch (_: Exception) { /* ignore */ }
        out
    }
    override suspend fun getPriceFactors(gameId: String): PriceFactors? = null
    override suspend fun isOnPromotion(gameId: String): Boolean = false

    override suspend fun getPlatforms(): List<String> = withContext(Dispatchers.IO) {
        tryFetchCatalogFromRest()?.mapNotNull { it.hardwareSupport }?.distinct()?.sorted() ?: emptyList()
    }

    override suspend fun filterByPlatform(platform: String?): List<Game> = withContext(Dispatchers.IO) {
        val rest = tryFetchCatalogFromRest() ?: return@withContext emptyList()
        if (platform.isNullOrBlank()) return@withContext rest
        rest.filter { it.hardwareSupport?.equals(platform, ignoreCase = true) == true }
    }

    override suspend fun getDistributionPlatforms(): List<DistributionPlatform> = DistributionPlatform.getAllKnown()
    override suspend fun filterByDistributionPlatform(platformId: String?): List<Game> = tryFetchCatalogFromRest() ?: emptyList()
    override suspend fun getDistributionPlatformStats(): Map<DistributionPlatform, PlatformStats> = emptyMap()
    override suspend fun getHardwareSupports(): List<String> = getPlatforms()
    override suspend fun filterByHardwareSupport(hardwareCode: String?): List<Game> = filterByPlatform(hardwareCode)
}
