package org.example.services.kafka

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.example.model.*
import org.example.services.api.MockDataService
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * Service simulant des événements Kafka sans infrastructure Kafka réelle.
 *
 * Émet automatiquement des événements temps réel :
 * - PatchPublishedEvent : nouveaux patchs
 * - PriceUpdateEvent : changements de prix
 * - IncidentAggregatedEvent : agrégation d'incidents
 *
 * Architecture asynchrone basée sur Kotlin Flow pour la réactivité.
 * L'UI peut s'abonner aux flux pour recevoir les mises à jour en temps réel.
 */
class FakeKafkaService(
    private val mockDataService: MockDataService
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val today = LocalDate.now()

    // Flux d'événements (SharedFlow = plusieurs observateurs possibles)
    private val _patchEvents = MutableSharedFlow<PatchPublishedEvent>()
    val patchEvents: SharedFlow<PatchPublishedEvent> = _patchEvents.asSharedFlow()

    private val _priceEvents = MutableSharedFlow<PriceUpdateEvent>()
    val priceEvents: SharedFlow<PriceUpdateEvent> = _priceEvents.asSharedFlow()

    private val _incidentEvents = MutableSharedFlow<IncidentAggregatedEvent>()
    val incidentEvents: SharedFlow<IncidentAggregatedEvent> = _incidentEvents.asSharedFlow()

    // Nouveaux flux d'événements temps réel
    private val _ratingEvents = MutableSharedFlow<NewRatingEvent>()
    val ratingEvents: SharedFlow<NewRatingEvent> = _ratingEvents.asSharedFlow()

    private val _purchaseEvents = MutableSharedFlow<GamePurchaseEvent>()
    val purchaseEvents: SharedFlow<GamePurchaseEvent> = _purchaseEvents.asSharedFlow()

    private val _sessionEvents = MutableSharedFlow<GameSessionEvent>()
    val sessionEvents: SharedFlow<GameSessionEvent> = _sessionEvents.asSharedFlow()

    private val _crashEvents = MutableSharedFlow<CrashReportEvent>()
    val crashEvents: SharedFlow<CrashReportEvent> = _crashEvents.asSharedFlow()

    private val _playerPeakEvents = MutableSharedFlow<PlayerPeakEvent>()
    val playerPeakEvents: SharedFlow<PlayerPeakEvent> = _playerPeakEvents.asSharedFlow()

    private val _trendingEvents = MutableSharedFlow<GameTrendingEvent>()
    val trendingEvents: SharedFlow<GameTrendingEvent> = _trendingEvents.asSharedFlow()

    private var isRunning = false
    private var patchJob: Job? = null
    private var priceJob: Job? = null
    private var incidentJob: Job? = null
    private var ratingJob: Job? = null
    private var purchaseJob: Job? = null
    private var sessionJob: Job? = null
    private var crashJob: Job? = null
    private var playerPeakJob: Job? = null
    private var trendingJob: Job? = null

    // ========== CONTRÔLE DU SERVICE ==========

    /**
     * Démarre l'émission d'événements automatiques.
     *
     * @param patchIntervalSeconds Intervalle entre les événements de patch (défaut: 15s)
     * @param priceIntervalSeconds Intervalle entre les changements de prix (défaut: 10s)
     * @param incidentIntervalSeconds Intervalle entre les incidents (défaut: 20s)
     */
    fun start(
        patchIntervalSeconds: Long = 15,
        priceIntervalSeconds: Long = 10,
        incidentIntervalSeconds: Long = 20
    ) {
        if (isRunning) return

        isRunning = true

        // Lancer l'émission de patchs
        patchJob = scope.launch {
            while (isActive) {
                delay(patchIntervalSeconds * 1000)
                emitRandomPatchEvent()
            }
        }

        // Lancer l'émission de changements de prix
        priceJob = scope.launch {
            while (isActive) {
                delay(priceIntervalSeconds * 1000)
                emitRandomPriceEvent()
            }
        }

        // Lancer l'émission d'incidents
        incidentJob = scope.launch {
            while (isActive) {
                delay(incidentIntervalSeconds * 1000)
                emitRandomIncidentEvent()
            }
        }

        // Nouveaux événements
        ratingJob = scope.launch {
            while (isActive) {
                delay(8000) // Toutes les 8 secondes
                emitRandomRatingEvent()
            }
        }

        purchaseJob = scope.launch {
            while (isActive) {
                delay(12000) // Toutes les 12 secondes
                emitRandomPurchaseEvent()
            }
        }

        sessionJob = scope.launch {
            while (isActive) {
                delay(25000) // Toutes les 25 secondes
                emitRandomSessionEvent()
            }
        }

        crashJob = scope.launch {
            while (isActive) {
                delay(30000) // Toutes les 30 secondes
                emitRandomCrashEvent()
            }
        }

        playerPeakJob = scope.launch {
            while (isActive) {
                delay(45000) // Toutes les 45 secondes
                emitRandomPlayerPeakEvent()
            }
        }

        trendingJob = scope.launch {
            while (isActive) {
                delay(60000) // Toutes les 60 secondes
                emitRandomTrendingEvent()
            }
        }

        println("🚀 Service Kafka démarré - Tous les événements actifs")
    }

    /**
     * Arrête l'émission d'événements.
     */
    fun stop() {
        isRunning = false
        patchJob?.cancel()
        priceJob?.cancel()
        incidentJob?.cancel()
        ratingJob?.cancel()
        purchaseJob?.cancel()
        sessionJob?.cancel()
        crashJob?.cancel()
        playerPeakJob?.cancel()
        trendingJob?.cancel()

        println("⏹️ Service Kafka arrêté")
    }

    /**
     * Nettoie les ressources.
     */
    fun shutdown() {
        stop()
        scope.cancel()
        println("Service arrêté complètement")
    }

    // ========== ÉMISSION D'ÉVÉNEMENTS ==========

    /**
     * Émet un événement de publication de patch aléatoire.
     */
    private suspend fun emitRandomPatchEvent() {
        val game = mockDataService.getRandomGame() ?: return

        val currentVersion = game.currentVersion ?: "1.0.0"
        val newVersion = incrementVersion(currentVersion)
        val patchType = PatchType.values().random()

        val event = PatchPublishedEvent(
            gameId = game.id,
            gameName = game.name,
            platform = game.platform ?: "PC",
            oldVersion = currentVersion,
            newVersion = newVersion,
            changeLog = generateChangeLog(patchType),
            changes = generateChanges(patchType),
            timestamp = System.currentTimeMillis()
        )

        // Créer le patch correspondant
        val patch = Patch(
            id = "patch_${System.currentTimeMillis()}",
            gameId = event.gameId,
            gameName = event.gameName,
            platform = event.platform,
            oldVersion = event.oldVersion,
            newVersion = event.newVersion,
            type = patchType,
            description = event.changeLog,
            changes = event.changes,
            sizeInMB = Random.nextInt(50, 1500),
            releaseDate = today.format(dateFormatter),
            timestamp = event.timestamp
        )

        // Mettre à jour les données mock
        mockDataService.addPatch(patch)

        // Émettre l'événement
        _patchEvents.emit(event)

        println("PatchPublishedEvent: ${game.name} ${event.oldVersion} → ${event.newVersion}")
    }

    /**
     * Émet un événement de changement de prix aléatoire.
     */
    private suspend fun emitRandomPriceEvent() {
        val game = mockDataService.getRandomGame() ?: return
        val oldPrice = game.price ?: return

        // Ne pas changer le prix des jeux gratuits
        if (oldPrice == 0.0) return

        // Variation de prix : -30% à +20%
        val priceChangeFactor = Random.nextDouble(0.7, 1.2)
        val newPrice = (oldPrice * priceChangeFactor).coerceIn(9.99, 79.99)
        val roundedNewPrice = (newPrice * 100).toInt() / 100.0 // Arrondi à 2 décimales

        val reason = if (roundedNewPrice < oldPrice) {
            listOf(PriceChangeReason.PROMOTION, PriceChangeReason.GOOD_REVIEWS).random()
        } else {
            listOf(PriceChangeReason.HIGH_DEMAND, PriceChangeReason.QUALITY_IMPROVEMENT).random()
        }

        val event = PriceUpdateEvent(
            gameId = game.id,
            gameName = game.name,
            oldPrice = oldPrice,
            newPrice = roundedNewPrice,
            reason = reason,
            timestamp = System.currentTimeMillis()
        )

        // Mettre à jour les données mock
        mockDataService.updatePrice(game.id, roundedNewPrice)

        // Émettre l'événement
        _priceEvents.emit(event)

        val change = if (roundedNewPrice > oldPrice) "📈" else "📉"
        println("PriceUpdateEvent: ${game.name} ${oldPrice}€ → ${roundedNewPrice}€ $change")
    }

    /**
     * Émet un événement d'incident agrégé aléatoire.
     */
    private suspend fun emitRandomIncidentEvent() {
        val game = mockDataService.getRandomGame() ?: return

        val incidentCount = Random.nextInt(5, 50)
        val severity = Random.nextDouble(1.0, 5.0)

        val event = IncidentAggregatedEvent(
            gameId = game.id,
            gameName = game.name,
            platform = game.platform ?: "PC",
            incidentCount = incidentCount,
            averageSeverity = severity,
            timestamp = System.currentTimeMillis()
        )

        // Mettre à jour les données mock
        mockDataService.updateIncidents(game.id, incidentCount)

        // Émettre l'événement
        _incidentEvents.emit(event)

        println("IncidentEvent: ${game.name} +${incidentCount} incidents")
    }

    // ========== MÉTHODES UTILITAIRES ==========

    /**
     * Incrémente une version sémantique (ex: 1.2.3 -> 1.2.4).
     */
    private fun incrementVersion(version: String): String {
        val parts = version.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 1
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0

        return when (Random.nextInt(3)) {
            0 -> "$major.$minor.${patch + 1}" // Patch increment
            1 -> "$major.${minor + 1}.0"      // Minor increment
            else -> "${major + 1}.0.0"        // Major increment
        }
    }

    /**
     * Génère un changelog descriptif selon le type de patch.
     */
    private fun generateChangeLog(type: PatchType): String {
        return when (type) {
            PatchType.FIX -> "Corrections de bugs et améliorations de stabilité"
            PatchType.ADD -> "Ajout de nouvelles fonctionnalités et contenu"
            PatchType.OPTIMIZATION -> "Optimisations de performances et réduction de la consommation mémoire"
        }
    }

    /**
     * Génère une liste de changements détaillés.
     */
    private fun generateChanges(type: PatchType): List<Change> {
        return when (type) {
            PatchType.FIX -> listOf(
                Change(PatchType.FIX, "Correction de crashes au démarrage"),
                Change(PatchType.FIX, "Correction de bugs d'affichage"),
                Change(PatchType.FIX, "Correction de problèmes de sauvegarde"),
                Change(PatchType.OPTIMIZATION, "Amélioration de la stabilité générale")
            )
            PatchType.ADD -> listOf(
                Change(PatchType.ADD, "Nouveau contenu jouable"),
                Change(PatchType.ADD, "Nouvelles options de personnalisation"),
                Change(PatchType.ADD, "Support de nouvelles fonctionnalités"),
                Change(PatchType.OPTIMIZATION, "Optimisations réseau")
            )
            PatchType.OPTIMIZATION -> listOf(
                Change(PatchType.OPTIMIZATION, "Amélioration des performances CPU"),
                Change(PatchType.OPTIMIZATION, "Réduction de l'utilisation mémoire"),
                Change(PatchType.OPTIMIZATION, "Optimisation du chargement"),
                Change(PatchType.OPTIMIZATION, "Amélioration du framerate")
            )
        }.shuffled().take(Random.nextInt(2, 4))
    }

    // ========== MÉTHODES PUBLIQUES POUR TESTS MANUELS ==========

    /**
     * Force l'émission d'un événement de patch (utile pour les tests).
     */
    suspend fun emitPatchEventNow() {
        emitRandomPatchEvent()
    }

    /**
     * Force l'émission d'un événement de prix (utile pour les tests).
     */
    suspend fun emitPriceEventNow() {
        emitRandomPriceEvent()
    }

    /**
     * Force l'émission d'un événement d'incident (utile pour les tests).
     */
    suspend fun emitIncidentEventNow() {
        emitRandomIncidentEvent()
    }

    // ========== NOUVEAUX ÉVÉNEMENTS - MÉTHODES D'ÉMISSION ==========

    /**
     * Émet un événement de nouvelle évaluation (rating).
     */
    private suspend fun emitRandomRatingEvent() {
        val game = mockDataService.getRandomGame() ?: return
        val player = mockDataService.getRandomPlayer() ?: return

        val event = NewRatingEvent(
            gameId = game.id,
            gameName = game.name,
            playerId = player.id,
            playerUsername = player.username,
            rating = Random.nextInt(1, 6), // 1 à 5 étoiles
            comment = if (Random.nextBoolean()) generateRandomComment() else null,
            playtime = Random.nextInt(1, 500),
            isRecommended = Random.nextBoolean(),
            timestamp = System.currentTimeMillis()
        )

        _ratingEvents.emit(event)
        println("⭐ NewRatingEvent: ${player.username} a noté ${game.name} ${event.rating}/5")
    }

    /**
     * Émet un événement d'achat de jeu.
     */
    private suspend fun emitRandomPurchaseEvent() {
        val game = mockDataService.getRandomGame() ?: return
        val player = mockDataService.getRandomPlayer() ?: return
        val price = game.price ?: 29.99

        val event = GamePurchaseEvent(
            purchaseId = "purchase_${System.currentTimeMillis()}",
            gameId = game.id,
            gameName = game.name,
            playerId = player.id,
            playerUsername = player.username,
            pricePaid = price,
            platform = game.platform ?: "PC",
            region = SalesRegion.values().random(),
            timestamp = System.currentTimeMillis()
        )

        _purchaseEvents.emit(event)
        println("🛒 GamePurchaseEvent: ${player.username} a acheté ${game.name} (${event.pricePaid}€)")
    }

    /**
     * Émet un événement de session de jeu.
     */
    private suspend fun emitRandomSessionEvent() {
        val game = mockDataService.getRandomGame() ?: return
        val player = mockDataService.getRandomPlayer() ?: return

        val event = GameSessionEvent(
            sessionId = "session_${System.currentTimeMillis()}",
            gameId = game.id,
            gameName = game.name,
            playerId = player.id,
            playerUsername = player.username,
            sessionDuration = Random.nextInt(15, 300), // 15 min à 5h
            sessionType = SessionType.values().random(),
            timestamp = System.currentTimeMillis()
        )

        _sessionEvents.emit(event)
        println("🎮 GameSessionEvent: ${player.username} a joué ${event.sessionDuration}min à ${game.name}")
    }

    /**
     * Émet un événement de crash/incident.
     */
    private suspend fun emitRandomCrashEvent() {
        val game = mockDataService.getRandomGame() ?: return
        val player = mockDataService.getRandomPlayer() ?: return

        val event = CrashReportEvent(
            crashId = "crash_${System.currentTimeMillis()}",
            gameId = game.id,
            gameName = game.name,
            playerId = player.id,
            gameVersion = game.currentVersion ?: "1.0.0",
            platform = game.platform ?: "PC",
            severity = CrashSeverity.values().random(),
            errorType = listOf("NullPointerException", "OutOfMemoryError", "NetworkTimeout", "GraphicsError").random(),
            errorMessage = if (Random.nextBoolean()) "Erreur critique détectée" else null,
            timestamp = System.currentTimeMillis()
        )

        _crashEvents.emit(event)
        println("💥 CrashReportEvent: ${game.name} - ${event.severity} (${event.errorType})")
    }

    /**
     * Émet un événement de pic de joueurs.
     */
    private suspend fun emitRandomPlayerPeakEvent() {
        val game = mockDataService.getRandomGame() ?: return

        val event = PlayerPeakEvent(
            gameId = game.id,
            gameName = game.name,
            currentPlayers = Random.nextInt(1000, 50000),
            peakType = PeakType.values().random(),
            comparedToAverage = Random.nextDouble(-20.0, 150.0), // % variation
            timestamp = System.currentTimeMillis()
        )

        _playerPeakEvents.emit(event)
        println("📈 PlayerPeakEvent: ${game.name} - ${event.currentPlayers} joueurs (${event.peakType})")
    }

    /**
     * Émet un événement de tendance/popularité.
     */
    private suspend fun emitRandomTrendingEvent() {
        val game = mockDataService.getRandomGame() ?: return

        val trendType = TrendType.values().random()
        val messages = mapOf(
            TrendType.RISING_STAR to "${game.name} est en pleine ascension !",
            TrendType.TOP_SELLER to "${game.name} explose les ventes !",
            TrendType.HIGHLY_RATED to "${game.name} reçoit d'excellentes critiques !",
            TrendType.DECLINING to "${game.name} perd en popularité...",
            TrendType.CONTROVERSIAL to "${game.name} divise la communauté"
        )

        val event = GameTrendingEvent(
            gameId = game.id,
            gameName = game.name,
            trendType = trendType,
            metric = listOf("sales", "ratings", "players", "reviews").random(),
            changePercentage = Random.nextDouble(-30.0, 200.0),
            message = messages[trendType] ?: "Tendance détectée",
            timestamp = System.currentTimeMillis()
        )

        _trendingEvents.emit(event)
        println("🔥 GameTrendingEvent: ${event.message}")
    }

    /**
     * Génère un commentaire aléatoire pour les ratings.
     */
    private fun generateRandomComment(): String {
        val comments = listOf(
            "Excellent jeu, je recommande vivement !",
            "Très bon mais quelques bugs à corriger.",
            "Décevant, j'attendais mieux...",
            "Addictif ! Je ne peux plus m'arrêter de jouer.",
            "Bon rapport qualité/prix.",
            "Trop de bugs, injouable pour l'instant.",
            "Graphismes magnifiques, gameplay parfait !",
            "Pas terrible, je ne recommande pas.",
            "Sympa pour passer le temps.",
            "Chef-d'œuvre absolu !"
        )
        return comments.random()
    }
}
