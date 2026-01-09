package org.example.services.api

import kotlinx.coroutines.delay
import org.example.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * Implémentation mock du service de données.
 * Fournit des données fictives mais réalistes basées sur un dataset CSV de jeux vidéo.
 *
 * Architecture découplée : cette classe peut être remplacée par RealDataService
 * sans aucune modification de l'UI.
 *
 * Les données sont basées sur le dataset suivant :
 * Name, Platform, Year, Genre, Publisher, NA_Sales, EU_Sales, JP_Sales, Other_Sales, Global_Sales
 */
class MockDataService : DataService {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val today = LocalDate.now()

    // Données mutables pour permettre les mises à jour temps réel
    private val games = mutableListOf<Game>()
    private val publishers = mutableListOf<Publisher>()
    private val players = mutableListOf<Player>()
    private val patches = mutableListOf<Patch>()

    init {
        initializeMockData()
    }

    // ========== INITIALISATION DES DONNÉES MOCK ==========

    /**
     * Initialise toutes les données mock à partir d'un dataset réaliste.
     */
    private fun initializeMockData() {
        // Dataset inspiré du CSV de jeux vidéo réels
        val rawGamesData = listOf(
            RawGame("Wii Sports", "Wii", 2006, "Sports", "Nintendo", 41.49, 29.02, 3.77, 8.46, 82.74),
            RawGame("Super Mario Bros.", "NES", 1985, "Platform", "Nintendo", 29.08, 3.58, 6.81, 0.77, 40.24),
            RawGame("Mario Kart Wii", "Wii", 2008, "Racing", "Nintendo", 15.85, 12.88, 3.79, 3.31, 35.82),
            RawGame("Wii Sports Resort", "Wii", 2009, "Sports", "Nintendo", 15.75, 11.01, 3.28, 2.96, 33.00),
            RawGame("Pokemon Red/Pokemon Blue", "GB", 1996, "Role-Playing", "Nintendo", 11.27, 8.89, 10.22, 1.00, 31.37),
            RawGame("Tetris", "GB", 1989, "Puzzle", "Nintendo", 23.20, 2.26, 4.22, 0.58, 30.26),
            RawGame("New Super Mario Bros.", "DS", 2006, "Platform", "Nintendo", 11.38, 9.23, 6.50, 2.90, 30.01),
            RawGame("Wii Play", "Wii", 2006, "Misc", "Nintendo", 14.03, 9.20, 2.93, 2.85, 29.02),
            RawGame("Duck Hunt", "NES", 1984, "Shooter", "Nintendo", 26.93, 0.63, 0.28, 0.47, 28.31),
            RawGame("Grand Theft Auto V", "PS3", 2013, "Action", "Rockstar Games", 7.01, 9.27, 0.97, 4.14, 21.40),
            RawGame("Grand Theft Auto: San Andreas", "PS2", 2004, "Action", "Rockstar Games", 9.43, 0.40, 0.41, 10.57, 20.81),
            RawGame("Super Mario World", "SNES", 1990, "Platform", "Nintendo", 12.78, 3.75, 3.54, 0.55, 20.61),
            RawGame("Pokemon Gold/Pokemon Silver", "GB", 1999, "Role-Playing", "Nintendo", 9.00, 6.18, 7.20, 0.71, 23.10),
            RawGame("Wii Fit", "Wii", 2007, "Sports", "Nintendo", 8.94, 8.03, 3.60, 2.15, 22.72),
            RawGame("Call of Duty: Modern Warfare 3", "X360", 2011, "Shooter", "Activision", 9.03, 4.28, 0.13, 1.32, 14.76),
            RawGame("Call of Duty: Black Ops", "X360", 2010, "Shooter", "Activision", 9.67, 3.73, 0.11, 1.13, 14.64),
            RawGame("Call of Duty: Black Ops II", "X360", 2012, "Shooter", "Activision", 4.99, 5.88, 0.65, 0.81, 13.73),
            RawGame("The Witcher 3: Wild Hunt", "PC", 2015, "Role-Playing", "CD Projekt", 2.34, 5.67, 0.45, 3.21, 11.67),
            RawGame("Cyberpunk 2077", "PC", 2020, "Role-Playing", "CD Projekt", 3.45, 4.23, 0.67, 2.11, 10.46),
            RawGame("Elden Ring", "PC", 2022, "Action", "FromSoftware", 4.56, 5.12, 1.89, 2.34, 13.91),
            RawGame("Dark Souls III", "PC", 2016, "Action", "FromSoftware", 1.23, 2.34, 1.56, 0.98, 6.11),
            RawGame("Fortnite", "PC", 2017, "Shooter", "Epic Games", 12.34, 8.90, 2.45, 5.67, 29.36),
            RawGame("League of Legends", "PC", 2009, "Strategy", "Riot Games", 8.90, 6.78, 3.45, 4.56, 23.69),
            RawGame("VALORANT", "PC", 2020, "Shooter", "Riot Games", 5.67, 4.23, 2.12, 3.45, 15.47),
            RawGame("Counter-Strike 2", "PC", 2023, "Shooter", "Valve", 6.78, 7.89, 1.23, 4.56, 20.46),
            RawGame("Dota 2", "PC", 2013, "Strategy", "Valve", 4.56, 5.67, 2.34, 3.45, 16.02),
            RawGame("Starfield", "PC", 2023, "Role-Playing", "Bethesda", 5.67, 4.23, 0.89, 2.34, 13.13),
            RawGame("The Elder Scrolls V: Skyrim", "PC", 2011, "Role-Playing", "Bethesda", 6.78, 5.45, 1.23, 3.21, 16.67),
            RawGame("Fallout 4", "PC", 2015, "Role-Playing", "Bethesda", 5.45, 4.32, 0.98, 2.67, 13.42),
            RawGame("Red Dead Redemption 2", "PC", 2019, "Action", "Rockstar Games", 6.89, 7.23, 1.12, 3.45, 18.69),
            RawGame("Minecraft", "PC", 2011, "Sandbox", "Mojang", 15.67, 12.34, 5.67, 8.90, 42.58),
            RawGame("Terraria", "PC", 2011, "Sandbox", "Re-Logic", 3.45, 4.56, 1.23, 2.34, 11.58),
            RawGame("Stardew Valley", "PC", 2016, "Simulation", "ConcernedApe", 2.34, 3.45, 0.89, 1.67, 8.35),
            RawGame("Hades", "PC", 2020, "Action", "Supergiant", 1.89, 2.45, 0.67, 1.23, 6.24),
            RawGame("Baldur's Gate 3", "PC", 2023, "Role-Playing", "Larian", 5.67, 6.78, 1.45, 3.89, 17.79),
            RawGame("Palworld", "PC", 2024, "Survival", "Pocketpair", 8.90, 5.67, 3.45, 4.23, 22.25),
            RawGame("Assassin's Creed Valhalla", "PC", 2020, "Action", "Ubisoft", 4.23, 5.67, 0.89, 2.45, 13.24),
            RawGame("Rainbow Six Siege", "PC", 2015, "Shooter", "Ubisoft", 3.45, 4.56, 1.23, 2.34, 11.58),
            RawGame("Far Cry 6", "PC", 2021, "Shooter", "Ubisoft", 3.21, 4.12, 0.78, 1.98, 10.09)
        )

        // Créer les éditeurs à partir des jeux
        createPublishers(rawGamesData)

        // Créer les jeux
        createGames(rawGamesData)

        // Créer les patchs
        createPatches()

        // Créer les joueurs
        createPlayers()
    }

    /**
     * Crée les éditeurs à partir des données brutes.
     */
    private fun createPublishers(rawGames: List<RawGame>) {
        val publisherNames = rawGames.map { it.publisher }.distinct()

        publisherNames.forEach { publisherName ->
            val publisherGames = rawGames.filter { it.publisher == publisherName }
            val id = publisherName.lowercase().replace(" ", "_")

            publishers.add(
                Publisher(
                    id = id,
                    name = publisherName,
                    foundedYear = publisherGames.minOfOrNull { it.year } ?: 2000,
                    platforms = publisherGames.map { it.platform }.distinct(),
                    genres = publisherGames.map { it.genre }.distinct(),
                    gamesPublished = publisherGames.size,
                    activeGames = publisherGames.count { it.year >= 2015 },
                    totalIncidents = Random.nextInt(50, 500),
                    averageRating = Random.nextDouble(3.5, 4.9)
                )
            )
        }
    }

    /**
     * Crée les jeux à partir des données brutes.
     */
    private fun createGames(rawGames: List<RawGame>) {
        rawGames.forEachIndexed { index, raw ->
            val publisherId = raw.publisher.lowercase().replace(" ", "_")
            val gameId = "game_${index + 1}"

            // Calcul du prix basé sur la popularité et l'année
            val price = calculatePrice(raw)

            // Calcul de la note basée sur les ventes et la qualité
            val rating = calculateRating(raw)

            // Version basée sur l'année
            val version = calculateVersion(raw.year)

            // Nombre d'incidents basé sur l'âge du jeu
            val incidents = calculateIncidents(raw.year)

            games.add(
                Game(
                    id = gameId,
                    name = raw.name,
                    platform = raw.platform,
                    genre = raw.genre,
                    publisherId = publisherId,
                    publisherName = raw.publisher,
                    releaseYear = raw.year,
                    currentVersion = version,
                    price = price,
                    averageRating = rating,
                    incidentCount = incidents,
                    salesNA = raw.salesNA,
                    salesEU = raw.salesEU,
                    salesJP = raw.salesJP,
                    salesOther = raw.salesOther,
                    salesGlobal = raw.salesGlobal,
                    description = generateDescription(raw),
                    versions = generateVersionHistory(version, raw.year),
                    incidents = generateIncidentHistory(incidents),
                    ratings = generateRatings(gameId, rating)
                )
            )
        }
    }

    /**
     * Crée l'historique de patchs.
     */
    private fun createPatches() {
        // Créer des patchs récents pour les jeux post-2015
        val recentGames = games.filter { (it.releaseYear ?: 0) >= 2015 }

        recentGames.take(15).forEach { game ->
            val currentVersion = game.currentVersion ?: "1.0.0"
            val parts = currentVersion.split(".")
            val major = parts.getOrNull(0)?.toIntOrNull() ?: 1
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0

            // Patch récent (correction)
            if (patch > 0) {
                val oldVersion = "$major.$minor.${patch - 1}"
                patches.add(
                    Patch(
                        id = "patch_${patches.size + 1}",
                        gameId = game.id,
                        gameName = game.name,
                        platform = game.platform ?: "PC",
                        oldVersion = oldVersion,
                        newVersion = currentVersion,
                        type = PatchType.FIX,
                        description = "Corrections de bugs et améliorations de stabilité",
                        changes = listOf(
                            Change(PatchType.FIX, "Correction de crashes au démarrage"),
                            Change(PatchType.FIX, "Correction de bugs d'affichage"),
                            Change(PatchType.OPTIMIZATION, "Amélioration des performances")
                        ),
                        sizeInMB = Random.nextInt(50, 500),
                        releaseDate = today.minusDays(Random.nextLong(1, 30)).format(dateFormatter)
                    )
                )
            }

            // Patch moyen (ajouts)
            if (minor > 0) {
                val oldVersion = "$major.${minor - 1}.0"
                val newVersion = "$major.$minor.0"
                patches.add(
                    Patch(
                        id = "patch_${patches.size + 1}",
                        gameId = game.id,
                        gameName = game.name,
                        platform = game.platform ?: "PC",
                        oldVersion = oldVersion,
                        newVersion = newVersion,
                        type = PatchType.ADD,
                        description = "Ajout de nouvelles fonctionnalités",
                        changes = listOf(
                            Change(PatchType.ADD, "Nouveau contenu jouable"),
                            Change(PatchType.ADD, "Nouvelles options graphiques"),
                            Change(PatchType.OPTIMIZATION, "Optimisations réseau")
                        ),
                        sizeInMB = Random.nextInt(500, 2000),
                        releaseDate = today.minusDays(Random.nextLong(30, 120)).format(dateFormatter)
                    )
                )
            }
        }
    }

    /**
     * Crée des joueurs fictifs.
     */
    private fun createPlayers() {
        val usernames = listOf("DragonSlayer92", "NightHawk_Pro", "CasualGamer2024", "RPG_Master", "SpeedRunner_X")

        usernames.forEachIndexed { index, username ->
            val yearsAgo = Random.nextInt(1, 8)
            val gamesOwned = games.shuffled().take(Random.nextInt(3, 10))

            players.add(
                Player(
                    id = "player_${index + 1}",
                    username = username,
                    email = "${username.lowercase()}@example.com",
                    registrationDate = today.minusYears(yearsAgo.toLong()).format(dateFormatter),
                    totalPlaytime = Random.nextInt(100, 3000),
                    lastEvaluationDate = today.minusDays(Random.nextLong(1, 30)).format(dateFormatter),
                    evaluationsCount = Random.nextInt(5, 50),
                    library = gamesOwned.map { game ->
                        GameOwnership(
                            gameId = game.id,
                            gameName = game.name,
                            purchaseDate = today.minusDays(Random.nextLong(30, 1000)).format(dateFormatter),
                            playtime = Random.nextInt(5, 500),
                            lastPlayed = today.minusDays(Random.nextLong(0, 60)).format(dateFormatter),
                            pricePaid = game.price ?: 0.0
                        )
                    }
                )
            )
        }
    }

    // ========== MÉTHODES UTILITAIRES ==========

    private fun calculatePrice(raw: RawGame): Double {
        val basePrice = when {
            raw.year >= 2023 -> 69.99
            raw.year >= 2020 -> 59.99
            raw.year >= 2015 -> 39.99
            raw.year >= 2010 -> 29.99
            raw.year >= 2000 -> 19.99
            else -> 9.99
        }

        // Ajustement basé sur la popularité
        val popularityFactor = (raw.salesGlobal / 50.0).coerceIn(0.5, 2.0)

        // Jeux gratuits
        if (raw.name in listOf("Fortnite", "League of Legends", "VALORANT", "Counter-Strike 2", "Dota 2")) {
            return 0.0
        }

        return (basePrice * popularityFactor).coerceIn(9.99, 79.99)
    }

    private fun calculateRating(raw: RawGame): Double {
        // Note basée sur les ventes globales
        val salesScore = (raw.salesGlobal / 10.0).coerceIn(3.0, 5.0)
        return (salesScore * 10).toInt() / 10.0 // Arrondi à 1 décimale
    }

    private fun calculateVersion(year: Int): String {
        val yearsOld = 2025 - year
        return when {
            year >= 2024 -> "0.${Random.nextInt(5, 9)}.${Random.nextInt(0, 5)}" // Early Access
            year >= 2020 -> "${Random.nextInt(1, 3)}.${Random.nextInt(5, 20)}.${Random.nextInt(0, 10)}"
            year >= 2015 -> "${Random.nextInt(1, 2)}.${Random.nextInt(0, 10)}.${Random.nextInt(0, 5)}"
            else -> "1.0.0"
        }
    }

    private fun calculateIncidents(year: Int): Int {
        val yearsOld = 2025 - year
        return when {
            year >= 2023 -> Random.nextInt(50, 300) // Jeux récents = plus de bugs
            year >= 2020 -> Random.nextInt(20, 150)
            year >= 2015 -> Random.nextInt(10, 80)
            else -> Random.nextInt(0, 30)
        }
    }

    private fun generateDescription(raw: RawGame): String {
        return when (raw.genre) {
            "Action" -> "${raw.name} est un jeu d'action palpitant développé par ${raw.publisher}."
            "Role-Playing" -> "${raw.name} est un RPG immersif qui vous plonge dans un univers riche."
            "Shooter" -> "${raw.name} est un FPS compétitif de référence dans le monde du gaming."
            "Sports" -> "${raw.name} offre une expérience sportive réaliste et accessible."
            "Strategy" -> "${raw.name} est un jeu de stratégie exigeant réflexion et tactique."
            "Platform" -> "${raw.name} est un jeu de plateforme culte de ${raw.publisher}."
            "Racing" -> "${raw.name} propose des courses endiablées et un gameplay arcade."
            "Sandbox" -> "${raw.name} offre une liberté créative totale dans un monde ouvert."
            "Simulation" -> "${raw.name} est une simulation détaillée et relaxante."
            "Survival" -> "${raw.name} vous met au défi de survivre dans un environnement hostile."
            else -> "${raw.name} est un excellent jeu développé par ${raw.publisher}."
        }
    }

    private fun generateVersionHistory(currentVersion: String, year: Int): List<GameVersion> {
        if (year < 2015) return emptyList()

        val history = mutableListOf<GameVersion>()
        val parts = currentVersion.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 1
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0

        // Version actuelle
        history.add(GameVersion(
            currentVersion,
            "Version actuelle - Dernières améliorations",
            today.minusDays(Random.nextLong(1, 15)).format(dateFormatter)
        ))

        // Version précédente
        if (minor > 0) {
            history.add(GameVersion(
                "$major.${minor - 1}.0",
                "Mise à jour majeure - Nouveau contenu",
                today.minusDays(Random.nextLong(30, 90)).format(dateFormatter)
            ))
        }

        return history
    }

    private fun generateIncidentHistory(totalIncidents: Int): List<Incident> {
        if (totalIncidents == 0) return emptyList()

        return listOf(
            Incident(today.minusDays(2).format(dateFormatter), totalIncidents / 10),
            Incident(today.minusDays(7).format(dateFormatter), totalIncidents / 5),
            Incident(today.minusDays(14).format(dateFormatter), totalIncidents / 3)
        )
    }

    private fun generateRatings(gameId: String, avgRating: Double): List<Rating> {
        val usernames = listOf("GamerPro", "NightOwl", "CasualPlayer", "HardcoreGamer", "RPGFan")
        val ratings = mutableListOf<Rating>()

        repeat(Random.nextInt(5, 10)) {
            val rating = (avgRating + Random.nextDouble(-0.5, 0.5)).coerceIn(1.0, 5.0).toInt()
            ratings.add(
                Rating(
                    username = usernames.random(),
                    rating = rating,
                    comment = when (rating) {
                        5 -> "Excellent jeu !"
                        4 -> "Très bon, je recommande"
                        3 -> "Correct mais perfectible"
                        2 -> "Décevant"
                        else -> "À éviter"
                    },
                    date = today.minusDays(Random.nextLong(1, 90)).format(dateFormatter)
                )
            )
        }

        return ratings
    }

    // ========== SIMULATION LATENCE RÉSEAU ==========

    private suspend fun simulateNetworkDelay() {
        delay(Random.nextLong(100, 300))
    }

    // ========== IMPLÉMENTATION DE L'INTERFACE ==========

    override suspend fun getCatalog(): List<Game> {
        simulateNetworkDelay()
        return games.toList()
    }

    override suspend fun getGame(gameId: String): Game? {
        simulateNetworkDelay()
        return games.find { it.id == gameId }
    }

    override suspend fun searchGames(query: String): List<Game> {
        simulateNetworkDelay()
        return games.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.genre?.contains(query, ignoreCase = true) == true ||
            it.publisherName?.contains(query, ignoreCase = true) == true
        }
    }

    override suspend fun getGamesByPublisher(publisherId: String): List<Game> {
        simulateNetworkDelay()
        return games.filter { it.publisherId == publisherId }
    }

    override suspend fun getPublishers(): List<Publisher> {
        simulateNetworkDelay()
        return publishers.toList()
    }

    override suspend fun getPublisher(publisherId: String): Publisher? {
        simulateNetworkDelay()
        return publishers.find { it.id == publisherId }
    }

    override suspend fun getPlayers(): List<Player> {
        simulateNetworkDelay()
        return players.toList()
    }

    override suspend fun getPlayer(playerId: String): Player? {
        simulateNetworkDelay()
        return players.find { it.id == playerId }
    }

    override suspend fun getAllPatches(): List<Patch> {
        simulateNetworkDelay()
        return patches.sortedByDescending { it.timestamp }
    }

    override suspend fun getPatchesByGame(gameId: String): List<Patch> {
        simulateNetworkDelay()
        return patches.filter { it.gameId == gameId }.sortedByDescending { it.timestamp }
    }

    override suspend fun getRecentPatches(limit: Int): List<Patch> {
        simulateNetworkDelay()
        return patches.sortedByDescending { it.timestamp }.take(limit)
    }

    override suspend fun getRatings(gameId: String): List<Rating> {
        simulateNetworkDelay()
        return games.find { it.id == gameId }?.ratings ?: emptyList()
    }

    override suspend fun addRating(gameId: String, rating: Rating): Boolean {
        simulateNetworkDelay()

        // Trouver le jeu et ajouter l'évaluation
        val gameIndex = games.indexOfFirst { it.id == gameId }
        if (gameIndex != -1) {
            val game = games[gameIndex]
            val currentRatings = game.ratings?.toMutableList() ?: mutableListOf()
            currentRatings.add(0, rating) // Ajouter en première position

            // Recalculer la note moyenne
            val newAverage = currentRatings.map { it.rating }.average()

            // Mettre à jour le jeu
            games[gameIndex] = game.copy(
                ratings = currentRatings,
                averageRating = newAverage
            )
            return true
        }

        return false
    }

    override suspend fun getPrice(gameId: String): Double? {
        simulateNetworkDelay()
        return games.find { it.id == gameId }?.price
    }

    // ========== MÉTHODES POUR MISE À JOUR DYNAMIQUE (KAFKA SIMULATION) ==========

    /**
     * Ajoute un nouveau patch (appelé par FakeKafkaService).
     */
    fun addPatch(patch: Patch) {
        patches.add(0, patch)

        // Met à jour la version du jeu
        val gameIndex = games.indexOfFirst { it.id == patch.gameId }
        if (gameIndex != -1) {
            val game = games[gameIndex]
            games[gameIndex] = game.copy(currentVersion = patch.newVersion)
        }
    }

    /**
     * Met à jour le prix d'un jeu (appelé par FakeKafkaService).
     */
    fun updatePrice(gameId: String, newPrice: Double) {
        val gameIndex = games.indexOfFirst { it.id == gameId }
        if (gameIndex != -1) {
            val game = games[gameIndex]
            games[gameIndex] = game.copy(price = newPrice)
        }
    }

    /**
     * Met à jour le compteur d'incidents (appelé par FakeKafkaService).
     */
    fun updateIncidents(gameId: String, additionalIncidents: Int) {
        val gameIndex = games.indexOfFirst { it.id == gameId }
        if (gameIndex != -1) {
            val game = games[gameIndex]
            val newCount = (game.incidentCount ?: 0) + additionalIncidents
            games[gameIndex] = game.copy(incidentCount = newCount)
        }
    }

    /**
     * Récupère un jeu aléatoire (pour simulation d'événements).
     */
    fun getRandomGame(): Game? = games.randomOrNull()

    /**
     * Récupère un joueur aléatoire (pour simulation d'événements joueurs).
     */
    fun getRandomPlayer(): Player? = players.randomOrNull()
}

/**
 * Classe de données brute pour parser le CSV.
 */
private data class RawGame(
    val name: String,
    val platform: String,
    val year: Int,
    val genre: String,
    val publisher: String,
    val salesNA: Double,
    val salesEU: Double,
    val salesJP: Double,
    val salesOther: Double,
    val salesGlobal: Double
)

