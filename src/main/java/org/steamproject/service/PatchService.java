package org.steamproject.service;

import org.steamproject.model.Game;
import org.steamproject.model.Patch;
import org.steamproject.model.PatchType;
import org.steamproject.model.Change;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service en mémoire pour stocker et fournir des patchs.
 */
public class PatchService {
    private final GameDataService gameDataService;
    private volatile List<Patch> store = new ArrayList<>();

    public PatchService(String resourcePath) {
        this.gameDataService = new GameDataService(resourcePath);
    }
    public PatchService(GameDataService gameDataService) {
        this.gameDataService = gameDataService;
    }

    public List<Patch> getAll() {
        return Collections.unmodifiableList(store);
    }

    public synchronized void addPatch(Patch p) {
        if (p == null) return;
        store.add(p);
    }

    public List<Patch> getPatchesByGameId(String gameId) {
        if (gameId == null) return Collections.emptyList();
        return store.stream().filter(p -> gameId.equals(p.getGameId())).collect(Collectors.toList());
    }

    public List<Patch> getRecentPatches(int limit) {
        return store.stream().sorted(Comparator.comparingLong(Patch::getTimestamp).reversed()).limit(limit).collect(Collectors.toList());
    }

    /**
     * Crée un nouveau patch pour un jeu.
    */
    public synchronized Patch createPatchForGame(org.steamproject.model.Game game,
                                                String oldVersion,
                                                String newVersion,
                                                PatchType type,
                                                String description,
                                                List<Change> changes,
                                                Integer sizeInMB,
                                                String releaseDate) {
        if (game == null) return null;
        Patch p = new Patch();
        p.setId(java.util.UUID.randomUUID().toString());
        p.setGameId(game.getId());
        p.setGameName(game.getName());
        p.setPlatform(game.getPlatform());
        p.setOldVersion(oldVersion);
        try {
            String computed = org.steamproject.util.Semver.nextVersionForPatchType(oldVersion, type == null ? org.steamproject.model.PatchType.FIX : type);
            p.setNewVersion(computed);
        } catch (Exception ex) {
            if (newVersion != null && !newVersion.isBlank()) p.setNewVersion(newVersion);
            else p.setNewVersion(org.steamproject.util.Semver.incrementPatch(oldVersion));
        }
        p.setType(type);
        p.setDescription(description);
        if (changes != null) p.setChanges(List.copyOf(changes));
        p.setSizeInMB(sizeInMB);
        p.setReleaseDate(releaseDate);
        p.setTimestamp(java.time.Instant.now().toEpochMilli());
        store.add(p);
        return p;
    }
    
    /**
     * Crée un patch de correction simple avec une liste de descriptions.
    */
    public synchronized Patch createFixPatch(org.steamproject.model.Game game,
                                             String oldVersion,
                                             String newVersion,
                                             List<String> fixDescriptions,
                                             Integer sizeInMB,
                                             String releaseDate) {
        List<Change> changes = fixDescriptions.stream()
            .map(Change::fix) // Utilisation de la méthode factory du record
            .collect(Collectors.toList());
        
        return createPatchForGame(game, oldVersion, newVersion, 
                                  PatchType.FIX, "Patch de correction", 
                                  changes, sizeInMB, releaseDate);
    }

}
