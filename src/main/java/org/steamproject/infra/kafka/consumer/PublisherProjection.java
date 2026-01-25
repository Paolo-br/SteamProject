package org.steamproject.infra.kafka.consumer;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PublisherProjection {
    private static final PublisherProjection INSTANCE = new PublisherProjection();
    private final Map<String, List<String>> store = new ConcurrentHashMap<>();
    // Track processed eventIds for publisher events
    private final java.util.Set<String> seenEventIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private PublisherProjection() {}

    public static PublisherProjection getInstance() { return INSTANCE; }

    public void addPublishedGame(String publisherId, String gameSummary) {
        store.compute(publisherId, (k, list) -> {
            if (list == null) return Collections.singletonList(gameSummary);
            java.util.List<String> newList = new java.util.ArrayList<>(list);
            newList.add(gameSummary);
            return Collections.unmodifiableList(newList);
        });
    }

    /**
     * Atomically mark an eventId as seen. Returns true if eventId was not seen before.
     */
    public boolean markEventIfNew(String eventId) {
        if (eventId == null || eventId.isEmpty()) return true;
        return seenEventIds.add(eventId);
    }

    public List<String> getPublishedGames(String publisherId) {
        return store.getOrDefault(publisherId, Collections.emptyList());
    }

    public Map<String, List<String>> snapshot() {
        return store.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
