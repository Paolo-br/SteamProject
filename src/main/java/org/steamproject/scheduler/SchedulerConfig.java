package org.steamproject.scheduler;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration du scheduler pour les fréquences et délais des événements.
 * Peut charger depuis un fichier properties ou utiliser des valeurs par défaut.
 */
public class SchedulerConfig {

    private final Properties props;

    // Valeurs par défaut (en millisecondes) - ESPACÉES pour éviter le spam
    private static final long DEFAULT_GAME_RELEASE_INTERVAL = 15000;     // 15 secondes
    private static final long DEFAULT_PLAYER_CREATE_INTERVAL = 20000;    // 20 secondes
    private static final long DEFAULT_PURCHASE_INTERVAL = 8000;          // 8 secondes
    private static final long DEFAULT_SESSION_INTERVAL = 10000;          // 10 secondes
    private static final long DEFAULT_RATING_INTERVAL = 12000;           // 12 secondes
    private static final long DEFAULT_CRASH_INTERVAL = 25000;            // 25 secondes
    private static final long DEFAULT_PATCH_INTERVAL = 60000;            // 1 minute
    private static final long DEFAULT_DLC_INTERVAL = 90000;              // 1.5 minutes
    private static final long DEFAULT_REVIEW_INTERVAL = 20000;           // 20 secondes

    // Délais initiaux pour la phase 2 (événements dépendants)
    private static final long DEFAULT_DEPENDENT_INITIAL_DELAY = 15000;
    private static final long DEFAULT_DEPENDENT_PHASE2_DELAY = 20000;
    private static final long DEFAULT_DEPENDENT_PHASE3_DELAY = 30000;

    // Seuils minimum
    private static final int DEFAULT_MINIMUM_GAMES = 5;
    private static final int DEFAULT_MINIMUM_PLAYERS = 3;

    // Intervalle de stats
    private static final long DEFAULT_STATS_INTERVAL = 30000;

    /**
     * Constructeur par défaut avec valeurs par défaut.
     */
    public SchedulerConfig() {
        this.props = new Properties();
    }

    /**
     * Constructeur chargeant depuis un fichier properties.
     */
    public SchedulerConfig(String path) throws IOException {
        this.props = new Properties();
        try (InputStream is = new FileInputStream(path)) {
            props.load(is);
        }
    }

    // ========== FRÉQUENCES ==========

    public long getGameReleaseInterval() {
        return getLong("game.release.interval", DEFAULT_GAME_RELEASE_INTERVAL);
    }

    public long getPlayerCreateInterval() {
        return getLong("player.create.interval", DEFAULT_PLAYER_CREATE_INTERVAL);
    }

    public long getPurchaseInterval() {
        return getLong("purchase.interval", DEFAULT_PURCHASE_INTERVAL);
    }

    public long getSessionInterval() {
        return getLong("session.interval", DEFAULT_SESSION_INTERVAL);
    }

    public long getRatingInterval() {
        return getLong("rating.interval", DEFAULT_RATING_INTERVAL);
    }

    public long getCrashInterval() {
        return getLong("crash.interval", DEFAULT_CRASH_INTERVAL);
    }

    public long getPatchInterval() {
        return getLong("patch.interval", DEFAULT_PATCH_INTERVAL);
    }

    public long getDlcInterval() {
        return getLong("dlc.interval", DEFAULT_DLC_INTERVAL);
    }

    public long getReviewInterval() {
        return getLong("review.interval", DEFAULT_REVIEW_INTERVAL);
    }

    // ========== DÉLAIS INITIAUX ==========

    public long getDependentInitialDelay() {
        return getLong("dependent.initial.delay", DEFAULT_DEPENDENT_INITIAL_DELAY);
    }

    public long getDependentPhase2Delay() {
        return getLong("dependent.phase2.delay", DEFAULT_DEPENDENT_PHASE2_DELAY);
    }

    public long getDependentPhase3Delay() {
        return getLong("dependent.phase3.delay", DEFAULT_DEPENDENT_PHASE3_DELAY);
    }

    // ========== SEUILS ==========

    public int getMinimumGames() {
        return getInt("minimum.games", DEFAULT_MINIMUM_GAMES);
    }

    public int getMinimumPlayers() {
        return getInt("minimum.players", DEFAULT_MINIMUM_PLAYERS);
    }

    // ========== STATS ==========

    public long getStatsInterval() {
        return getLong("stats.interval", DEFAULT_STATS_INTERVAL);
    }

    // ========== KAFKA ==========

    public String getBootstrapServers() {
        return props.getProperty("kafka.bootstrap.servers",
                System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"));
    }

    public String getSchemaRegistryUrl() {
        return props.getProperty("schema.registry.url",
                System.getenv().getOrDefault("SCHEMA_REGISTRY_URL", "http://localhost:8081"));
    }

    // ========== UTILITAIRES ==========

    private long getLong(String key, long defaultValue) {
        String value = props.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int getInt(String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public String toString() {
        return """
                SchedulerConfig {
                  Fréquences (ms):
                    - Game Release: %d
                    - Player Create: %d
                    - Purchase: %d
                    - Session: %d
                    - Rating: %d
                    - Crash: %d
                    - Patch: %d
                    - DLC: %d
                    - Review: %d
                  Délais (ms):
                    - Initial: %d
                    - Phase 2: %d
                    - Phase 3: %d
                  Seuils:
                    - Min Games: %d
                    - Min Players: %d
                  Kafka:
                    - Bootstrap: %s
                    - Schema Registry: %s
                }
                """.formatted(
                getGameReleaseInterval(), getPlayerCreateInterval(), getPurchaseInterval(),
                getSessionInterval(), getRatingInterval(), getCrashInterval(),
                getPatchInterval(), getDlcInterval(), getReviewInterval(),
                getDependentInitialDelay(), getDependentPhase2Delay(), getDependentPhase3Delay(),
                getMinimumGames(), getMinimumPlayers(),
                getBootstrapServers(), getSchemaRegistryUrl()
        );
    }
}
