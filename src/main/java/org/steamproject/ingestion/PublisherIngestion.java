package org.steamproject.ingestion;

import org.steamproject.model.Game;
import org.steamproject.model.Publisher;
import org.steamproject.service.GameDataService;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Ingestion dédiée aux éditeurs : agrège les lignes CSV par éditeur
 * et construit un état initial `Publisher`.
 *
  */
public class PublisherIngestion {
    private final GameDataService gameDataService;

    public PublisherIngestion(String resourcePath) {
        this.gameDataService = new GameDataService(resourcePath);
    }

    public PublisherIngestion() {
        this("/data/vgsales.csv");
    }

    public List<Publisher> readAll() {
        List<Game> games = gameDataService.getAll();

        Map<String, PublisherBuilder> map = new LinkedHashMap<>();

        for (Game g : games) {
            String pub = g.getPublisher();
            if (pub == null) continue;
            pub = pub.trim();
            if (pub.isEmpty()) continue;

            PublisherBuilder b = map.computeIfAbsent(pub, PublisherBuilder::new);
            b.incrementGames();

            String platform = g.getPlatform();
            if (platform != null && !platform.trim().isEmpty()) b.platforms.add(platform.trim());

            String genre = g.getGenre();
            if (genre != null && !genre.trim().isEmpty()) b.genres.add(genre.trim());
        }

        List<Publisher> out = new ArrayList<>();
        for (PublisherBuilder b : map.values()) {
            Publisher p = new Publisher();
            p.setName(b.name);

            
            String slug = b.name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{Alnum}]+", "_");
            slug = slug.replaceAll("^_+|_+$", "");
            if (slug.isEmpty()) {
                slug = UUID.nameUUIDFromBytes(b.name.getBytes(StandardCharsets.UTF_8)).toString();
            }
            p.setId("publisher-" + slug);

           
            p.setGamesPublished(b.gamesPublished);
            p.setPlatforms(new ArrayList<>(b.platforms));
            p.setGenres(new ArrayList<>(b.genres));

            
            Random rnd = new Random(b.name.hashCode());
            int founded = 1970 + rnd.nextInt(2005 - 1970 + 1); // 1970..2005
            p.setFoundedYear(founded);

            int pct = 60 + rnd.nextInt(21); // 60..80%
            int active = (int) Math.round(b.gamesPublished * (pct / 100.0));
            p.setActiveGames(active);
            out.add(p);
        }

        return out;
    }

    
    private static class PublisherBuilder {
        final String name;
        final Set<String> platforms = new LinkedHashSet<>();
        final Set<String> genres = new LinkedHashSet<>();
        int gamesPublished = 0;

        PublisherBuilder(String name) { this.name = name; }

        void incrementGames() { this.gamesPublished++; }
    }
}
