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
     */
    suspend fun addRating(gameId: String, rating: Rating): Boolean

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
}

