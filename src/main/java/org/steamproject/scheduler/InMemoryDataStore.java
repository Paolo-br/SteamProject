package org.steamproject.scheduler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stockage thread-safe des jeux, joueurs et éditeurs créés.
 * Permet aux producteurs dépendants de récupérer des entités existantes.
 */
public class InMemoryDataStore {

    // CopyOnWriteArrayList = thread-safe pour lectures fréquentes
    private final List<GameInfo> games = new CopyOnWriteArrayList<>();
    private final List<PlayerInfo> players = new CopyOnWriteArrayList<>();
    private final List<PurchaseInfo> purchases = new CopyOnWriteArrayList<>();
    private final List<PublisherInfo> publishers = new CopyOnWriteArrayList<>();
    private final List<ReviewInfo> reviews = new CopyOnWriteArrayList<>();
    private final List<DlcInfo> dlcs = new CopyOnWriteArrayList<>();
    private final List<DlcPurchaseInfo> dlcPurchases = new CopyOnWriteArrayList<>();
    
    // Compteurs par jeu pour déterminer le type de patch
    private final Map<String, GameMetrics> gameMetrics = new ConcurrentHashMap<>();
    
    // Temps de jeu par joueur et par jeu (clé = "playerId:gameId", valeur = minutes jouées)
    private final Map<String, AtomicLong> playerGamePlaytime = new ConcurrentHashMap<>();

    // Configuration minimum pour les événements dépendants
    private static final int MIN_GAMES = 5;
    private static final int MIN_PLAYERS = 3;
    
    // Seuils pour les types de patchs (valeurs basses pour tests rapides)
    public static final int INCIDENTS_FOR_FIX = 2;           // 2 crashs → patch FIX
    public static final int PURCHASES_FOR_OPTIMIZATION = 3;  // 3 achats → patch OPTIMIZATION
    public static final long PLAYTIME_HOURS_FOR_ADD = 2;     // 2h de jeu → patch ADD (120 min)
    
    // Seuil de temps de jeu requis pour pouvoir évaluer un jeu (10h = 600 minutes)
    public static final long PLAYTIME_HOURS_FOR_REVIEW = 10;

    // ========== MÉTRIQUES PAR JEU ==========
    
    /**
     * Classe pour stocker les métriques d'un jeu (thread-safe).
     */
    public static class GameMetrics {
        private final AtomicInteger incidentCount = new AtomicInteger(0);
        private final AtomicInteger purchaseCount = new AtomicInteger(0);
        private final AtomicLong totalPlaytimeMinutes = new AtomicLong(0);
        
        public int incrementIncidents() {
            return incidentCount.incrementAndGet();
        }
        
        public int incrementPurchases() {
            return purchaseCount.incrementAndGet();
        }
        
        public long addPlaytime(long minutes) {
            return totalPlaytimeMinutes.addAndGet(minutes);
        }
        
        public int getIncidentCount() { return incidentCount.get(); }
        public int getPurchaseCount() { return purchaseCount.get(); }
        public long getTotalPlaytimeHours() { return totalPlaytimeMinutes.get() / 60; }
        
        // Reset quand un patch est publié
        public void resetForFix() { incidentCount.set(0); }
        public void resetForOptimization() { purchaseCount.set(0); }
        public void resetForAdd() { totalPlaytimeMinutes.set(0); }
    }
    
    public GameMetrics getOrCreateMetrics(String gameId) {
        return gameMetrics.computeIfAbsent(gameId, id -> new GameMetrics());
    }
    
    /**
     * Enregistre un incident (crash) pour un jeu.
     */
    public void recordIncident(String gameId) {
        getOrCreateMetrics(gameId).incrementIncidents();
    }
    
    /**
     * Enregistre un achat pour un jeu.
     */
    public void recordPurchase(String gameId) {
        getOrCreateMetrics(gameId).incrementPurchases();
    }
    
    /**
     * Enregistre du temps de jeu pour un jeu (métrique globale du jeu).
     */
    public void recordPlaytime(String gameId, long minutes) {
        getOrCreateMetrics(gameId).addPlaytime(minutes);
    }
    
    /**
     * Enregistre du temps de jeu pour un joueur sur un jeu spécifique.
     */
    public void recordPlayerPlaytime(String playerId, String gameId, long minutes) {
        String key = playerId + ":" + gameId;
        playerGamePlaytime.computeIfAbsent(key, k -> new AtomicLong(0)).addAndGet(minutes);
    }
    
    /**
     * Récupère le temps de jeu d'un joueur sur un jeu spécifique (en minutes).
     */
    public long getPlayerPlaytimeMinutes(String playerId, String gameId) {
        String key = playerId + ":" + gameId;
        AtomicLong playtime = playerGamePlaytime.get(key);
        return playtime != null ? playtime.get() : 0;
    }
    
    /**
     * Récupère le temps de jeu d'un joueur sur un jeu spécifique (en heures).
     */
    public long getPlayerPlaytimeHours(String playerId, String gameId) {
        return getPlayerPlaytimeMinutes(playerId, gameId) / 60;
    }
    
    /**
     * Vérifie si un joueur a joué suffisamment (10h+) pour pouvoir évaluer un jeu.
     */
    public boolean canPlayerReviewGame(String playerId, String gameId) {
        return getPlayerPlaytimeHours(playerId, gameId) >= PLAYTIME_HOURS_FOR_REVIEW;
    }
    
    /**
     * Vérifie si un jeu est éligible pour un patch FIX (3+ incidents).
     */
    public boolean isEligibleForFix(String gameId) {
        GameMetrics metrics = gameMetrics.get(gameId);
        return metrics != null && metrics.getIncidentCount() >= INCIDENTS_FOR_FIX;
    }
    
    /**
     * Vérifie si un jeu est éligible pour un patch OPTIMIZATION (5+ achats).
     */
    public boolean isEligibleForOptimization(String gameId) {
        GameMetrics metrics = gameMetrics.get(gameId);
        return metrics != null && metrics.getPurchaseCount() >= PURCHASES_FOR_OPTIMIZATION;
    }
    
    /**
     * Vérifie si un jeu est éligible pour un patch ADD (100h+ de temps de jeu cumulé).
     */
    public boolean isEligibleForAdd(String gameId) {
        GameMetrics metrics = gameMetrics.get(gameId);
        return metrics != null && metrics.getTotalPlaytimeHours() >= PLAYTIME_HOURS_FOR_ADD;
    }
    
    /**
     * Réinitialise le compteur après publication d'un patch FIX.
     */
    public void resetAfterFix(String gameId) {
        GameMetrics metrics = gameMetrics.get(gameId);
        if (metrics != null) metrics.resetForFix();
    }
    
    /**
     * Réinitialise le compteur après publication d'un patch OPTIMIZATION.
     */
    public void resetAfterOptimization(String gameId) {
        GameMetrics metrics = gameMetrics.get(gameId);
        if (metrics != null) metrics.resetForOptimization();
    }
    
    /**
     * Réinitialise le compteur après publication d'un patch ADD.
     */
    public void resetAfterAdd(String gameId) {
        GameMetrics metrics = gameMetrics.get(gameId);
        if (metrics != null) metrics.resetForAdd();
    }

    // ========== ÉDITEURS ==========

    public void addPublisher(PublisherInfo publisher) {
        publishers.add(publisher);
    }

    public PublisherInfo getRandomPublisher() {
        if (publishers.isEmpty()) return null;
        int index = ThreadLocalRandom.current().nextInt(publishers.size());
        return publishers.get(index);
    }

    public List<PublisherInfo> getAllPublishers() {
        return List.copyOf(publishers);
    }

    public int getPublisherCount() {
        return publishers.size();
    }

    public boolean hasPublishers() {
        return !publishers.isEmpty();
    }

    // ========== JEUX ==========

    public void addGame(GameInfo game) {
        games.add(game);
    }

    public GameInfo getRandomGame() {
        if (games.isEmpty()) return null;
        int index = ThreadLocalRandom.current().nextInt(games.size());
        return games.get(index);
    }

    public List<GameInfo> getAllGames() {
        return List.copyOf(games);
    }

    public int getGameCount() {
        return games.size();
    }

    // ========== JOUEURS ==========

    public void addPlayer(PlayerInfo player) {
        players.add(player);
    }

    public PlayerInfo getRandomPlayer() {
        if (players.isEmpty()) return null;
        int index = ThreadLocalRandom.current().nextInt(players.size());
        return players.get(index);
    }

    public List<PlayerInfo> getAllPlayers() {
        return List.copyOf(players);
    }

    public int getPlayerCount() {
        return players.size();
    }

    // ========== ACHATS ==========

    public void addPurchase(PurchaseInfo purchase) {
        purchases.add(purchase);
    }

    public PurchaseInfo getRandomPurchase() {
        if (purchases.isEmpty()) return null;
        int index = ThreadLocalRandom.current().nextInt(purchases.size());
        return purchases.get(index);
    }

    public List<PurchaseInfo> getAllPurchases() {
        return List.copyOf(purchases);
    }

    public int getPurchaseCount() {
        return purchases.size();
    }

    // ========== VÉRIFICATION DES PRÉREQUIS ==========

    /**
     * Vérifie si le nombre minimum de jeux et joueurs est atteint
     * pour pouvoir générer les événements dépendants.
     */
    public boolean hasMinimumData() {
        return games.size() >= MIN_GAMES && players.size() >= MIN_PLAYERS;
    }

    public boolean hasGames() {
        return !games.isEmpty();
    }

    public boolean hasPlayers() {
        return !players.isEmpty();
    }

    public boolean hasPurchases() {
        return !purchases.isEmpty();
    }

    // ========== CLASSES INTERNES (Records) ==========

    /**
     * Informations sur un jeu stocké en mémoire.
     */
    public record GameInfo(
            String gameId,
            String gameName,
            String publisherId,
            String publisherName,
            String platform,
            String genre,
            double price,
            String currentVersion
    ) {}

    /**
     * Informations sur un joueur stocké en mémoire.
     */
    public record PlayerInfo(
            String playerId,
            String username,
            String email,
            String platformId
    ) {}

    /**
     * Informations sur un achat stocké en mémoire.
     */
    public record PurchaseInfo(
            String purchaseId,
            String gameId,
            String gameName,
            String playerId,
            String playerUsername,
            double pricePaid
    ) {}

    /**
     * Informations sur un éditeur stocké en mémoire.
     */
    public record PublisherInfo(
            String publisherId,
            String publisherName
    ) {}

    /**
     * Informations sur une évaluation stockée en mémoire.
     */
    public record ReviewInfo(
            String reviewId,
            String gameId,
            String playerId,
            String playerUsername
    ) {}

    /**
     * Informations sur un DLC publié stocké en mémoire.
     */
    public record DlcInfo(
            String dlcId,
            String dlcName,
            String gameId,
            String publisherId,
            String platform,
            double price
    ) {}

    /**
     * Informations sur un achat de DLC stocké en mémoire.
     */
    public record DlcPurchaseInfo(
            String purchaseId,
            String dlcId,
            String dlcName,
            String gameId,
            String playerId,
            String playerUsername,
            double pricePaid
    ) {}

    // ========== MÉTHODES POUR LES REVIEWS ==========

    public void addReview(ReviewInfo review) {
        reviews.add(review);
    }

    public ReviewInfo getRandomReview() {
        if (reviews.isEmpty()) return null;
        return reviews.get(ThreadLocalRandom.current().nextInt(reviews.size()));
    }

    public boolean hasReviews() {
        return !reviews.isEmpty();
    }

    public int getReviewCount() {
        return reviews.size();
    }

    // ========== MÉTHODES POUR LES DLCs ==========

    public void addDlc(DlcInfo dlc) {
        dlcs.add(dlc);
    }

    public DlcInfo getRandomDlc() {
        if (dlcs.isEmpty()) return null;
        return dlcs.get(ThreadLocalRandom.current().nextInt(dlcs.size()));
    }

    /**
     * Retourne un DLC aléatoire pour un jeu que le joueur possède.
     * @param playerId L'ID du joueur
     * @return Un DLC pour lequel le joueur possède le jeu de base, ou null
     */
    public DlcInfo getRandomDlcForPlayer(String playerId) {
        if (dlcs.isEmpty()) return null;
        
        // Récupère les IDs des jeux que le joueur possède
        java.util.Set<String> ownedGameIds = purchases.stream()
                .filter(p -> p.playerId().equals(playerId))
                .map(PurchaseInfo::gameId)
                .collect(java.util.stream.Collectors.toSet());
        
        if (ownedGameIds.isEmpty()) return null;
        
        // Filtre les DLCs pour lesquels le joueur possède le jeu de base
        // et qu'il n'a pas encore achetés
        java.util.Set<String> ownedDlcIds = dlcPurchases.stream()
                .filter(dp -> dp.playerId().equals(playerId))
                .map(DlcPurchaseInfo::dlcId)
                .collect(java.util.stream.Collectors.toSet());
        
        List<DlcInfo> availableDlcs = dlcs.stream()
                .filter(dlc -> ownedGameIds.contains(dlc.gameId()))
                .filter(dlc -> !ownedDlcIds.contains(dlc.dlcId()))
                .collect(java.util.stream.Collectors.toList());
        
        if (availableDlcs.isEmpty()) return null;
        return availableDlcs.get(ThreadLocalRandom.current().nextInt(availableDlcs.size()));
    }

    public List<DlcInfo> getAllDlcs() {
        return List.copyOf(dlcs);
    }

    public List<DlcInfo> getDlcsForGame(String gameId) {
        return dlcs.stream()
                .filter(dlc -> dlc.gameId().equals(gameId))
                .collect(java.util.stream.Collectors.toList());
    }

    public boolean hasDlcs() {
        return !dlcs.isEmpty();
    }

    public int getDlcCount() {
        return dlcs.size();
    }

    // ========== MÉTHODES POUR LES ACHATS DE DLC ==========

    public void addDlcPurchase(DlcPurchaseInfo dlcPurchase) {
        dlcPurchases.add(dlcPurchase);
    }

    /**
     * Vérifie si un joueur possède un jeu spécifique.
     */
    public boolean playerOwnsGame(String playerId, String gameId) {
        return purchases.stream()
                .anyMatch(p -> p.playerId().equals(playerId) && p.gameId().equals(gameId));
    }

    /**
     * Vérifie si un joueur a déjà acheté un DLC spécifique.
     */
    public boolean playerOwnsDlc(String playerId, String dlcId) {
        return dlcPurchases.stream()
                .anyMatch(dp -> dp.playerId().equals(playerId) && dp.dlcId().equals(dlcId));
    }

    public List<DlcPurchaseInfo> getAllDlcPurchases() {
        return List.copyOf(dlcPurchases);
    }

    public int getDlcPurchaseCount() {
        return dlcPurchases.size();
    }

    public boolean hasDlcPurchases() {
        return !dlcPurchases.isEmpty();
    }
}
