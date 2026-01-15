package org.steamproject.service;

import org.steamproject.ingestion.PlayerGenerator;
import org.steamproject.model.Player;

import java.util.Collections;
import java.util.List;


public class PlayerService {
    private final PlayerGenerator generator;
    private volatile List<Player> cache;

    public PlayerService() {
        // initialize generator with available games so generated players can own games
        java.util.List<org.steamproject.model.Game> games;
        try {
            games = new GameDataService().getAll();
        } catch (Exception ex) {
            games = java.util.Collections.emptyList();
        }
        this.generator = new PlayerGenerator(new java.util.Random(), games);
    }

    public PlayerService(PlayerGenerator generator) {
        this.generator = generator;
    }

    public List<Player> getAllPlayers() {
        if (cache == null) {
            synchronized (this) {
                if (cache == null) {
                    cache = Collections.unmodifiableList(generator.generate(40));
                }
            }
        }
        return cache;
    }

    public Player getPlayerById(String id) {
        return getAllPlayers().stream().filter(p -> p.getId() != null && p.getId().equals(id)).findFirst().orElse(null);
    }
}
