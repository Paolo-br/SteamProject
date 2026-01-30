package org.steamproject.scheduler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

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

    // Configuration minimum pour les événements dépendants
    private static final int MIN_GAMES = 5;
    private static final int MIN_PLAYERS = 3;

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
}
