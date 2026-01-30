package org.steamproject.service;

import org.steamproject.model.DLC;
import org.steamproject.model.Game;
import org.steamproject.model.GameOwnership;
import org.steamproject.model.Player;
import org.steamproject.model.Purchase;
import org.steamproject.util.Semver;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service de gestion des achats.
 * Stocke les achats en mémoire.
 */
public class PurchaseService {
    private final Map<String, Purchase> purchasesById = new ConcurrentHashMap<>();
    private final Map<String, List<String>> purchasesByPlayer = new ConcurrentHashMap<>();
    private final Map<String, List<String>> purchasesByGame = new ConcurrentHashMap<>();

    private final PlayerService playerService;
    private final GameDataService gameDataService;
    private final DLCService dlcService;
    private final PriceService priceService;

    public PurchaseService(PlayerService playerService, GameDataService gameDataService, 
                           DLCService dlcService, PriceService priceService) {
        this.playerService = playerService;
        this.gameDataService = gameDataService;
        this.dlcService = dlcService;
        this.priceService = priceService;
    }

    /**
     * Effectue l'achat d'un jeu par un joueur.
     * @return L'achat créé, ou null si achat impossible (jeu non trouvé, joueur non trouvé, déjà possédé)
     */
    public synchronized Purchase purchaseGame(String playerId, String gameId) {
        Player player = playerService.getPlayerById(playerId);
        if (player == null) return null;

        Game game = gameDataService.getById(gameId);
        if (game == null) return null;

        // Vérifier si le joueur possède déjà le jeu
        if (playerOwnsGame(playerId, gameId)) {
            return null;
        }

        // Déterminer le prix (via PriceService ou prix par défaut)
        Double price = priceService.getPrice(gameId);
        if (price == null) {
            price = 29.99; // Prix par défaut
        }

        // Créer l'achat
        String purchaseId = UUID.randomUUID().toString();
        long now = Instant.now().toEpochMilli();
        String platform = game.getPlatform() != null ? game.getPlatform() : "PC";

        Purchase purchase = new Purchase(
            purchaseId, gameId, game.getName(), playerId, 
            player.getUsername(), price, platform, now
        );

        // Enregistrer l'achat
        purchasesById.put(purchaseId, purchase);
        purchasesByPlayer.computeIfAbsent(playerId, k -> new ArrayList<>()).add(purchaseId);
        purchasesByGame.computeIfAbsent(gameId, k -> new ArrayList<>()).add(purchaseId);

        // Ajouter le jeu à la bibliothèque du joueur
        addGameToPlayerLibrary(player, game, price);

        return purchase;
    }

    /**
     * Effectue l'achat d'un DLC par un joueur.
     * Vérifie que le joueur possède le jeu principal et qu'il a la version requise.
     * @return L'achat créé, ou null si achat impossible
     */
    public synchronized Purchase purchaseDLC(String playerId, String dlcId) {
        Player player = playerService.getPlayerById(playerId);
        if (player == null) return null;

        DLC dlc = dlcService.getDLCById(dlcId);
        if (dlc == null) return null;

        // Vérifier que le joueur possède le jeu principal (sauf si DLC standalone)
        if (!dlc.isStandalone()) {
            GameOwnership ownership = getPlayerGameOwnership(player, dlc.getParentGameId());
            if (ownership == null) {
                return null; // Le joueur ne possède pas le jeu principal
            }

            // Vérifier la version minimale (si applicable)
            if (dlc.getMinGameVersion() != null && !dlc.getMinGameVersion().isEmpty()) {
                String playerVersion = getPlayerGameVersion(player, dlc.getParentGameId());
                if (playerVersion == null || !Semver.isCompatible(playerVersion, dlc.getMinGameVersion())) {
                    return null; // Version insuffisante
                }
            }
        }

        // Vérifier si le joueur possède déjà ce DLC
        if (playerOwnsDLC(playerId, dlcId)) {
            return null;
        }

        // Créer l'achat
        String purchaseId = UUID.randomUUID().toString();
        long now = Instant.now().toEpochMilli();

        Purchase purchase = new Purchase(
            purchaseId, dlc.getParentGameId(), dlc.getName(), playerId,
            player.getUsername(), dlc.getPrice(), "PC", now
        );
        purchase.setDlc(true);
        purchase.setDlcId(dlcId);

        // Enregistrer
        purchasesById.put(purchaseId, purchase);
        purchasesByPlayer.computeIfAbsent(playerId, k -> new ArrayList<>()).add(purchaseId);

        return purchase;
    }

    /**
     * Récupère tous les achats d'un joueur.
     */
    public List<Purchase> getPurchasesByPlayer(String playerId) {
        List<String> ids = purchasesByPlayer.getOrDefault(playerId, Collections.emptyList());
        return ids.stream()
            .map(purchasesById::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * Récupère tous les achats pour un jeu.
     */
    public List<Purchase> getPurchasesByGame(String gameId) {
        List<String> ids = purchasesByGame.getOrDefault(gameId, Collections.emptyList());
        return ids.stream()
            .map(purchasesById::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * Récupère un achat par son ID.
     */
    public Purchase getPurchaseById(String purchaseId) {
        return purchasesById.get(purchaseId);
    }

    /**
     * Récupère tous les achats.
     */
    public List<Purchase> getAllPurchases() {
        return new ArrayList<>(purchasesById.values());
    }

    /**
     * Retourne le nombre total d'achats.
     */
    public int getTotalPurchaseCount() {
        return purchasesById.size();
    }

    /**
     * Retourne le revenu total.
     */
    public double getTotalRevenue() {
        return purchasesById.values().stream()
            .mapToDouble(Purchase::getPricePaid)
            .sum();
    }

    /**
     * Récupère les achats récents.
     */
    public List<Purchase> getRecentPurchases(int limit) {
        return purchasesById.values().stream()
            .sorted(Comparator.comparingLong(Purchase::getTimestamp).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    // --- Helpers privés ---

    private boolean playerOwnsGame(String playerId, String gameId) {
        Player player = playerService.getPlayerById(playerId);
        if (player == null || player.getLibrary() == null) return false;
        return player.getLibrary().stream()
            .anyMatch(o -> gameId.equals(o.gameId()));
    }

    private boolean playerOwnsDLC(String playerId, String dlcId) {
        List<String> ids = purchasesByPlayer.getOrDefault(playerId, Collections.emptyList());
        return ids.stream()
            .map(purchasesById::get)
            .filter(Objects::nonNull)
            .filter(Purchase::isDlc)
            .anyMatch(p -> dlcId.equals(p.getDlcId()));
    }

    private GameOwnership getPlayerGameOwnership(Player player, String gameId) {
        if (player.getLibrary() == null) return null;
        return player.getLibrary().stream()
            .filter(o -> gameId.equals(o.gameId()))
            .findFirst()
            .orElse(null);
    }

    private String getPlayerGameVersion(Player player, String gameId) {
        // Pour le moment, on retourne une version par défaut
        // Dans une vraie implémentation, cela viendrait de la bibliothèque du joueur
        return "1.0.0";
    }

    /**
     * Ajoute un jeu à la bibliothèque du joueur.
     * Player.library est une List mutable pour permettre les ajouts.
     * Dans une architecture plus pure, on utiliserait un wither sur Player.
     */
    private void addGameToPlayerLibrary(Player player, Game game, double pricePaid) {
        List<GameOwnership> library = player.getLibrary();
        if (library == null) {
            library = new ArrayList<>();
            player.setLibrary(library);
        }

        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        
        GameOwnership ownership = new GameOwnership(
            game.getId(),
            game.getName(),
            today,
            0,      // playtime initial
            null,   // lastPlayed
            pricePaid
        );

        library.add(ownership);
    }
}
