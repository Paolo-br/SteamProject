package org.steamproject.infra.kafka.consumer;

import org.steamproject.model.GameOwnership;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PlayerLibraryProjection {
    private static final PlayerLibraryProjection INSTANCE = new PlayerLibraryProjection();
    private final Map<String, List<GameOwnership>> store = new ConcurrentHashMap<>();
    private final java.util.Set<String> seenEventIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private PlayerLibraryProjection() {}

    public static PlayerLibraryProjection getInstance() {
        return INSTANCE;
    }

    public void addOwnership(String playerId, GameOwnership ownership) {
        store.compute(playerId, (k, list) -> {
            if (list == null) return Collections.singletonList(ownership);
            java.util.List<GameOwnership> newList = new java.util.ArrayList<>(list);
            newList.add(ownership);
            return Collections.unmodifiableList(newList);
        });
    }

    public void addPlaytime(String playerId, String gameId, int minutes, String lastPlayedIso) {
        store.computeIfPresent(playerId, (k, list) -> {
            java.util.List<GameOwnership> newList = new java.util.ArrayList<>();
            boolean changed = false;
            for (GameOwnership go : list) {
                if (go != null && go.gameId() != null && go.gameId().equals(gameId)) {
                    int hoursToAdd = Math.max(0, minutes / 60);
                    GameOwnership updated = go.withAdditionalPlaytime(hoursToAdd);
                    if (lastPlayedIso != null) updated = updated.withLastPlayed(lastPlayedIso);
                    newList.add(updated);
                    changed = true;
                } else {
                    newList.add(go);
                }
            }
            if (!changed) {
                return list; // no change
            }
            return Collections.unmodifiableList(newList);
        });
    }


    public boolean markEventIfNew(String eventId) {
        if (eventId == null || eventId.isEmpty()) return true; 
        return seenEventIds.add(eventId);
    }

    public List<GameOwnership> getLibrary(String playerId) {
        return store.getOrDefault(playerId, Collections.emptyList());
    }

    public Map<String, List<GameOwnership>> snapshot() {
        return store.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

   
}
