package org.example.services.api

import org.example.model.*

/**
 * Interface abstraite pour les services de données.
 * Permet de basculer facilement entre mock et implémentation réelle.
 *
 * Architecture découplée : l'UI ne dépend que de cette interface,
 * l'implémentation (mock ou réelle) peut changer sans impact.
 */
interface DataService {

    // ========== JEUX ==========

    /**
     * Récupère le catalogue complet de jeux.
     */
    suspend fun getCatalog(): List<Game>

    /**
     * Récupère un jeu spécifique par son ID.
     */
    suspend fun getGame(gameId: String): Game?

    /**
     * Recherche des jeux par nom, genre ou éditeur.
     */
    suspend fun searchGames(query: String): List<Game>

    /**
     * Récupère les jeux par éditeur.
     */
    suspend fun getGamesByPublisher(publisherId: String): List<Game>

    // ========== ÉDITEURS ==========

    /**
     * Récupère la liste de tous les éditeurs.
     */
    suspend fun getPublishers(): List<Publisher>

    /**
     * Récupère un éditeur spécifique.
     */
    suspend fun getPublisher(publisherId: String): Publisher?

    // ========== JOUEURS ==========

    /**
     * Récupère la liste des joueurs.
     */
    suspend fun getPlayers(): List<Player>

    /**
     * Récupère un joueur spécifique.
     */
    suspend fun getPlayer(playerId: String): Player?

    // ========== PATCHS ==========

    /**
     * Récupère tous les patchs (historique complet).
     */
    suspend fun getAllPatches(): List<Patch>

    /**
     * Récupère les patchs d'un jeu spécifique.
     */
    suspend fun getPatchesByGame(gameId: String): List<Patch>

    /**
     * Récupère les patchs récents (ex: dernières 24h).
     */
    suspend fun getRecentPatches(limit: Int = 10): List<Patch>

    // ========== ÉVALUATIONS ==========

    /**
     * Récupère les évaluations d'un jeu.
     */
    suspend fun getRatings(gameId: String): List<Rating>

    /**
     * Ajoute une nouvelle évaluation.
     * Le joueur doit posséder le jeu et avoir suffisamment joué.
     */
    suspend fun addRating(gameId: String, rating: Rating): Boolean

    /**
     * Vote sur l'utilité d'une évaluation.
     * @param ratingId ID de l'évaluation
     * @param voterId ID du joueur qui vote
     * @param isHelpful true = utile, false = pas utile
     * @return true si le vote a été enregistré, false si le joueur a déjà voté
     */
    suspend fun voteOnRating(ratingId: String, voterId: String, isHelpful: Boolean): Boolean

    /**
     * Récupère une évaluation par son ID.
     */
    suspend fun getRating(ratingId: String): Rating?

    // ========== COMMENTAIRES / REPORTS ==========

    /**
     * Récupère les commentaires d'un jeu.
     */
    suspend fun getComments(gameId: String): List<Comment>

    /**
     * Ajoute un commentaire et retourne s'il a été routé.
     */
    suspend fun addComment(comment: Comment): Boolean

    // ========== PRIX ==========

    /**
     * Récupère le prix actuel d'un jeu.
     */
    suspend fun getPrice(gameId: String): Double?
    
    // ========== UTILITAIRES / FILTRAGE ==========

    /**
     * Filtre les jeux par année. Si `year` est null retourne tout le catalogue.
     */
    suspend fun filterByYear(year: Int?): List<Game>

    /**
     * Filtre les jeux par genre (sous-chaîne, insensible à la casse).
     * Si `genre` est null ou vide retourne tout le catalogue.
     */
    suspend fun filterByGenre(genre: String?): List<Game>

    /**
     * Retourne les `n` jeux ayant les plus grandes ventes globales.
     */
    
    // ========== ACHATS ==========

    /**
     * Effectue l'achat d'un jeu par un joueur.
     * @return L'achat créé, ou null si impossible (jeu non trouvé, déjà possédé, etc.)
     */
    suspend fun purchaseGame(playerId: String, gameId: String): Purchase?

    /**
     * Effectue l'achat d'un DLC par un joueur.
     * Vérifie possession du jeu parent et compatibilité de version.
     */
    suspend fun purchaseDLC(playerId: String, dlcId: String): Purchase?

    /**
     * Récupère tous les achats d'un joueur.
     */
    suspend fun getPurchasesByPlayer(playerId: String): List<Purchase>

    /**
     * Récupère les achats récents.
     */
    suspend fun getRecentPurchases(limit: Int = 10): List<Purchase>

    /**
     * Récupère les statistiques d'achats.
     */
    suspend fun getPurchaseStats(): PurchaseStats

    // ========== DLC ==========

    /**
     * Récupère tous les DLC pour un jeu.
     */
    suspend fun getDLCsForGame(gameId: String): List<DLC>

    /**
     * Récupère un DLC par son ID.
     */
    suspend fun getDLC(dlcId: String): DLC?

    /**
     * Vérifie si un joueur peut acheter un DLC (version compatible).
     */
    suspend fun canPurchaseDLC(playerId: String, dlcId: String): Boolean

    /**
     * Récupère tous les DLC.
     */
    suspend fun getAllDLCs(): List<DLC>

    // ========== PRIX AVANCÉS ==========

    /**
     * Récupère les facteurs de prix pour un jeu.
     */
    suspend fun getPriceFactors(gameId: String): PriceFactors?

    /**
     * Vérifie si un jeu est en promotion.
     */
    suspend fun isOnPromotion(gameId: String): Boolean

    // ========== FILTRAGE PAR PLATEFORME ==========

    /**
     * Récupère la liste des plateformes disponibles.
     */
    suspend fun getPlatforms(): List<String>

    /**
     * Filtre les jeux par plateforme.
     */
    suspend fun filterByPlatform(platform: String?): List<Game>
}

