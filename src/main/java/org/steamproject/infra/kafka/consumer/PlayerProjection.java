package org.steamproject.infra.kafka.consumer;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Projection en mémoire maintenant les données complètes des joueurs et leur activité.
 * 
 * Cette classe implémente un pattern Singleton thread-safe pour stocker :
 * - Les informations de profil des joueurs (username, email, RGPD, etc.)
 * - L'historique des sessions de jeu
 * - Les rapports de crash signalés
 * - Les avis et notations publiés
 * 
 * Chaque type d'information est stocké dans une projection distincte pour
 * faciliter l'accès et la gestion indépendante des données.
 */
public class PlayerProjection {
    private static final PlayerProjection INSTANCE = new PlayerProjection();
    private final Map<String, java.util.Map<String, Object>> store = new ConcurrentHashMap<>();
    private final Map<String, java.util.List<java.util.Map<String, Object>>> sessions = new ConcurrentHashMap<>();
    private final Map<String, java.util.List<java.util.Map<String, Object>>> crashes = new ConcurrentHashMap<>();
    private final Map<String, java.util.List<java.util.Map<String, Object>>> reviews = new ConcurrentHashMap<>();

    private PlayerProjection() {}

    public static PlayerProjection getInstance() { return INSTANCE; }

    /**
     * Insère ou met à jour les informations de profil d'un joueur.
     * 
     * Cette méthode stocke toutes les données du joueur incluant les informations
     * personnelles et le consentement RGPD. Les champs null sont préservés tels quels,
     * sauf pour gdprConsent qui est initialisé à false si null.
     * 
     * @param playerId Identifiant unique du joueur
     * @param username Nom d'utilisateur
     * @param email Adresse email
     * @param registrationDate Date d'inscription au format ISO
     * @param firstName Prénom du joueur (peut être null)
     * @param lastName Nom de famille du joueur (peut être null)
     * @param dateOfBirth Date de naissance au format ISO (peut être null)
     * @param timestamp Timestamp de création du profil
     * @param gdprConsent Consentement RGPD (null devient false)
     * @param gdprConsentDate Date du consentement RGPD (peut être null)
     */
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

    /**
     * Enregistre une session de jeu pour un joueur.
     * 
     * Ajoute la session à l'historique du joueur avec toutes ses métadonnées
     * (durée, type de session, etc.). Les sessions sont stockées dans l'ordre
     * d'ajout.
     * 
     * @param playerId Identifiant du joueur
     * @param sessionId Identifiant unique de la session
     * @param gameId Identifiant du jeu joué
     * @param gameName Nom du jeu
     * @param duration Durée de la session en minutes
     * @param sessionType Type de session (ONLINE, OFFLINE, etc.)
     * @param timestamp Timestamp de la session
     */
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

    /**
     * Enregistre un rapport de crash pour un joueur.
     * 
     * Ajoute le crash à l'historique des incidents signalés par le joueur
     * avec toutes les informations de diagnostic (plateforme, sévérité,
     * type d'erreur et message).
     * 
     * @param playerId Identifiant du joueur ayant subi le crash
     * @param crashId Identifiant unique du crash
     * @param gameId Identifiant du jeu concerné
     * @param gameName Nom du jeu
     * @param platform Plateforme sur laquelle le crash s'est produit
     * @param severity Niveau de sévérité du crash
     * @param errorType Type d'erreur technique
     * @param errorMessage Message d'erreur détaillé (peut être null)
     * @param timestamp Timestamp du crash
     */
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

    /**
     * Ajoute un avis/notation de jeu pour un joueur.
     * 
     * Vérifie qu'un avis pour ce jeu n'existe pas déjà avant d'ajouter.
     * Si le joueur a déjà publié un avis pour ce jeu, l'avis n'est pas
     * ajouté à nouveau (évite les doublons par gameId).
     * 
     * @param playerId Identifiant du joueur auteur de l'avis
     * @param reviewId Identifiant unique de l'avis
     * @param gameId Identifiant du jeu noté
     * @param rating Note attribuée au jeu
     * @param title Titre de l'avis (peut être null pour une simple notation)
     * @param text Texte de l'avis (peut être null pour une simple notation)
     * @param isSpoiler Indique si l'avis contient des spoilers
     * @param timestamp Timestamp de publication de l'avis
     */
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
            for (java.util.Map<String,Object> existing : list) {
                try {
                    Object gid = existing.get("gameId");
                    if (gid != null && gid.equals(gameId)) {
                        return list;
                    }
                } catch (Throwable t) { }
            }
            java.util.List<java.util.Map<String,Object>> nl = new java.util.ArrayList<>(list);
            nl.add(Collections.unmodifiableMap(m));
            return Collections.unmodifiableList(nl);
        });
    }

    /**
     * Retourne la liste de tous les profils de joueurs.
     * 
     * @return Liste immuable de toutes les données de joueurs
     */
    public java.util.List<java.util.Map<String, Object>> list() {
        return store.values().stream().collect(Collectors.toUnmodifiableList());
    }

    /**
     * Retourne un snapshot immuable de tous les profils de joueurs.
     * 
     * @return Map non-modifiable associant chaque playerId à ses données de profil
     */
    public java.util.Map<String, java.util.Map<String, Object>> snapshot() {
        return store.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Retourne un snapshot immuable de toutes les sessions de jeu.
     * 
     * @return Map non-modifiable associant chaque playerId à son historique de sessions
     */
    public java.util.Map<String, java.util.List<java.util.Map<String, Object>>> snapshotSessions() {
        return sessions.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Retourne un snapshot immuable de tous les rapports de crash.
     * 
     * @return Map non-modifiable associant chaque playerId à son historique de crashes
     */
    public java.util.Map<String, java.util.List<java.util.Map<String, Object>>> snapshotCrashes() {
        return crashes.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Retourne un snapshot immuable de tous les avis publiés.
     * 
     * @return Map non-modifiable associant chaque playerId à ses avis de jeux
     */
    public java.util.Map<String, java.util.List<java.util.Map<String, Object>>> snapshotReviews() {
        return reviews.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}