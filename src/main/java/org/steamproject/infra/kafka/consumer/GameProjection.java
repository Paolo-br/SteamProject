package org.steamproject.infra.kafka.consumer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory projection storing full game metadata keyed by gameId.
 */
public class GameProjection {
    private static final GameProjection INSTANCE = new GameProjection();
    private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    private GameProjection() {}

    public static GameProjection getInstance() { return INSTANCE; }

    public void upsertGame(String gameId, String distributionPlatform, String console, String gameName, Integer releaseYear, String genre, String publisherId, String initialVersion, Double initialPrice) {
        store.compute(gameId, (k, old) -> {
            java.util.Map<String, Object> m = old != null ? new java.util.HashMap<>(old) : new java.util.HashMap<>();
            m.put("gameId", gameId);
            m.put("gameName", gameName);
            m.put("releaseYear", releaseYear);
            m.put("distributionPlatform", distributionPlatform);
            // console = hardware/console (PC, PS5, XboxSeriesX, Switch, etc.)
            m.put("console", console);
            m.put("platform", console);
            m.put("genre", genre);
            m.put("publisherId", publisherId);
            if (initialVersion != null) m.put("initialVersion", initialVersion);
            if (initialPrice != null) m.put("price", initialPrice);
            m.putIfAbsent("incidentCount", 0);
            m.putIfAbsent("versions", new java.util.concurrent.CopyOnWriteArrayList<java.util.Map<String,Object>>());
            m.putIfAbsent("patches", new java.util.concurrent.CopyOnWriteArrayList<java.util.Map<String,Object>>());
            m.putIfAbsent("dlcs", new java.util.concurrent.CopyOnWriteArrayList<java.util.Map<String,Object>>());
            m.putIfAbsent("deprecatedVersions", new java.util.concurrent.CopyOnWriteArrayList<String>());
            m.putIfAbsent("incidentResponses", new java.util.concurrent.CopyOnWriteArrayList<java.util.Map<String,Object>>());
            return java.util.Collections.unmodifiableMap(m);
        });
    }

    /**
     * Incrémente le compteur d'incidents pour un jeu donné (best-effort).
     */
    public void incrementIncidentCount(String gameId) {
        store.computeIfPresent(gameId, (k, old) -> {
            java.util.Map<String,Object> m = new java.util.HashMap<>(old);
            Object cur = m.getOrDefault("incidentCount", 0);
            int val = 0;
            try {
                if (cur instanceof Number) val = ((Number) cur).intValue();
                else val = Integer.parseInt(cur.toString());
            } catch (Exception e) { val = 0; }
            m.put("incidentCount", val + 1);
            return java.util.Collections.unmodifiableMap(m);
        });
    }

    public void addPatch(String gameId, String patchId, String oldVersion, String newVersion, String description, Long releaseTimestamp) {
        store.computeIfPresent(gameId, (k, old) -> {
            java.util.Map<String,Object> m = new java.util.HashMap<>(old);
            var patches = (java.util.List<java.util.Map<String,Object>>) m.getOrDefault("patches", new java.util.concurrent.CopyOnWriteArrayList<>());
            java.util.Map<String,Object> p = new java.util.HashMap<>();
            p.put("patchId", patchId);
            p.put("oldVersion", oldVersion);
            p.put("newVersion", newVersion);
            p.put("description", description);
            p.put("releaseTimestamp", releaseTimestamp);
            patches.add(0, p);
            m.put("patches", patches);
            var versions = (java.util.List<java.util.Map<String,Object>>) m.getOrDefault("versions", new java.util.concurrent.CopyOnWriteArrayList<>());
            java.util.Map<String,Object> v = new java.util.HashMap<>();
            v.put("versionNumber", newVersion);
            v.put("description", description == null ? "" : description);
            v.put("releaseDate", releaseTimestamp == null ? java.time.Instant.now().toString() : java.time.Instant.ofEpochMilli(releaseTimestamp).toString());
            versions.add(0, v);
            m.put("versions", versions);
            return java.util.Collections.unmodifiableMap(m);
        });
    }

    public void addDlc(String gameId, String dlcId, String dlcName, Double price, Long releaseTimestamp) {
        store.computeIfPresent(gameId, (k, old) -> {
            java.util.Map<String,Object> m = new java.util.HashMap<>(old);
            var dlcs = (java.util.List<java.util.Map<String,Object>>) m.getOrDefault("dlcs", new java.util.concurrent.CopyOnWriteArrayList<>());
            java.util.Map<String,Object> d = new java.util.HashMap<>();
            d.put("dlcId", dlcId);
            d.put("dlcName", dlcName);
            d.put("price", price);
            d.put("releaseTimestamp", releaseTimestamp);
            dlcs.add(0, d);
            m.put("dlcs", dlcs);
            return java.util.Collections.unmodifiableMap(m);
        });
    }

    public void deprecateVersion(String gameId, String deprecatedVersion, Long deprecatedAt) {
        store.computeIfPresent(gameId, (k, old) -> {
            java.util.Map<String,Object> m = new java.util.HashMap<>(old);
            var deprecated = (java.util.List<String>) m.getOrDefault("deprecatedVersions", new java.util.concurrent.CopyOnWriteArrayList<String>());
            if (!deprecated.contains(deprecatedVersion)) deprecated.add(0, deprecatedVersion);
            m.put("deprecatedVersions", deprecated);
            return java.util.Collections.unmodifiableMap(m);
        });
    }

    public void addEditorResponse(String gameId, String incidentId, String responseMessage, Long responseTimestamp) {
        store.computeIfPresent(gameId, (k, old) -> {
            java.util.Map<String,Object> m = new java.util.HashMap<>(old);
            var resp = (java.util.List<java.util.Map<String,Object>>) m.getOrDefault("incidentResponses", new java.util.concurrent.CopyOnWriteArrayList<>());
            java.util.Map<String,Object> r = new java.util.HashMap<>();
            r.put("incidentId", incidentId);
            r.put("responseMessage", responseMessage);
            r.put("responseTimestamp", responseTimestamp);
            resp.add(0, r);
            m.put("incidentResponses", resp);
            return java.util.Collections.unmodifiableMap(m);
        });
    }

    public Map<String, Object> getGame(String gameId) {
        return store.get(gameId);
    }

    public java.util.Map<String, Map<String, Object>> snapshot() {
        return java.util.Collections.unmodifiableMap(store);
    }
}
