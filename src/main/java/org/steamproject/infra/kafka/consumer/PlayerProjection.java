package org.steamproject.infra.kafka.consumer;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PlayerProjection {
    private static final PlayerProjection INSTANCE = new PlayerProjection();
    private final Map<String, java.util.Map<String, Object>> store = new ConcurrentHashMap<>();

    private PlayerProjection() {}

    public static PlayerProjection getInstance() { return INSTANCE; }

    public void upsert(String playerId, String username, String email, String registrationDate, long timestamp) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", playerId);
        map.put("username", username == null ? "" : username);
        map.put("email", email);
        map.put("registrationDate", registrationDate == null ? "" : registrationDate);
        map.put("timestamp", timestamp);
        store.put(playerId, Collections.unmodifiableMap(map));
    }

    public java.util.List<java.util.Map<String, Object>> list() {
        return store.values().stream().collect(Collectors.toUnmodifiableList());
    }

    public java.util.Map<String, java.util.Map<String, Object>> snapshot() {
        return store.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
