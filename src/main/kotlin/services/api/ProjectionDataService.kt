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
 * Implémentation de `DataService` basée sur les projections.
 *
 * Appelle les endpoints REST locaux de projection (Streams / Purchase).
 * Si le service de projection est indisponible, l'implémentation retombe
 * sur des valeurs de secours pour ne pas casser l'interface utilisateur.
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

    // Récupère de manière synchrone toutes les évaluations et les indexe par gameId
    private fun fetchAllReviewsByGame(): Map<String, List<Rating>> {
        try {
            val playersBody = safeGet("/api/players") ?: return emptyMap()
            val playersNode = mapper.readTree(playersBody)
            val playerNames = mutableMapOf<String, String>()
            if (playersNode.isArray) {
                for (pn in playersNode) {
                    val pid = pn.get("id")?.asText() ?: continue
                    val uname = pn.get("username")?.asText() ?: "Anonymous"
                    playerNames[pid] = uname
                }
            }

            val byGame = mutableMapOf<String, MutableList<Rating>>()
            if (playersNode.isArray) {
                for (pn in playersNode) {
                    val pid = pn.get("id")?.asText() ?: continue
                    val uname = playerNames[pid] ?: "Anonymous"
                    val revBody = safeGet("/api/players/$pid/reviews") ?: continue
                    val revNode = mapper.readTree(revBody)
                    if (!revNode.isArray) continue
                    for (rn in revNode) {
                        val gid = rn.get("gameId")?.asText() ?: continue
                        val rid = rn.get("reviewId")?.asText() ?: java.util.UUID.randomUUID().toString()
                        val ratingVal = try { rn.get("rating")?.asInt() ?: 0 } catch (_: Exception) { 0 }
                        val title = rn.get("title")?.asText() ?: rn.get("text")?.asText() ?: ""
                        val ts = try { rn.get("timestamp")?.asLong() ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }
                        val date = try { java.time.Instant.ofEpochMilli(ts).toString() } catch (_: Exception) { java.time.Instant.now().toString() }
                        val playtime = try { rn.get("playtime")?.asInt() ?: 0 } catch (_: Exception) { 0 }
                        val isRecommended = try { rn.get("isRecommended")?.asBoolean() ?: true } catch (_: Exception) { true }
                        val model = Rating(id = rid, username = uname, playerId = pid, rating = ratingVal, comment = title, date = date, playtime = playtime, isRecommended = isRecommended)
                        val list = byGame.getOrPut(gid) { mutableListOf() }
                        list.add(model)
                    }
                }
            }
            return byGame
        } catch (_: Exception) { return emptyMap() }
    }

    private fun fetchRatingsForGameSync(gameId: String): List<Rating> {
        return try {
            val byGame = fetchAllReviewsByGame()
            byGame[gameId] ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun tryFetchCatalogFromRest(): List<Game>? {
        val body = safeGet("/api/catalog") ?: return null
        // Log temporaire pour aider à diagnostiquer un éventuel décalage UI/projection
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
                   val hw = n.get("console")?.asText() ?: n.get("platform")?.asText()
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

                    val incidentCnt = try { if (n.get("incidentCount") != null && !n.get("incidentCount").isNull) n.get("incidentCount").asInt() else null } catch (_: Exception) { null }
                    // Build incident history from projection's incidentResponses (aggregate per day)
                    val incidentsList: List<Incident> = try {
                        val respNode = n.get("incidentResponses")
                        if (respNode != null && respNode.isArray) {
                            val counts = mutableMapOf<String, Int>()
                            val fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
                            val zid = java.time.ZoneId.systemDefault()
                            for (rn in respNode) {
                                val ts = try { rn.get("responseTimestamp")?.asLong() ?: rn.get("timestamp")?.asLong() ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }
                                val date = try { java.time.Instant.ofEpochMilli(ts).atZone(zid).toLocalDate().format(fmt) } catch (_: Exception) { java.time.Instant.ofEpochMilli(ts).toString() }
                                counts[date] = (counts[date] ?: 0) + 1
                            }
                            counts.entries.sortedByDescending { it.key }.map { Incident(date = it.key, count = it.value) }
                        } else emptyList()
                    } catch (_: Exception) { emptyList() }

                    // attach ratings and average from player reviews if available
                    val ratingsForThisGame: List<Rating> = fetchRatingsForGameSync(id)
                    val avg = if (ratingsForThisGame.isNotEmpty()) ratingsForThisGame.map { it.rating }.average() else null

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
                    averageRating = avg,
                    incidentCount = incidentCnt,
                    salesNA = null,
                    salesEU = null,
                    salesJP = null,
                    salesOther = null,
                    salesGlobal = null,
                    description = null,
                    versions = versionsList,
                    incidents = incidentsList,
                    ratings = if (ratingsForThisGame.isEmpty()) emptyList() else ratingsForThisGame
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

            // Fetch catalog & purchases to compute incidents sum, patch counts and active games
            val catalogBody = safeGet("/api/catalog")
            val catalogNode: JsonNode? = catalogBody?.let { mapper.readTree(it) }
            val purchasesBody = safeGet("/api/purchases")
            val purchasesNode: JsonNode? = purchasesBody?.let { mapper.readTree(it) }

            // Build helper maps per publisher
            val incidentsByPub = mutableMapOf<String, Int>()
            val patchesByPub = mutableMapOf<String, Int>()
            val publishedGamesByPub = mutableMapOf<String, MutableSet<String>>()

            try {
                if (catalogNode != null && catalogNode.isArray) {
                    for (n in catalogNode) {
                        val gid = n.get("gameId")?.asText() ?: continue
                        val pubId = n.get("publisherId")?.asText()
                        if (pubId != null) {
                            val inc = try { if (n.get("incidentCount") != null && !n.get("incidentCount").isNull) n.get("incidentCount").asInt() else 0 } catch (_: Exception) { 0 }
                            incidentsByPub[pubId] = (incidentsByPub[pubId] ?: 0) + inc

                            val pNode = n.get("patches")
                            val pCount = if (pNode != null && pNode.isArray) pNode.size() else 0
                            patchesByPub[pubId] = (patchesByPub[pubId] ?: 0) + pCount

                            val set = publishedGamesByPub.getOrPut(pubId) { mutableSetOf() }
                            set.add(gid)
                        }
                    }
                }
            } catch (_: Exception) { /* ignore */ }

            // Determine which games were actually purchased
            val purchasedGameIds = mutableSetOf<String>()
            try {
                if (purchasesNode != null && purchasesNode.isArray) {
                    for (pn in purchasesNode) {
                        pn.get("gameId")?.asText()?.let { purchasedGameIds.add(it) }
                    }
                }
            } catch (_: Exception) { /* ignore */ }

            // Map publishers and inject computed stats (publisher averageRating left empty for now)
            publishers.map { p ->
                val gpSet = publishedGamesByPub[p.id] ?: emptySet()
                val active = gpSet.count { purchasedGameIds.contains(it) }
                val patches = patchesByPub[p.id] ?: 0
                val incidents = incidentsByPub[p.id]
                p.copy(gamesPublished = counts[p.id] ?: gpSet.size, activeGames = active, totalIncidents = incidents, patchCount = patches, averageRating = null)
            }
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
                                val platform = ln.get("platform")?.asText()
                                library.add(GameOwnership(gameId = gid, gameName = gname, purchaseDate = purchaseDate, playtime = playtime, lastPlayed = null, pricePaid = pricePaid, platform = platform))
                            }
                        }
                    }
                    val gdprConsent = n.get("gdprConsent")?.asBoolean() ?: false
                    val gdprConsentDate = n.get("gdprConsentDate")?.asText()?.takeIf { it.isNotBlank() }
                    val firstName = n.get("firstName")?.asText()?.takeIf { it.isNotBlank() }
                    val lastName = n.get("lastName")?.asText()?.takeIf { it.isNotBlank() }
                    val dateOfBirth = n.get("dateOfBirth")?.asText()?.takeIf { it.isNotBlank() }
                    // Fetch reviews for this player to populate evaluation count and last evaluation date
                    var evalCount: Int? = null
                    var lastEvalDate: String? = null
                    try {
                        val revBody = safeGet("/api/players/$id/reviews")
                        if (revBody != null) {
                            val revNode = mapper.readTree(revBody)
                            if (revNode.isArray) {
                                evalCount = revNode.size()
                                var maxTs: Long = 0L
                                for (rn in revNode) {
                                    val ts = try { rn.get("timestamp")?.asLong() ?: 0L } catch (_: Exception) { 0L }
                                    if (ts > maxTs) maxTs = ts
                                }
                                if (maxTs > 0L) lastEvalDate = try { java.time.Instant.ofEpochMilli(maxTs).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() } catch (_: Exception) { null }
                            }
                        }
                    } catch (_: Exception) { /* ignore */ }

                    // compute total playtime (sum of library playtimes in hours)
                    val totalPlay = if (library.isEmpty()) null else library.sumOf { it.playtime }
                    out.add(Player(id = id, username = username, email = email, registrationDate = reg, firstName = firstName, lastName = lastName, dateOfBirth = dateOfBirth, gdprConsent = gdprConsent, gdprConsentDate = gdprConsentDate, library = library, totalPlaytime = totalPlay, lastEvaluationDate = lastEvalDate, evaluationsCount = evalCount))
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
        // Impossible de reconstituer des objets Patch complets depuis les seules versions ; on agrège donc depuis le JSON brut
        try {
            val body = safeGet("/api/catalog") ?: return@withContext emptyList()
            val node = mapper.readTree(body)
            if (node.isArray) {
                for (n in node) {
                    val gid = n.get("gameId")?.asText() ?: continue
                    val gname = n.get("gameName")?.asText() ?: ""
                    val console = n.get("console")?.asText() ?: n.get("platform")?.asText() ?: ""
                    val pNode = n.get("patches")
                    if (pNode != null && pNode.isArray) {
                        for (pn in pNode) {
                            val pid = pn.get("patchId")?.asText() ?: java.util.UUID.randomUUID().toString()
                            val oldV = pn.get("oldVersion")?.asText() ?: ""
                            val newV = pn.get("newVersion")?.asText() ?: ""
                            val desc = pn.get("description")?.asText() ?: ""
                            val ts = pn.get("releaseTimestamp")?.asLong()?.let { it } ?: System.currentTimeMillis()
                                out.add(Patch(id = pid, gameId = gid, gameName = gname, platform = console, oldVersion = oldV, newVersion = newV, type = PatchType.ADD, description = desc, changes = emptyList(), sizeInMB = 0, releaseDate = try { Instant.ofEpochMilli(ts).toString() } catch (_: Exception) { Instant.now().toString() }, timestamp = ts))
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
    override suspend fun getRatings(gameId: String): List<Rating> = withContext(Dispatchers.IO) {
        fetchRatingsForGameSync(gameId)
    }
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

    override suspend fun getRecentPurchases(limit: Int): List<Purchase> = withContext(Dispatchers.IO) {
        try {
            val body = safeGet("/api/purchases") ?: return@withContext emptyList()
            val node = mapper.readTree(body)
            if (!node.isArray) return@withContext emptyList()
            val out = mutableListOf<Purchase>()
            for (n in node) {
                val gid = n.get("gameId")?.asText() ?: continue
                val gname = n.get("gameName")?.asText() ?: ""
                val pid = n.get("playerId")?.asText() ?: ""
                val price = if (n.get("pricePaid") != null && !n.get("pricePaid").isNull) n.get("pricePaid").asDouble() else 0.0
                val date = n.get("purchaseDate")?.asText() ?: Instant.now().toString()
                val ts = try { Instant.parse(date).toEpochMilli() } catch (_: Exception) { System.currentTimeMillis() }
                out.add(Purchase(purchaseId = "$pid-$gid", gameId = gid, gameName = gname, playerId = pid, playerUsername = "", pricePaid = price, platform = "", timestamp = ts, isDlc = false, dlcId = null))
            }
            out.sortedByDescending { it.timestamp }.take(limit)
        } catch (_: Exception) { emptyList() }
    }

    override suspend fun getPurchaseStats(): PurchaseStats = withContext(Dispatchers.IO) {
        try {
            val purchases = getRecentPurchases(1000)
            val total = purchases.size
            val revenue = purchases.sumOf { it.pricePaid }
            PurchaseStats(totalPurchases = total, totalRevenue = revenue)
        } catch (_: Exception) { PurchaseStats(0, 0.0) }
    }
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
