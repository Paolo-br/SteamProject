package org.steamproject.infra.kafka.consumer;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PlatformProjection {
    private static final PlatformProjection INSTANCE = new PlatformProjection();
    private final Map<String, List<String>> store = new ConcurrentHashMap<>();

    private PlatformProjection() {}

    public static PlatformProjection getInstance() { return INSTANCE; }

    public void applyCatalogChange(String platformId, String gameSummary, boolean add) {
        store.compute(platformId, (k, list) -> {
            java.util.List<String> newList = list == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(list);
            if (add) {
                newList.add(gameSummary);
            } else {
                newList.removeIf(s -> s.startsWith(gameSummary.split("\\|",2)[0]));
            }
            return java.util.Collections.unmodifiableList(newList);
        });
    }

    public List<String> getCatalog(String platformId) { return store.getOrDefault(platformId, Collections.emptyList()); }

    public Map<String, List<String>> snapshot() { return store.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)); }
}
