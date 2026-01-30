package org.steamproject.infra.kafka.consumer;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Projection en mémoire maintenant le catalogue de jeux pour chaque plateforme.
 * 
 * Cette classe implémente un pattern Singleton thread-safe pour stocker
 * une vue matérialisée des catalogues de distribution par plateforme.
 * Chaque plateforme possède une liste de résumés de jeux au format "gameId|gameName".
 */
public class PlatformProjection {
    private static final PlatformProjection INSTANCE = new PlatformProjection();
    private final Map<String, List<String>> store = new ConcurrentHashMap<>();

    private PlatformProjection() {}

    public static PlatformProjection getInstance() { return INSTANCE; }

    /**
     * Applique un changement au catalogue d'une plateforme (ajout ou retrait de jeu).
     * 
     * Cette méthode permet de maintenir à jour la liste des jeux disponibles
     * sur une plateforme donnée. En cas de retrait, tous les jeux correspondant
     * au gameId (première partie du résumé avant le '|') sont supprimés.
     * 
     * @param platformId Identifiant de la plateforme (Steam, Epic, etc.)
     * @param gameSummary Résumé du jeu au format "gameId|gameName"
     * @param add true pour ajouter le jeu, false pour le retirer
     */
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

    /**
     * Récupère le catalogue complet d'une plateforme.
     * 
     * @param platformId Identifiant de la plateforme
     * @return Liste immuable des résumés de jeux, ou liste vide si la plateforme n'existe pas
     */
    public List<String> getCatalog(String platformId) { 
        return store.getOrDefault(platformId, Collections.emptyList()); 
    }

    /**
     * Retourne un snapshot immuable de tous les catalogues en mémoire.
     * 
     * @return Map non-modifiable associant chaque platformId à son catalogue de jeux
     */
    public Map<String, List<String>> snapshot() { 
        return store.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)); 
    }
}