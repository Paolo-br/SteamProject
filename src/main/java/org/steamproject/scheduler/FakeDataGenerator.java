package org.steamproject.scheduler;

import net.datafaker.Faker;
import org.steamproject.events.*;
import org.steamproject.scheduler.InMemoryDataStore.*;

import java.time.Instant;
import java.util.*;

/**
 * Génère des événements Avro avec des données réalistes via DataFaker.
 * Permet de simuler une activité de plateforme de jeux vidéo.
 */
public class FakeDataGenerator {

    private final Faker faker = new Faker();
    private final Random random = new Random();
    private InMemoryDataStore dataStore; // Reference to the data store for accessing publishers

    // Listes de valeurs possibles pour les données générées
    private static final List<String> PLATFORMS = List.of(
            "PC", "PS5", "PS4", "Xbox Series X", "Xbox One", "Nintendo Switch"
    );

    private static final List<String> DISTRIBUTIONS = List.of(
            "Steam", "Epic Games", "GOG", "PlayStation Store", "Xbox Store", "Nintendo eShop"
    );

    private static final List<String> GENRES = List.of(
            "Action", "RPG", "FPS", "Strategy", "Sports", "Adventure",
            "Simulation", "Racing", "Horror", "Puzzle", "Platformer", "MMO"
    );

    private static final List<String> ERROR_TYPES = List.of(
            "NullPointerException", "OutOfMemoryError", "SegmentationFault",
            "GraphicsDriverCrash", "NetworkTimeout", "SaveCorruption",
            "AudioDriverError", "TextureLoadFailure", "PhysicsEngineError"
    );

    /**
     * Sets the data store reference for accessing existing publishers.
     */
    public void setDataStore(InMemoryDataStore store) {
        this.dataStore = store;
    }

    // ========== ÉVÉNEMENTS DE BASE (Phase 1) ==========

    // Liste de titres de jeux vidéo réalistes
    private static final List<String> GAME_TITLES = List.of(
            "Shadow of the Colossus", "Final Fantasy XVI", "Cyberpunk 2078",
            "The Witcher 4", "Starfield Rising", "Dragon Age: Veilguard",
            "Mass Effect 5", "Elden Ring 2", "God of War: Ragnarok 2",
            "Horizon Forbidden West 2", "Spider-Man 3", "Assassin's Creed: Shadows",
            "Call of Duty: Black Ops 7", "Battlefield 2042 II", "GTA VI",
            "Red Dead Redemption 3", "Halo Infinite 2", "Forza Horizon 6",
            "The Elder Scrolls VII", "Fallout 5", "Doom Eternal 2"
    );

    /**
     * Génère un nouvel éditeur.
     */
    public PublisherInfo generatePublisher() {
        return new PublisherInfo(
                UUID.randomUUID().toString(),
                faker.company().name()
        );
    }

    /**
     * Génère un événement de sortie de jeu.
     * Utilise un éditeur existant du dataStore s'il y en a, sinon en crée un nouveau.
     */
    public GameReleasedEvent generateGameReleased() {
        String gameId = UUID.randomUUID().toString();
        
        // Réutilise un éditeur existant s'il y en a dans le dataStore
        String publisherId;
        String publisherName;
        if (dataStore != null && dataStore.hasPublishers()) {
            PublisherInfo publisher = dataStore.getRandomPublisher();
            publisherId = publisher.publisherId();
            publisherName = publisher.publisherName();
        } else {
            publisherId = UUID.randomUUID().toString();
            publisherName = faker.company().name();
        }
        
        String gameName = randomFrom(GAME_TITLES) + " " + (2024 + random.nextInt(5));

        return GameReleasedEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setGameId(gameId)
                .setGameName(gameName)
                .setPublisherId(publisherId)
                .setPublisherName(publisherName)
                .setPlatform(randomFrom(PLATFORMS))
                .setPlatforms(randomSublist(DISTRIBUTIONS, 1, 3))
                .setGenre(randomFrom(GENRES))
                .setInitialPrice(randomPrice(9.99, 69.99))
                .setInitialVersion("1.0.0")
                .setReleaseYear(2024 + random.nextInt(3))
                .setTimestamp(Instant.now().toEpochMilli())
                .build();
    }

    /**
     * Génère un événement de création de joueur.
     */
    public PlayerCreatedEvent generatePlayerCreated() {
        String username = faker.name().firstName().toLowerCase() + random.nextInt(9999);
        
        return PlayerCreatedEvent.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setUsername(username)
                .setEmail(faker.internet().emailAddress())
                .setFirstName(faker.name().firstName())
                .setLastName(faker.name().lastName())
                .setDateOfBirth(faker.date().birthday(13, 60).toString())
                .setRegistrationDate(Instant.now().toString())
                .setDistributionPlatformId(randomFrom(DISTRIBUTIONS))
                .setGdprConsent(true)
                .setGdprConsentDate(Instant.now().toString())
                .setTimestamp(Instant.now().toEpochMilli())
                .build();
    }

    // ========== ÉVÉNEMENTS DÉPENDANTS (Phase 2) ==========

    /**
     * Génère un événement d'achat de jeu.
     */
    public GamePurchaseEvent generatePurchase(GameInfo game, PlayerInfo player) {
        double discount = random.nextDouble() < 0.2 ? 0.8 : 1.0; // 20% de chance de promo
        SalesRegion[] regions = SalesRegion.values();

        return GamePurchaseEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setPurchaseId(UUID.randomUUID().toString())
                .setGameId(game.gameId())
                .setGameName(game.gameName())
                .setPlayerId(player.playerId())
                .setPlayerUsername(player.username())
                .setPlatform(game.platform())
                .setPublisherId(game.publisherId())
                .setPricePaid(Math.round(game.price() * discount * 100.0) / 100.0)
                .setRegion(regions[random.nextInt(regions.length)])
                .setTimestamp(Instant.now().toEpochMilli())
                .build();
    }

    /**
     * Génère un événement de session de jeu.
     */
    public GameSessionEvent generateSession(GameInfo game, PlayerInfo player) {
        SessionType[] sessionTypes = SessionType.values();

        return GameSessionEvent.newBuilder()
                .setSessionId(UUID.randomUUID().toString())
                .setGameId(game.gameId())
                .setGameName(game.gameName())
                .setPlayerId(player.playerId())
                .setPlayerUsername(player.username())
                .setSessionDuration(random.nextInt(180) + 10) // 10-190 minutes
                .setSessionType(sessionTypes[random.nextInt(sessionTypes.length)])
                .setTimestamp(Instant.now().toEpochMilli())
                .build();
    }

    /**
     * Génère un événement de notation de jeu.
     */
    public NewRatingEvent generateRating(GameInfo game, PlayerInfo player) {
        int rating = 1 + random.nextInt(5); // 1-5 étoiles
        int playtime = random.nextInt(200); // 0-200 heures

        NewRatingEvent.Builder builder = NewRatingEvent.newBuilder()
                .setGameId(game.gameId())
                .setGameName(game.gameName())
                .setPlayerId(player.playerId())
                .setPlayerUsername(player.username())
                .setRating(rating)
                .setPlaytime(playtime)
                .setIsRecommended(rating >= 3)
                .setTimestamp(Instant.now().toEpochMilli());

        // 60% de chance d'ajouter un commentaire
        if (random.nextDouble() < 0.6) {
            builder.setComment(faker.lorem().paragraph());
        }

        return builder.build();
    }

    /**
     * Génère un événement de crash/incident.
     */
    public CrashReportEvent generateCrash(GameInfo game, PlayerInfo player) {
        CrashSeverity[] severities = CrashSeverity.values();

        return CrashReportEvent.newBuilder()
                .setCrashId(UUID.randomUUID().toString())
                .setGameId(game.gameId())
                .setGameName(game.gameName())
                .setPlayerId(player.playerId())
                .setEditeurId(game.publisherId())
                .setEditeurName(game.publisherName())
                .setGameVersion(game.currentVersion())
                .setPlatform(game.platform())
                .setSeverity(severities[random.nextInt(severities.length)])
                .setErrorType(randomFrom(ERROR_TYPES))
                .setErrorMessage(faker.lorem().sentence())
                .setTimestamp(Instant.now().toEpochMilli())
                .build();
    }

    // ========== ÉVÉNEMENTS RARES (Phase 3) ==========

    /**
     * Génère un événement de publication de patch.
     */
    public PatchPublishedEvent generatePatch(GameInfo game) {
        String oldVersion = game.currentVersion();
        String newVersion = incrementVersion(oldVersion);

        List<Change> changes = new ArrayList<>();
        int numChanges = 1 + random.nextInt(5);
        PatchType[] patchTypes = PatchType.values();

        for (int i = 0; i < numChanges; i++) {
            changes.add(Change.newBuilder()
                    .setType(patchTypes[random.nextInt(patchTypes.length)])
                    .setDescription(faker.lorem().sentence())
                    .build());
        }

        return PatchPublishedEvent.newBuilder()
                .setGameId(game.gameId())
                .setGameName(game.gameName())
                .setPlatform(game.platform())
                .setOldVersion(oldVersion)
                .setNewVersion(newVersion)
                .setChangeLog(faker.lorem().paragraph())
                .setChanges(changes)
                .setTimestamp(Instant.now().toEpochMilli())
                .build();
    }

    /**
     * Génère un événement de publication de DLC.
     */
    public DlcPublishedEvent generateDlc(GameInfo game) {
        String dlcSuffix = randomFrom(List.of(
                "Expansion Pack", "Season Pass", "Deluxe Edition",
                "Story DLC", "Multiplayer Pack", "Cosmetic Bundle"
        ));

        return DlcPublishedEvent.newBuilder()
                .setDlcId(UUID.randomUUID().toString())
                .setGameId(game.gameId())
                .setPublisherId(game.publisherId())
                .setPlatform(game.platform())
                .setDlcName(game.gameName() + " - " + dlcSuffix)
                .setPrice(randomPrice(4.99, 29.99))
                .setSizeInMB(100 + random.nextInt(5000)) // DLC size between 100 MB and 5 GB
                .setReleaseTimestamp(Instant.now().toEpochMilli())
                .build();
    }

    /**
     * Génère un événement de review/avis publié.
     */
    public ReviewPublishedEvent generateReview(GameInfo game, PlayerInfo player) {
        int rating = 1 + random.nextInt(5); // Score 1-5

        ReviewPublishedEvent.Builder builder = ReviewPublishedEvent.newBuilder()
                .setReviewId(UUID.randomUUID().toString())
                .setGameId(game.gameId())
                .setPlayerId(player.playerId())
                .setPlayerUsername(player.username())
                .setRating(rating)
                .setIsSpoiler(random.nextDouble() < 0.1) // 10% chance de spoiler
                .setTimestamp(Instant.now().toEpochMilli());

        // 80% de chance d'ajouter un titre
        if (random.nextDouble() < 0.8) {
            builder.setTitle(faker.lorem().sentence(5));
        }

        // 70% de chance d'ajouter du texte
        if (random.nextDouble() < 0.7) {
            builder.setText(faker.lorem().paragraph());
        }

        return builder.build();
    }

    // ========== UTILITAIRES ==========

    private <T> T randomFrom(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }

    private List<String> randomSublist(List<String> list, int min, int max) {
        int count = min + random.nextInt(max - min + 1);
        List<String> shuffled = new ArrayList<>(list);
        Collections.shuffle(shuffled, random);
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }

    private double randomPrice(double min, double max) {
        double price = min + random.nextDouble() * (max - min);
        return Math.round(price * 100.0) / 100.0;
    }

    private String incrementVersion(String version) {
        String[] parts = version.split("\\.");
        if (parts.length == 3) {
            int patch = Integer.parseInt(parts[2]) + 1;
            return parts[0] + "." + parts[1] + "." + patch;
        }
        return version + ".1";
    }
}
