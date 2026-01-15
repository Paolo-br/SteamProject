package org.steamproject.ingestion;

import net.datafaker.Faker;
import org.steamproject.model.Player;

import java.time.Instant;
import java.util.*;

/**
 * Générateur de joueurs factices via DataFaker.
 */
public class PlayerGenerator {
    private final Random random;
    private final Faker faker;
    private final java.util.List<org.steamproject.model.Game> availableGames;

    public PlayerGenerator() {
        this(new Random(), null);
    }

    public PlayerGenerator(Random random, java.util.List<org.steamproject.model.Game> availableGames) {
        this.random = random;
        this.faker = new Faker(random);
        this.availableGames = availableGames == null ? java.util.Collections.emptyList() : availableGames;
    }

    public List<Player> generate(int count) {
        List<Player> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Player p = new Player();
            p.setId(UUID.randomUUID().toString());
            p.setUsername(faker.name().username());
            p.setEmail(faker.internet().emailAddress());
            p.setRegistrationDate(Instant.ofEpochMilli(faker.date().birthday().getTime()).toString());

            // assign a random library from available games
            java.util.List<org.steamproject.model.GameOwnership> lib = new ArrayList<>();
            if (!availableGames.isEmpty()) {
                int ownedCount = random.nextInt(Math.min(10, availableGames.size()) + 1); // 0..10
                java.util.Set<Integer> picks = new java.util.HashSet<>();
                for (int k = 0; k < ownedCount; k++) {
                    int idx;
                    do { idx = random.nextInt(availableGames.size()); } while (picks.contains(idx));
                    picks.add(idx);
                    org.steamproject.model.Game g = availableGames.get(idx);
                    org.steamproject.model.GameOwnership go = new org.steamproject.model.GameOwnership();
                    // ensure game id exists (may be computed by ingestion)
                    String gid = g.getId();
                    if (gid == null) {
                        try {
                            String key = (g.getName() == null ? "" : g.getName()) + "|" + (g.getPlatform() == null ? "" : g.getPlatform());
                            gid = java.util.UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                        } catch (Exception ex) { gid = UUID.randomUUID().toString(); }
                    }
                    go.setGameId(gid);
                    go.setGameName(g.getName());
                    go.setPurchaseDate(Instant.now().minusSeconds(random.nextInt(60*60*24*365)).toString());
                    go.setPlaytime(random.nextInt(500));
                    go.setLastPlayed(null);
                    go.setPricePaid(Math.round((random.nextDouble() * 60.0) * 100.0) / 100.0);
                    lib.add(go);
                }
            }
            p.setLibrary(java.util.Collections.unmodifiableList(lib));

            out.add(p);
        }
        return out;
    }
}
