package org.steamproject.infra.kafka.consumer;

import org.steamproject.model.GameOwnership;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Projection en mémoire maintenant la bibliothèque de jeux de chaque joueur.
 * 
 * Cette classe implémente un pattern Singleton thread-safe pour stocker
 * les jeux possédés par chaque joueur (achats de jeux et DLCs) avec leurs
 * statistiques de temps de jeu. Elle gère également la déduplication des
 * événements via un registre d'eventIds déjà traités.
 */
public class PlayerLibraryProjection {
    private static final PlayerLibraryProjection INSTANCE = new PlayerLibraryProjection();
    private final Map<String, List<GameOwnership>> store = new ConcurrentHashMap<>();
    private final java.util.Set<String> seenEventIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private PlayerLibraryProjection() {}

    public static PlayerLibraryProjection getInstance() {
        return INSTANCE;
    }

    /**
     * Ajoute un jeu ou DLC à la bibliothèque d'un joueur.
     * 
     * Si le joueur n'a pas encore de bibliothèque, une nouvelle liste est créée.
     * Le jeu est ajouté à la fin de la liste existante.
     * 
     * @param playerId Identifiant du joueur
     * @param ownership Objet représentant la possession du jeu/DLC avec ses métadonnées
     */
    public void addOwnership(String playerId, GameOwnership ownership) {
        store.compute(playerId, (k, list) -> {
            if (list == null) return Collections.singletonList(ownership);
            java.util.List<GameOwnership> newList = new java.util.ArrayList<>(list);
            newList.add(ownership);
            return Collections.unmodifiableList(newList);
        });
    }

    /**
     * Ajoute du temps de jeu à un jeu spécifique dans la bibliothèque d'un joueur.
     * 
     * Recherche le jeu dans la bibliothèque du joueur et met à jour son temps
     * de jeu cumulé ainsi que sa dernière date de jeu. Les minutes sont converties
     * en heures (division par 60). Si le jeu n'est pas trouvé, aucune modification
     * n'est effectuée.
     * 
     * @param playerId Identifiant du joueur
     * @param gameId Identifiant du jeu concerné
     * @param minutes Durée de jeu à ajouter en minutes
     * @param lastPlayedIso Date de dernière session au format ISO (peut être null)
     */
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
                return list; 
            }
            return Collections.unmodifiableList(newList);
        });
    }

    /**
     * Marque un événement comme traité pour éviter les doublons.
     * 
     * Cette méthode implémente un mécanisme de déduplication thread-safe.
     * Si l'eventId est null ou vide, l'événement est considéré comme nouveau
     * par défaut (retourne true).
     * 
     * @param eventId Identifiant unique de l'événement
     * @return true si l'événement est nouveau (première fois qu'on le voit),
     *         false si l'événement a déjà été traité (doublon détecté)
     */
    public boolean markEventIfNew(String eventId) {
        if (eventId == null || eventId.isEmpty()) return true; 
        return seenEventIds.add(eventId);
    }

    /**
     * Récupère la bibliothèque complète d'un joueur.
     * 
     * @param playerId Identifiant du joueur
     * @return Liste immuable des jeux possédés, ou liste vide si le joueur n'existe pas
     */
    public List<GameOwnership> getLibrary(String playerId) {
        return store.getOrDefault(playerId, Collections.emptyList());
    }

    /**
     * Retourne un snapshot immuable de toutes les bibliothèques en mémoire.
     * 
     * @return Map non-modifiable associant chaque playerId à sa bibliothèque de jeux
     */
    public Map<String, List<GameOwnership>> snapshot() {
        return store.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}