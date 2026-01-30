package org.example.services.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.model.*
import org.steamproject.model.Game as JavaGame
import org.steamproject.model.Player as JavaPlayer
import org.steamproject.model.Publisher as JavaPublisher
import org.steamproject.model.Patch as JavaPatch
import org.steamproject.model.PatchType as JavaPatchType
import org.steamproject.model.Change as JavaChange
import org.steamproject.model.Comment as JavaComment
import org.steamproject.model.Purchase as JavaPurchase
import org.steamproject.model.DLC as JavaDLC
import org.steamproject.service.GameDataService
import org.steamproject.service.PublisherService
import org.steamproject.service.PlayerService
import org.steamproject.service.PatchService
import org.steamproject.service.CommentService
import org.steamproject.service.PurchaseService
import org.steamproject.service.DLCService
import org.steamproject.service.PriceService
import java.nio.charset.StandardCharsets
import java.util.*
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * Adapter qui réutilise la `GameDataService` Java (chargée depuis classpath)
 * et mappe les objets Java `org.steamproject.model.Game` vers
 * les data classes Kotlin `org.example.model.Game`.
 */
class JavaBackedDataService(private val resourcePath: String = "/data/vgsales.csv") : DataService {

    private val javaService = GameDataService(resourcePath)
    private val publisherService = PublisherService(resourcePath)
    private val playerService = PlayerService()
    private val patchService = PatchService(javaService)
    private val commentService = CommentService()
    private val priceService = PriceService()
    private val dlcService = DLCService(javaService)
    private val purchaseService = PurchaseService(playerService, javaService, dlcService, priceService)

    private fun mapPublisher(javaPub: JavaPublisher): Publisher {
        return Publisher(
            id = javaPub.getId() ?: javaPub.getName().lowercase().replace(" ", "_"),
            name = javaPub.getName(),
            foundedYear = javaPub.getFoundedYear(),
            platforms = javaPub.getPlatforms() ?: emptyList(),
            genres = javaPub.getGenres() ?: emptyList(),
            gamesPublished = javaPub.getGamesPublished() ?: 0,
            activeGames = javaPub.getActiveGames() ?: 0,
            totalIncidents = javaPub.getTotalIncidents(),
            averageRating = javaPub.getAverageRating(),
            reactivity = javaPub.getReactivity()
        )
    }

    private fun mapPlayer(javaPlayer: JavaPlayer): Player {
        return Player(
            id = javaPlayer.getId(),
            username = javaPlayer.getUsername() ?: "",
            email = javaPlayer.getEmail() ?: "",
            registrationDate = javaPlayer.getRegistrationDate() ?: "",
            // === Champs RGPD ===
            firstName = javaPlayer.getFirstName(),
            lastName = javaPlayer.getLastName(),
            dateOfBirth = javaPlayer.getDateOfBirth(),
            gdprConsent = javaPlayer.getGdprConsent() ?: false,
            gdprConsentDate = javaPlayer.getGdprConsentDate(),
            // === Bibliothèque et statistiques ===
            library = javaPlayer.getLibrary()?.map {
                GameOwnership(
                    gameId = it.gameId(),
                    gameName = it.gameName(),
                    purchaseDate = it.purchaseDate(),
                    playtime = it.playtime() ?: 0,
                    lastPlayed = it.lastPlayed(),
                    pricePaid = it.pricePaid() ?: 0.0,
                    platform = null
                )
            } ?: emptyList(),
            totalPlaytime = javaPlayer.getTotalPlaytime(),
            lastEvaluationDate = javaPlayer.getLastEvaluationDate(),
            evaluationsCount = javaPlayer.getEvaluationsCount()
        )
    }

    private fun map(javaGame: JavaGame): Game {
        val publisherId = javaGame.getPublisher() ?: ""
        val idBytes = (javaGame.getName() + "|" + (javaGame.getPlatform() ?: "")).toByteArray(StandardCharsets.UTF_8)
        val id = UUID.nameUUIDFromBytes(idBytes).toString()
        
        // Nous inférons la plateforme de distribution à partir de ce support.
        val hardwareCode = javaGame.getPlatform()
        val inferredPlatform = DistributionPlatform.inferFromHardwareCode(hardwareCode)

        return Game(
            id = id,
            name = javaGame.getName() ?: "",
            hardwareSupport = hardwareCode,
            distributionPlatformId = inferredPlatform.id,
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

    // Tentative de récupération d'un catalogue projeté via REST depuis un service local.
    // Retourne une liste vide si la projection est disponible mais vide.
    // Retourne null si la projection est indisponible (erreur HTTP / problème de connexion).
    private fun tryFetchCatalogFromRest(): List<Game>? {
        return try {
            val client = HttpClient.newHttpClient()
            val req = HttpRequest.newBuilder().uri(URI.create("http://localhost:8080/api/catalog")).GET().build()
            val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() != 200) return null
            val mapper = jacksonObjectMapper()
            val node = mapper.readTree(resp.body())
            if (!node.isArray) return emptyList()
            val out = mutableListOf<Game>()
            for (n in node) {
                val id = n.get("gameId")?.asText() ?: continue
                val name = n.get("gameName")?.asText() ?: ""
                val hw = n.get("platform")?.asText()
                val releaseYear = n.get("releaseYear")?.asInt() ?: null
                val publisherId = n.get("publisherId")?.asText()?.takeIf { it.isNotBlank() }
                val publisherName = null
                val inferredDist = if (hw != null) DistributionPlatform.inferFromHardwareCode(hw) else DistributionPlatform.STEAM
                val incidentCnt = try { if (n.get("incidentCount") != null && !n.get("incidentCount").isNull) n.get("incidentCount").asInt() else null } catch (_: Exception) { null }
                val incidentsList: List<Incident> = try {
                    val ir = n.get("incidentResponses")
                    if (ir != null && ir.isArray) {
                        val counts = mutableMapOf<String, Int>()
                        val fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
                        val zid = java.time.ZoneId.systemDefault()
                        for (rn in ir) {
                            val ts = try { rn.get("responseTimestamp")?.asLong() ?: rn.get("timestamp")?.asLong() ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }
                            val date = try { java.time.Instant.ofEpochMilli(ts).atZone(zid).toLocalDate().format(fmt) } catch (_: Exception) { java.time.Instant.ofEpochMilli(ts).toString() }
                            counts[date] = (counts[date] ?: 0) + 1
                        }
                        counts.entries.sortedByDescending { it.key }.map { Incident(date = it.key, count = it.value) }
                    } else emptyList()
                } catch (_: Exception) { emptyList() }

                val g = Game(
                    id = id,
                    name = name,
                    hardwareSupport = hw,
                    distributionPlatformId = inferredDist.id,
                    genre = null,
                    publisherId = publisherId,
                    publisherName = publisherName,
                    releaseYear = releaseYear,
                    currentVersion = null,
                    price = null,
                    averageRating = null,
                    incidentCount = incidentCnt,
                    salesNA = null,
                    salesEU = null,
                    salesJP = null,
                    salesOther = null,
                    salesGlobal = null,
                    description = null,
                    versions = emptyList(),
                    incidents = incidentsList,
                    ratings = emptyList()
                )
                out.add(g)
            }
            return out
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getCatalog(): List<Game> = withContext(Dispatchers.IO) {
        // Utilise la projection REST comme source unique de vérité pour le catalogue.
        // Si le service de projection est indisponible, retourne une liste vide
        // (ne PAS revenir aux données CSV d'ingestion).
        try {
            val rest = tryFetchCatalogFromRest()
            return@withContext rest ?: emptyList()
        } catch (_: Exception) {
            return@withContext emptyList()
        }
    }

    override suspend fun getGame(gameId: String): Game? = withContext(Dispatchers.IO) {
        return@withContext try {
            val rest = tryFetchCatalogFromRest()
            val restGame = rest?.firstOrNull { it.id == gameId }
            if (restGame != null) {
                // If the projection/catalog has no valid year (null or 0), try to read the ingestion CSV
                val year = restGame.releaseYear
                if (year == null || year == 0) {
                    try {
                        val javaGame = javaService.getById(gameId)
                        val jYear = javaGame?.getYear()
                        if (jYear != null && jYear != 0) {
                            return@withContext restGame.copy(releaseYear = jYear)
                        }
                    } catch (_: Throwable) { /* best-effort fallback */ }
                }
            }
            restGame
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun searchGames(query: String): List<Game> = withContext(Dispatchers.IO) {
        // Search only within the REST-projected catalog.
        val rest = tryFetchCatalogFromRest() ?: return@withContext emptyList()
        if (query.isBlank()) return@withContext rest
        val q = query.lowercase()
        rest.filter { (it.name.lowercase().contains(q) || (it.genre ?: "").lowercase().contains(q) || (it.publisherName ?: "").lowercase().contains(q)) }
    }

    override suspend fun getGamesByPublisher(publisherId: String): List<Game> = withContext(Dispatchers.IO) {
        val rest = tryFetchCatalogFromRest() ?: return@withContext emptyList()
        return@withContext rest.filter { it.publisherId == publisherId }
    }

    override suspend fun getPublishers(): List<Publisher> = withContext(Dispatchers.IO) {
        // Publishers are authoritative from ingestion: always return publishers created
        // by the ingestion process. Update statistics (gamesPublished) from the
        // projection when available, but do not invent publishers.
        val base = try {
            publisherService.getAllPublishers().map { mapPublisher(it) }
        } catch (_: Exception) {
            emptyList<Publisher>()
        }

        try {
            val client = HttpClient.newHttpClient()
            val req = HttpRequest.newBuilder().uri(URI.create("http://localhost:8080/api/publishers-list")).GET().build()
            val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() == 200) {
                val mapper = jacksonObjectMapper()
                val counts: Map<String, Int> = mapper.readValue(resp.body())

                // fetch catalog and purchases to compute incidents, patches and activeGames
                val catalogReq = HttpRequest.newBuilder().uri(URI.create("http://localhost:8080/api/catalog")).GET().build()
                val catalogResp = client.send(catalogReq, HttpResponse.BodyHandlers.ofString())
                val catalogNode = if (catalogResp.statusCode() == 200) mapper.readTree(catalogResp.body()) else null
                val purchasesReq = HttpRequest.newBuilder().uri(URI.create("http://localhost:8080/api/purchases")).GET().build()
                val purchasesResp = client.send(purchasesReq, HttpResponse.BodyHandlers.ofString())
                val purchasesNode = if (purchasesResp.statusCode() == 200) mapper.readTree(purchasesResp.body()) else null

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
                } catch (_: Exception) { }

                val purchasedGameIds = mutableSetOf<String>()
                try {
                    if (purchasesNode != null && purchasesNode.isArray) {
                        for (pn in purchasesNode) {
                            pn.get("gameId")?.asText()?.let { purchasedGameIds.add(it) }
                        }
                    }
                } catch (_: Exception) { }

                return@withContext base.map { p ->
                    val gpSet = publishedGamesByPub[p.id] ?: emptySet()
                    val active = gpSet.count { purchasedGameIds.contains(it) }
                    val patches = patchesByPub[p.id] ?: 0
                    val incidents = incidentsByPub[p.id]
                    p.copy(gamesPublished = counts[p.id] ?: p.gamesPublished, activeGames = active, totalIncidents = incidents, patchCount = patches)
                }
            }
        } catch (_: Exception) {}

        // Projection unavailable: return ingestion publishers as-is.
        return@withContext base
    }

    override suspend fun getPublisher(publisherId: String): Publisher? = withContext(Dispatchers.IO) {
        getPublishers().firstOrNull { it.id == publisherId }
    }

    override suspend fun getPlayers(): List<Player> = withContext(Dispatchers.IO) {
        // Return only players known to the projection. If projection is unavailable,
        // return an empty list (do NOT expose CSV/seed players).
        try {
            val client = HttpClient.newHttpClient()
            val req = HttpRequest.newBuilder().uri(URI.create("http://localhost:8080/api/players")).GET().build()
            val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() == 200) {
                val mapper = ObjectMapper()
                val nodes = mapper.readTree(resp.body())
                val out = mutableListOf<Player>()
                if (nodes.isArray) {
                    for (n in nodes) {
                        val id = n.get("id")?.asText() ?: continue
                        val username = n.get("username")?.asText() ?: ""
                        val email = n.get("email")?.asText() ?: ""
                        val reg = n.get("registrationDate")?.asText() ?: ""
                        // Fetch library for this player from projection REST so list shows correct counts
                        val libReq = HttpRequest.newBuilder().uri(URI.create("http://localhost:8080/api/players/" + id + "/library")).GET().build()
                        val libResp = try { client.send(libReq, HttpResponse.BodyHandlers.ofString()) } catch (_: Exception) { null }
                        val library = mutableListOf<org.example.model.GameOwnership>()
                        if (libResp != null && libResp.statusCode() == 200) {
                            val libNodes = mapper.readTree(libResp.body())
                            if (libNodes.isArray) {
                                for (ln in libNodes) {
                                    val gid = ln.get("gameId")?.asText() ?: continue
                                    val gname = ln.get("gameName")?.asText() ?: ""
                                    val purchaseDate = ln.get("purchaseDate")?.asText() ?: java.time.Instant.now().toString()
                                        val playtime = ln.get("playtime")?.asInt() ?: 0
                                        val platform = ln.get("platform")?.asText()
                                        library.add(org.example.model.GameOwnership(gameId = gid, gameName = gname, purchaseDate = purchaseDate, playtime = playtime, lastPlayed = null, pricePaid = 0.0, platform = platform))
                                }
                            }
                        }

                        out.add(Player(id = id, username = username, email = email, registrationDate = reg, library = library))
                    }
                }
                return@withContext out
            }
        } catch (_: Exception) {}
        emptyList()
    }

    override suspend fun getPlayer(playerId: String): Player? = withContext(Dispatchers.IO) {
        // Build Player only from projection data. If no player is present in projection,
        // return null.
        try {
            // First fetch base player info from /api/players
            val client = HttpClient.newHttpClient()
            val listReq = HttpRequest.newBuilder().uri(URI.create("http://localhost:8080/api/players")).GET().build()
            val listResp = client.send(listReq, HttpResponse.BodyHandlers.ofString())
            if (listResp.statusCode() != 200) return@withContext null
            val mapper = ObjectMapper()
            val nodes = mapper.readTree(listResp.body())
            var baseNode: com.fasterxml.jackson.databind.JsonNode? = null
            if (nodes.isArray) {
                for (n in nodes) {
                    if (n.get("id")?.asText() == playerId) { baseNode = n; break }
                }
            }
            if (baseNode == null) return@withContext null
            val username = baseNode.get("username")?.asText() ?: ""
            val email = baseNode.get("email")?.asText() ?: ""
            val reg = baseNode.get("registrationDate")?.asText() ?: ""

            // Then fetch the library
            val libReq = HttpRequest.newBuilder().uri(URI.create("http://localhost:8080/api/players/" + playerId + "/library")).GET().build()
            val libResp = client.send(libReq, HttpResponse.BodyHandlers.ofString())
            val library = mutableListOf<org.example.model.GameOwnership>()
            if (libResp.statusCode() == 200) {
                val libNodes = mapper.readTree(libResp.body())
                if (libNodes.isArray) {
                    for (n in libNodes) {
                        val gid = n.get("gameId")?.asText() ?: continue
                        val gname = n.get("gameName")?.asText() ?: ""
                        val purchaseDate = n.get("purchaseDate")?.asText() ?: java.time.Instant.now().toString()
                        val playtime = n.get("playtime")?.asInt() ?: 0
                        library.add(org.example.model.GameOwnership(gameId = gid, gameName = gname, purchaseDate = purchaseDate, playtime = playtime))
                    }
                }
            }

            return@withContext Player(id = playerId, username = username, email = email, registrationDate = reg, library = library)
        } catch (_: Exception) {
            null
        }
    }

    private fun mapPatch(j: JavaPatch): Patch {
        return Patch(
            id = j.getId(),
            gameId = j.getGameId(),
            gameName = j.getGameName(),
            platform = j.getPlatform() ?: "",
            oldVersion = j.getOldVersion() ?: "",
            newVersion = j.getNewVersion() ?: "",
            type = when (j.getType()) {
                JavaPatchType.ADD -> PatchType.ADD
                JavaPatchType.OPTIMIZATION -> PatchType.OPTIMIZATION
                else -> PatchType.FIX
            },
            description = j.getDescription() ?: "",
            // Utilisation des accesseurs de record Java 
            changes = j.getChanges()?.map {
                Change(type = when (it.type()) {
                    JavaPatchType.ADD -> PatchType.ADD
                    JavaPatchType.OPTIMIZATION -> PatchType.OPTIMIZATION
                    else -> PatchType.FIX
                }, description = it.description() ?: "")
            } ?: emptyList(),
            sizeInMB = j.getSizeInMB() ?: 0,
            releaseDate = j.getReleaseDate() ?: "",
            timestamp = j.getTimestamp()
        )
    }

    override suspend fun getAllPatches(): List<Patch> = withContext(Dispatchers.IO) {
        patchService.getAll().stream().map { mapPatch(it) }.toList()
    }

    override suspend fun getPatchesByGame(gameId: String): List<Patch> = withContext(Dispatchers.IO) {
        patchService.getPatchesByGameId(gameId).stream().map { mapPatch(it) }.toList()
    }

    override suspend fun getRecentPatches(limit: Int): List<Patch> = withContext(Dispatchers.IO) {
        patchService.getRecentPatches(limit).stream().map { mapPatch(it) }.toList()
    }

    override suspend fun getRatings(gameId: String): List<Rating> = emptyList()
    
    // === Votes sur évaluations (stockage en mémoire pour le moment) ===
    private val ratingsStore = mutableMapOf<String, Rating>()
    
    override suspend fun voteOnRating(ratingId: String, voterId: String, isHelpful: Boolean): Boolean {
        val rating = ratingsStore[ratingId] ?: return false
        if (rating.hasVoted(voterId)) return false // Déjà voté
        
        val updatedRating = rating.copy(
            helpfulVotes = if (isHelpful) rating.helpfulVotes + 1 else rating.helpfulVotes,
            notHelpfulVotes = if (!isHelpful) rating.notHelpfulVotes + 1 else rating.notHelpfulVotes,
            votedByPlayerIds = rating.votedByPlayerIds + voterId
        )
        ratingsStore[ratingId] = updatedRating
        return true
    }
    
    override suspend fun getRating(ratingId: String): Rating? = ratingsStore[ratingId]

    override suspend fun getComments(gameId: String): List<Comment> = withContext(Dispatchers.IO) {
        commentService.getCommentsForGame(gameId).map {
            Comment(
                id = it.getId(),
                gameId = it.getGameId(),
                playerId = it.getPlayerId(),
                text = it.getText() ?: "",
                severity = when (it.getSeverity()) {
                    JavaComment.Severity.MEDIUM -> Severity.MEDIUM
                    JavaComment.Severity.HIGH -> Severity.HIGH
                    else -> Severity.LOW
                },
                routed = it.isRouted(),
                timestamp = it.getTimestamp()
            )
        }
    }

    override suspend fun addComment(comment: Comment): Boolean = withContext(Dispatchers.IO) {
        val jc = JavaComment()
        jc.setGameId(comment.gameId)
        jc.setPlayerId(comment.playerId)
        jc.setText(comment.text)
        jc.setTimestamp(comment.timestamp)
        val added = commentService.addComment(jc)
        added.isRouted()
    }

    override suspend fun filterByYear(year: Int?): List<Game> = withContext(Dispatchers.IO) {
        javaService.filterByYear(year).map { map(it) }
    }

    override suspend fun filterByGenre(genre: String?): List<Game> = withContext(Dispatchers.IO) {
        javaService.filterByGenre(genre).map { map(it) }
    }

    // ========== ACHATS ==========

    private fun mapPurchase(j: JavaPurchase): Purchase {
        return Purchase(
            purchaseId = j.getPurchaseId(),
            gameId = j.getGameId(),
            gameName = j.getGameName(),
            playerId = j.getPlayerId(),
            playerUsername = j.getPlayerUsername(),
            pricePaid = j.getPricePaid(),
            platform = j.getPlatform() ?: "",
            timestamp = j.getTimestamp(),
            isDlc = j.isDlc(),
            dlcId = j.getDlcId()
        )
    }

    override suspend fun purchaseGame(playerId: String, gameId: String): Purchase? = withContext(Dispatchers.IO) {
        purchaseService.purchaseGame(playerId, gameId)?.let { mapPurchase(it) }
    }

    override suspend fun purchaseDLC(playerId: String, dlcId: String): Purchase? = withContext(Dispatchers.IO) {
        purchaseService.purchaseDLC(playerId, dlcId)?.let { mapPurchase(it) }
    }

    override suspend fun getPurchasesByPlayer(playerId: String): List<Purchase> = withContext(Dispatchers.IO) {
        purchaseService.getPurchasesByPlayer(playerId).map { mapPurchase(it) }
    }

    override suspend fun getRecentPurchases(limit: Int): List<Purchase> = withContext(Dispatchers.IO) {
        purchaseService.getRecentPurchases(limit).map { mapPurchase(it) }
    }

    override suspend fun getPurchaseStats(): PurchaseStats = withContext(Dispatchers.IO) {
        PurchaseStats(
            totalPurchases = purchaseService.getTotalPurchaseCount(),
            totalRevenue = purchaseService.getTotalRevenue()
        )
    }

    // ========== DLC ==========

    private fun mapDLC(j: JavaDLC): DLC {
        return DLC(
            id = j.getId(),
            parentGameId = j.getParentGameId(),
            name = j.getName(),
            description = j.getDescription() ?: "",
            price = j.getPrice(),
            minGameVersion = j.getMinGameVersion(),
            releaseDate = j.getReleaseDate() ?: "",
            sizeInMB = j.getSizeInMB(),
            standalone = j.isStandalone()
        )
    }

    override suspend fun getDLCsForGame(gameId: String): List<DLC> = withContext(Dispatchers.IO) {
        dlcService.getDLCsForGame(gameId).map { mapDLC(it) }
    }

    override suspend fun getDLC(dlcId: String): DLC? = withContext(Dispatchers.IO) {
        dlcService.getDLCById(dlcId)?.let { mapDLC(it) }
    }

    override suspend fun canPurchaseDLC(playerId: String, dlcId: String): Boolean = withContext(Dispatchers.IO) {
        val player = playerService.getPlayerById(playerId) ?: return@withContext false
        val dlc = dlcService.getDLCById(dlcId) ?: return@withContext false
        
        // DLC standalone = toujours achetable
        if (dlc.isStandalone()) return@withContext true
        
        // Vérifier possession du jeu parent
        // Justification pédagogique : accesseurs de record Java (gameId() au lieu de getGameId())
        val ownsGame = player.getLibrary()?.any { it.gameId() == dlc.getParentGameId() } ?: false
        if (!ownsGame) return@withContext false
        
        // Vérifier version (on utilise 1.0.0 par défaut pour le moment)
        dlcService.canPlayerPurchaseDLC("1.0.0", dlcId)
    }

    override suspend fun getAllDLCs(): List<DLC> = withContext(Dispatchers.IO) {
        dlcService.getAllDLCs().map { mapDLC(it) }
    }

    // ========== PRIX AVANCÉS ==========

    override suspend fun getPrice(gameId: String): Double? = withContext(Dispatchers.IO) {
        priceService.getPrice(gameId)
    }

    override suspend fun getPriceFactors(gameId: String): PriceFactors? = withContext(Dispatchers.IO) {
        val factors = priceService.getPriceFactors(gameId)
        PriceFactors(
            basePrice = factors.basePrice,
            qualityFactor = factors.qualityFactor,
            demandFactor = factors.demandFactor,
            promotionFactor = factors.promotionFactor,
            finalPrice = factors.finalPrice
        )
    }

    override suspend fun isOnPromotion(gameId: String): Boolean = withContext(Dispatchers.IO) {
        priceService.isOnPromotion(gameId)
    }

    // ========== FILTRAGE PAR PLATEFORME ==========


    @Suppress("DEPRECATION")
    override suspend fun getPlatforms(): List<String> = withContext(Dispatchers.IO) {
        val rest = tryFetchCatalogFromRest() ?: return@withContext emptyList()
        return@withContext rest.mapNotNull { it.hardwareSupport }.distinct().sorted()
    }

    @Suppress("DEPRECATION")
    override suspend fun filterByPlatform(platform: String?): List<Game> = withContext(Dispatchers.IO) {
        val rest = tryFetchCatalogFromRest() ?: return@withContext emptyList()
        if (platform.isNullOrBlank()) return@withContext rest
        return@withContext rest.filter { it.hardwareSupport?.equals(platform, ignoreCase = true) == true }
    }

    // ========== PLATEFORMES DE DISTRIBUTION (NOUVEAU) ==========

    /**
     * Retourne toutes les plateformes de distribution connues.
     * 
     */
    override suspend fun getDistributionPlatforms(): List<DistributionPlatform> = withContext(Dispatchers.IO) {
        DistributionPlatform.getAllKnown()
    }

    /**
     * Filtre les jeux par plateforme de distribution.
     * 
     * La plateforme est inférée du support matériel pour les données CSV existantes.
     */
    override suspend fun filterByDistributionPlatform(platformId: String?): List<Game> = withContext(Dispatchers.IO) {
        val rest = tryFetchCatalogFromRest() ?: return@withContext emptyList()
        if (platformId.isNullOrBlank()) return@withContext rest
        return@withContext rest.filter { it.distributionPlatformId?.equals(platformId, ignoreCase = true) == true }
    }

    /**
     * Calcule les statistiques par plateforme de distribution.
     */
    override suspend fun getDistributionPlatformStats(): Map<DistributionPlatform, PlatformStats> = withContext(Dispatchers.IO) {
        // Compute stats from projection (catalog + players) only.
        val allGames = tryFetchCatalogFromRest() ?: emptyList()
        // Fetch players from REST projection
        val players = try {
            val client = HttpClient.newHttpClient()
            val req = HttpRequest.newBuilder().uri(URI.create("http://localhost:8080/api/players")).GET().build()
            val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() == 200) {
                val mapper = ObjectMapper()
                val nodes = mapper.readTree(resp.body())
                val out = mutableListOf<Player>()
                if (nodes.isArray) {
                    for (n in nodes) {
                        val id = n.get("id")?.asText() ?: continue
                        val username = n.get("username")?.asText() ?: ""
                        val reg = n.get("registrationDate")?.asText() ?: ""
                        out.add(Player(id = id, username = username, email = "", registrationDate = reg))
                    }
                }
                out
            } else emptyList()
        } catch (_: Exception) { emptyList() }

        DistributionPlatform.getAllKnown().associateWith { platform ->
            val platformGames = allGames.filter { it.distributionPlatformId == platform.id }
            val platformPlayers = players.filter { it.getDistributionPlatform().id == platform.id }
            PlatformStats(
                gameCount = platformGames.size,
                playerCount = platformPlayers.size,
                totalSales = platformGames.sumOf { it.salesGlobal ?: 0.0 },
                averageRating = null
            )
        }
    }

    // ========== SUPPORTS MATÉRIELS (CLARIFICATION) ==========

    /**
     * Retourne la liste des supports matériels distincts.
     * 
     */
    override suspend fun getHardwareSupports(): List<String> = withContext(Dispatchers.IO) {
        val rest = tryFetchCatalogFromRest() ?: return@withContext emptyList()
        return@withContext rest.mapNotNull { it.hardwareSupport }.distinct().sorted()
    }

    /**
     * Filtre les jeux par support matériel (hardware).
     */
    override suspend fun filterByHardwareSupport(hardwareCode: String?): List<Game> = withContext(Dispatchers.IO) {
        val rest = tryFetchCatalogFromRest() ?: return@withContext emptyList()
        if (hardwareCode.isNullOrBlank()) return@withContext rest
        return@withContext rest.filter { it.hardwareSupport?.equals(hardwareCode, ignoreCase = true) == true }
    }
}
