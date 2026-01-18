package org.steamproject.service;

import org.steamproject.model.DLC;
import org.steamproject.model.Game;
import org.steamproject.util.Semver;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service de gestion des DLC.
 */
public class DLCService {
    private final Map<String, DLC> dlcById = new ConcurrentHashMap<>();
    private final Map<String, List<String>> dlcByParentGame = new ConcurrentHashMap<>();
    private final GameDataService gameDataService;

    public DLCService(GameDataService gameDataService) {
        this.gameDataService = gameDataService;
     }

    

    /**
     * Ajoute un DLC au catalogue.
     */
    public synchronized void addDLC(DLC dlc) {
        if (dlc == null || dlc.getId() == null) return;
        dlcById.put(dlc.getId(), dlc);
        if (dlc.getParentGameId() != null) {
            dlcByParentGame.computeIfAbsent(dlc.getParentGameId(), k -> new ArrayList<>())
                .add(dlc.getId());
        }
    }

    /**
     * Récupère un DLC par son ID.
     */
    public DLC getDLCById(String dlcId) {
        return dlcById.get(dlcId);
    }

    /**
     * Récupère tous les DLC pour un jeu.
     */
    public List<DLC> getDLCsForGame(String gameId) {
        List<String> ids = dlcByParentGame.getOrDefault(gameId, Collections.emptyList());
        return ids.stream()
            .map(dlcById::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * Récupère tous les DLC.
     */
    public List<DLC> getAllDLCs() {
        return new ArrayList<>(dlcById.values());
    }

    /**
     * Vérifie si un joueur peut acheter un DLC (enforcement de version).
     * @param playerGameVersion La version du jeu que le joueur possède
     * @param dlcId L'ID du DLC à acheter
     * @return true si le joueur peut acheter, false sinon
     */
    public boolean canPlayerPurchaseDLC(String playerGameVersion, String dlcId) {
        DLC dlc = getDLCById(dlcId);
        if (dlc == null) return false;
        
        // DLC standalone peut toujours être acheté
        if (dlc.isStandalone()) return true;
        
        // Vérifier la version
        if (dlc.getMinGameVersion() == null || dlc.getMinGameVersion().isEmpty()) {
            return true;
        }
        
        return Semver.isCompatible(playerGameVersion, dlc.getMinGameVersion());
    }

    /**
     * Retourne les DLC compatibles avec une version donnée du jeu.
     */
    public List<DLC> getCompatibleDLCs(String gameId, String playerVersion) {
        return getDLCsForGame(gameId).stream()
            .filter(dlc -> dlc.isStandalone() || 
                          dlc.getMinGameVersion() == null || 
                          Semver.isCompatible(playerVersion, dlc.getMinGameVersion()))
            .collect(Collectors.toList());
    }

    /**
     * Retourne le nombre total de DLC.
     */
    public int getTotalDLCCount() {
        return dlcById.size();
    }
}
