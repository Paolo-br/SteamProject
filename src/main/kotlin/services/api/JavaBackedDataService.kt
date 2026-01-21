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
                    pricePaid = it.pricePaid() ?: 0.0
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
        publisherService.getAllPublishers().map { mapPublisher(it) }
    }

    override suspend fun getPublisher(publisherId: String): Publisher? = withContext(Dispatchers.IO) {
        getPublishers().firstOrNull { it.id == publisherId }
    }

    override suspend fun getPlayers(): List<Player> = withContext(Dispatchers.IO) {
        playerService.getAllPlayers().map { mapPlayer(it) }
    }

    override suspend fun getPlayer(playerId: String): Player? = withContext(Dispatchers.IO) {
        playerService.getPlayerById(playerId)?.let { mapPlayer(it) }
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
    override suspend fun addRating(gameId: String, rating: Rating): Boolean = false
    
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
        javaService.getAll()
            .mapNotNull { it.getPlatform() }
            .distinct()
            .sorted()
    }

    @Suppress("DEPRECATION")
    override suspend fun filterByPlatform(platform: String?): List<Game> = withContext(Dispatchers.IO) {
        if (platform.isNullOrBlank()) {
            javaService.getAll().map { map(it) }
        } else {
            javaService.getAll()
                .filter { it.getPlatform()?.equals(platform, ignoreCase = true) == true }
                .map { map(it) }
        }
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
        if (platformId.isNullOrBlank()) {
            javaService.getAll().map { map(it) }
        } else {
            javaService.getAll()
                .map { map(it) }
                .filter { it.distributionPlatformId?.equals(platformId, ignoreCase = true) == true }
        }
    }

    /**
     * Calcule les statistiques par plateforme de distribution.
     */
    override suspend fun getDistributionPlatformStats(): Map<DistributionPlatform, PlatformStats> = withContext(Dispatchers.IO) {
        val allGames = javaService.getAll().map { map(it) }
        val allPlayers = playerService.getAllPlayers()
        
        DistributionPlatform.getAllKnown().associateWith { platform ->
            val platformGames = allGames.filter { it.distributionPlatformId == platform.id }
            val platformPlayers = allPlayers.filter { 
                it.getId().hashCode() % DistributionPlatform.getAllKnown().size == 
                    DistributionPlatform.getAllKnown().indexOf(platform)
            }
            
            PlatformStats(
                gameCount = platformGames.size,
                playerCount = platformPlayers.size,
                totalSales = platformGames.sumOf { it.salesGlobal ?: 0.0 },
                averageRating = null // À calculer avec les vraies évaluations
            )
        }
    }

    // ========== SUPPORTS MATÉRIELS (CLARIFICATION) ==========

    /**
     * Retourne la liste des supports matériels distincts.
     * 
     */
    override suspend fun getHardwareSupports(): List<String> = withContext(Dispatchers.IO) {
        javaService.getAll()
            .mapNotNull { it.getPlatform() }
            .distinct()
            .sorted()
    }

    /**
     * Filtre les jeux par support matériel (hardware).
     */
    override suspend fun filterByHardwareSupport(hardwareCode: String?): List<Game> = withContext(Dispatchers.IO) {
        if (hardwareCode.isNullOrBlank()) {
            javaService.getAll().map { map(it) }
        } else {
            javaService.getAll()
                .filter { it.getPlatform()?.equals(hardwareCode, ignoreCase = true) == true }
                .map { map(it) }
        }
    }
}
