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
}
