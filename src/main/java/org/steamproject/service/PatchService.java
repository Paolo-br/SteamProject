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
     * Génère et stocke des patchs pour tous les jeux d'un éditeur donné.
     * Le mapping publisherId -> publisherName est effectué via `PublisherService`.
     */
    public List<Patch> generatePatchesForPublisher(String publisherId, int perGame) {
        // Generation automatique non supportée sans attributs explicites.
        // Use `createPatchForGameUsingPublisher(...)` to create patches with explicit attributes.
        return Collections.emptyList();
    }
}
