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

    public PlayerGenerator() {
        this(new Random());
    }

    public PlayerGenerator(Random random) {
        this.random = random;
        this.faker = new Faker(random);
    }

    public List<Player> generate(int count) {
        List<Player> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Player p = new Player();
            p.setId(UUID.randomUUID().toString());
            p.setUsername(faker.name().username());
            p.setEmail(faker.internet().emailAddress());
            p.setRegistrationDate(Instant.ofEpochMilli(faker.date().birthday().getTime()).toString());

            out.add(p);
        }
        return out;
    }
}
