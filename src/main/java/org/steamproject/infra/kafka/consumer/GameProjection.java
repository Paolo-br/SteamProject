package org.steamproject.infra.kafka.consumer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Projection en mémoire stockant les métadonnées complètes des jeux indexées par gameId.
 * 
 * Cette classe implémente un pattern Singleton thread-safe pour maintenir
 * une vue matérialisée des données de jeux agrégées depuis les événements Kafka.
 */
public class GameProjection {
    private static final GameProjection INSTANCE = new GameProjection();
    private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    private GameProjection() {}

    public static GameProjection getInstance() { return INSTANCE; }

    /**
     * Insère ou met à jour les informations de base d'un jeu.
     * 
     * Cette méthode initialise également les collections vides pour les patches,
     * DLCs, versions dépréciées et réponses aux incidents si elles n'existent pas déjà.
     * 
     * @param gameId Identifiant unique du jeu
     * @param distributionPlatform Plateforme de distribution (Steam, Epic, etc.)
     * @param console Hardware/console (PC, PS5, XboxSeriesX, Switch, etc.)
     * @param gameName Nom du jeu
     * @param releaseYear Année de sortie
     * @param genre Genre du jeu
     * @param publisherId Identifiant de l'éditeur
     * @param initialVersion Version initiale du jeu
     * @param initialPrice Prix initial
     */
    public void upsertGame(String gameId, String distributionPlatform, String console, String gameName, Integer releaseYear, String genre, String publisherId, String initialVersion, Double initialPrice) {
        store.compute(gameId, (k, old) -> {
            java.util.Map<String, Object> m = old != null ? new java.util.HashMap<>(old) : new java.util.HashMap<>();
            m.put("gameId", gameId);
            m.put("gameName", gameName);
            m.put("releaseYear", releaseYear);
            m.put("distributionPlatform", distributionPlatform);
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
     * 
     * Si le jeu n'existe pas dans le store, aucune action n'est effectuée.
     * Gère les conversions de type de manière défensive pour éviter les erreurs.
     * 
     * @param gameId Identifiant du jeu concerné
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

    /**
     * Ajoute un patch au jeu et crée automatiquement une nouvelle version.
     * 
     * Le patch est ajouté en tête de liste (index 0) pour avoir les plus récents en premier.
     * Une entrée de version correspondante est également créée automatiquement.
     * 
     * @param gameId Identifiant du jeu
     * @param patchId Identifiant unique du patch
     * @param oldVersion Numéro de version avant le patch
     * @param newVersion Numéro de version après le patch
     * @param description Description des changements
     * @param releaseTimestamp Timestamp de sortie du patch
     */
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

    /**
     * Ajoute un DLC (contenu téléchargeable) au jeu.
     * 
     * Le DLC est ajouté en tête de liste pour maintenir l'ordre chronologique inverse.
     * 
     * @param gameId Identifiant du jeu
     * @param dlcId Identifiant unique du DLC
     * @param dlcName Nom du DLC
     * @param price Prix du DLC
     * @param releaseTimestamp Timestamp de sortie du DLC
     */
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

    /**
     * Marque une version comme dépréciée pour un jeu donné.
     * 
     * Évite les doublons : si la version est déjà dans la liste, elle n'est pas ajoutée à nouveau.
     * 
     * @param gameId Identifiant du jeu
     * @param deprecatedVersion Numéro de version à déprécier
     * @param deprecatedAt Timestamp de dépréciation
     */
    public void deprecateVersion(String gameId, String deprecatedVersion, Long deprecatedAt) {
        store.computeIfPresent(gameId, (k, old) -> {
            java.util.Map<String,Object> m = new java.util.HashMap<>(old);
            var deprecated = (java.util.List<String>) m.getOrDefault("deprecatedVersions", new java.util.concurrent.CopyOnWriteArrayList<String>());
            if (!deprecated.contains(deprecatedVersion)) deprecated.add(0, deprecatedVersion);
            m.put("deprecatedVersions", deprecated);
            return java.util.Collections.unmodifiableMap(m);
        });
    }

    /**
     * Ajoute une réponse de l'éditeur à un incident signalé.
     * 
     * Les réponses sont stockées en ordre chronologique inverse (plus récentes en premier).
     * 
     * @param gameId Identifiant du jeu concerné
     * @param incidentId Identifiant de l'incident
     * @param responseMessage Message de réponse de l'éditeur
     * @param responseTimestamp Timestamp de la réponse
     */
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

    /**
     * Récupère les métadonnées complètes d'un jeu.
     * 
     * @param gameId Identifiant du jeu
     * @return Map contenant toutes les données du jeu, ou null si le jeu n'existe pas
     */
    public Map<String, Object> getGame(String gameId) {
        return store.get(gameId);
    }

    /**
     * Retourne un snapshot immuable de tous les jeux en mémoire.
     * 
     * @return Map non-modifiable contenant tous les jeux indexés par gameId
     */
    public java.util.Map<String, Map<String, Object>> snapshot() {
        return java.util.Collections.unmodifiableMap(store);
    }
}