package org.steamproject.service;

import org.steamproject.ingestion.GameIngestion;
import org.steamproject.model.Game;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service fournissant un accès en mémoire aux données de jeux.
 */
public class GameDataService {
    private final String resourcePath;
    private volatile List<Game> cache;

    public GameDataService(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public GameDataService() {
        this("/data/vgsales.csv");
    }

    private void ensureLoaded() {
        if (cache == null) {
            synchronized (this) {
                if (cache == null) {
                    try {
                        cache = new GameIngestion(resourcePath).readAll();
                    } catch (IOException e) {
                        cache = new ArrayList<>();
                    }
                }
            }
        }
    }

    public List<Game> getAll() {
        ensureLoaded();
        return Collections.unmodifiableList(cache);
    }

    public List<Game> filterByYear(Integer year) {
        ensureLoaded();
        if (year == null) return getAll();
        return cache.stream()
                .filter(g -> Objects.equals(g.getYear(), year))
                .collect(Collectors.toList());
    }

    public List<Game> filterByGenre(String genre) {
        ensureLoaded();
        if (genre == null || genre.trim().isEmpty()) return getAll();
        String q = genre.trim().toLowerCase();
        return cache.stream()
                .filter(g -> g.getGenre() != null && g.getGenre().toLowerCase().contains(q))
                .collect(Collectors.toList());
    }

    public List<Game> searchByName(String query) {
        ensureLoaded();
        if (query == null || query.trim().isEmpty()) return getAll();
        String q = query.trim().toLowerCase();
        return cache.stream()
                .filter(g -> g.getName() != null && g.getName().toLowerCase().contains(q))
                .collect(Collectors.toList());
    }

    /**
     * Récupère un jeu par son ID.
     */
    public Game getById(String gameId) {
        ensureLoaded();
        if (gameId == null) return null;
        return cache.stream()
                .filter(g -> gameId.equals(g.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Récupère les jeux par plateforme.
     */
    public List<Game> filterByPlatform(String platform) {
        ensureLoaded();
        if (platform == null || platform.trim().isEmpty()) return getAll();
        String p = platform.trim();
        return cache.stream()
                .filter(g -> g.getPlatform() != null && g.getPlatform().equalsIgnoreCase(p))
                .collect(Collectors.toList());
    }

    /**
     * Récupère la liste des plateformes distinctes.
     */
    public List<String> getPlatforms() {
        ensureLoaded();
        return cache.stream()
                .map(Game::getPlatform)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}
