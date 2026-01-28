package org.steamproject.infra.kafka.consumer;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PlayerProjection {
    private static final PlayerProjection INSTANCE = new PlayerProjection();
    private final Map<String, java.util.Map<String, Object>> store = new ConcurrentHashMap<>();
    // Additional projections for activity and reviews
    private final Map<String, java.util.List<java.util.Map<String, Object>>> sessions = new ConcurrentHashMap<>();
    private final Map<String, java.util.List<java.util.Map<String, Object>>> crashes = new ConcurrentHashMap<>();
    private final Map<String, java.util.List<java.util.Map<String, Object>>> reviews = new ConcurrentHashMap<>();

    private PlayerProjection() {}

    public static PlayerProjection getInstance() { return INSTANCE; }

    public void upsert(String playerId, String username, String email, String registrationDate, String firstName, String lastName, String dateOfBirth, long timestamp, Boolean gdprConsent, String gdprConsentDate) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", playerId);
        map.put("username", username == null ? "" : username);
        map.put("email", email);
        map.put("registrationDate", registrationDate == null ? "" : registrationDate);
        map.put("firstName", firstName);
        map.put("lastName", lastName);
        map.put("dateOfBirth", dateOfBirth);
        map.put("timestamp", timestamp);
        map.put("gdprConsent", gdprConsent == null ? Boolean.FALSE : gdprConsent);
        map.put("gdprConsentDate", gdprConsentDate);
        store.put(playerId, Collections.unmodifiableMap(map));
    }

    public void recordSession(String playerId, String sessionId, String gameId, String gameName, int duration, String sessionType, long timestamp) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("sessionId", sessionId);
        m.put("gameId", gameId);
        m.put("gameName", gameName);
        m.put("duration", duration);
        m.put("sessionType", sessionType);
        m.put("timestamp", timestamp);
        sessions.compute(playerId, (k, list) -> {
            if (list == null) return Collections.singletonList(Collections.unmodifiableMap(m));
            java.util.List<java.util.Map<String,Object>> nl = new java.util.ArrayList<>(list);
            nl.add(Collections.unmodifiableMap(m));
            return Collections.unmodifiableList(nl);
        });
    }

    public void recordCrash(String playerId, String crashId, String gameId, String gameName, String platform, String severity, String errorType, String errorMessage, long timestamp) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("crashId", crashId);
        m.put("gameId", gameId);
        m.put("gameName", gameName);
        m.put("platform", platform);
        m.put("severity", severity);
        m.put("errorType", errorType);
        m.put("errorMessage", errorMessage);
        m.put("timestamp", timestamp);
        crashes.compute(playerId, (k, list) -> {
            if (list == null) return Collections.singletonList(Collections.unmodifiableMap(m));
            java.util.List<java.util.Map<String,Object>> nl = new java.util.ArrayList<>(list);
            nl.add(Collections.unmodifiableMap(m));
            return Collections.unmodifiableList(nl);
        });
    }

    public void addReview(String playerId, String reviewId, String gameId, int rating, String title, String text, boolean isSpoiler, long timestamp) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("reviewId", reviewId);
        m.put("gameId", gameId);
        m.put("rating", rating);
        m.put("title", title);
        m.put("text", text);
        m.put("isSpoiler", isSpoiler);
        m.put("timestamp", timestamp);
        reviews.compute(playerId, (k, list) -> {
            if (list == null) return Collections.singletonList(Collections.unmodifiableMap(m));
            // Prevent a player from adding more than one rating per game
            for (java.util.Map<String,Object> existing : list) {
                try {
                    Object gid = existing.get("gameId");
                    if (gid != null && gid.equals(gameId)) {
                        // already rated this game; ignore new rating
                        return list;
                    }
                } catch (Throwable t) { /* ignore per-item errors */ }
            }
            java.util.List<java.util.Map<String,Object>> nl = new java.util.ArrayList<>(list);
            nl.add(Collections.unmodifiableMap(m));
            return Collections.unmodifiableList(nl);
        });
    }

    public java.util.List<java.util.Map<String, Object>> list() {
        return store.values().stream().collect(Collectors.toUnmodifiableList());
    }

    public java.util.Map<String, java.util.Map<String, Object>> snapshot() {
        return store.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public java.util.Map<String, java.util.List<java.util.Map<String, Object>>> snapshotSessions() {
        return sessions.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public java.util.Map<String, java.util.List<java.util.Map<String, Object>>> snapshotCrashes() {
        return crashes.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public java.util.Map<String, java.util.List<java.util.Map<String, Object>>> snapshotReviews() {
        return reviews.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
