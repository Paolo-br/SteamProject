package org.steamproject.scheduler;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.steamproject.events.CrashReportEvent;
import org.steamproject.events.DlcPublishedEvent;
import org.steamproject.events.GamePurchaseEvent;
import org.steamproject.events.GameReleasedEvent;
import org.steamproject.events.GameSessionEvent;
import org.steamproject.events.NewRatingEvent;
import org.steamproject.events.PatchPublishedEvent;
import org.steamproject.events.PlayerCreatedEvent;
import org.steamproject.events.ReviewPublishedEvent;
import org.steamproject.ingestion.PublisherIngestion;
import org.steamproject.model.Publisher;
import org.steamproject.scheduler.InMemoryDataStore.*;
import org.steamproject.scheduler.InMemoryDataStore.GameInfo;
import org.steamproject.scheduler.InMemoryDataStore.PlayerInfo;
import org.steamproject.scheduler.InMemoryDataStore.PublisherInfo;
import org.steamproject.scheduler.InMemoryDataStore.PurchaseInfo;

import io.confluent.kafka.serializers.KafkaAvroSerializer;

/**
 * Orchestrateur central qui planifie la génération de tous les événements Kafka.
 * 
 * Architecture en 3 phases:
 * - Phase 1: Création des données de base (jeux et joueurs)
 * - Phase 2: Événements dépendants (achats, sessions, ratings, crashs)
 * - Phase 3: Événements rares (patches, DLCs, reviews)
 */
public class ScheduledEventOrchestrator {

    // ========== TOPICS KAFKA (doivent correspondre aux consumers existants) ==========
    private static final String TOPIC_GAME_RELEASED = "game-released-events";
    private static final String TOPIC_PLAYER_CREATED = "player-created-events";
    private static final String TOPIC_PURCHASE = "game-purchase-events";  // Corrigé!
    private static final String TOPIC_SESSION = "game-session-events";    // Corrigé!
    private static final String TOPIC_RATING = "new-rating-events";       // Corrigé!
    private static final String TOPIC_CRASH = "crash-report-events";      // Corrigé!
    private static final String TOPIC_PATCH = "patch-published-events";
    private static final String TOPIC_DLC = "dlc-published-events";
    private static final String TOPIC_REVIEW = "review-published-events";

    // ========== COMPOSANTS ==========
    private final SchedulerConfig config;
    private final ScheduledExecutorService scheduler;
    private final KafkaProducer<String, Object> producer;
    private final InMemoryDataStore dataStore;
    private final FakeDataGenerator generator;

    // ========== COMPTEURS POUR STATISTIQUES ==========
    private final AtomicInteger gameCount = new AtomicInteger(0);
    private final AtomicInteger playerCount = new AtomicInteger(0);
    private final AtomicInteger purchaseCount = new AtomicInteger(0);
    private final AtomicInteger sessionCount = new AtomicInteger(0);
    private final AtomicInteger ratingCount = new AtomicInteger(0);
    private final AtomicInteger crashCount = new AtomicInteger(0);
    private final AtomicInteger patchCount = new AtomicInteger(0);
    private final AtomicInteger dlcCount = new AtomicInteger(0);
    private final AtomicInteger reviewCount = new AtomicInteger(0);
    private final AtomicLong startTime = new AtomicLong(0);

    // ========== ÉTAT ==========
    private volatile boolean running = false;

    /**
     * Constructeur avec configuration par défaut.
     */
    public ScheduledEventOrchestrator() {
        this(new SchedulerConfig());
    }

    /**
     * Constructeur avec configuration personnalisée.
     */
    public ScheduledEventOrchestrator(SchedulerConfig config) {
        this.config = config;
        this.scheduler = Executors.newScheduledThreadPool(10, r -> {
            Thread t = new Thread(r, "EventOrchestrator-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
        this.producer = createProducer();
        this.dataStore = new InMemoryDataStore();
        this.generator = new FakeDataGenerator();
        this.generator.setDataStore(this.dataStore); // Connect generator to data store for publishers
    }

    /**
     * Crée et configure le producteur Kafka avec sérialisation Avro.
     */
    private KafkaProducer<String, Object> createProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
        props.put("schema.registry.url", config.getSchemaRegistryUrl());
        
        // Configuration pour la fiabilité
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        
        return new KafkaProducer<>(props);
    }

    // Configuration de la phase d'initialisation (quantités réduites)
    private static final int INIT_PUBLISHERS_COUNT = 20;  // Charger depuis le CSV!
    private static final int INIT_GAMES_COUNT = 10;
    private static final int INIT_PLAYERS_COUNT = 15;
    private static final int INIT_PURCHASES_COUNT = 25;
    private static final int INIT_SESSIONS_COUNT = 20;
    private static final int INIT_RATINGS_COUNT = 15;
    private static final int INIT_CRASHS_COUNT = 8;
    private static final int INIT_PATCHES_COUNT = 5;
    private static final int INIT_DLCS_COUNT = 5;
    private static final int INIT_REVIEWS_COUNT = 10;

    /**
     * Démarre la génération planifiée de tous les événements.
     */
    public void start() {
        if (running) {
            System.out.println("⚠️ L'orchestrateur est déjà en cours d'exécution.");
            return;
        }

        running = true;
        startTime.set(System.currentTimeMillis());

        System.out.println("\n╔════════════════════════════════════════════════════════════╗");
        System.out.println("║      🚀 DÉMARRAGE DE L'ORCHESTRATEUR D'ÉVÉNEMENTS 🚀       ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");
        System.out.println(config);

        // ========== PHASE 0 : INITIALISATION RAPIDE ==========
        System.out.println("\n╔════════════════════════════════════════════════════════════╗");
        System.out.println("║       📦 PHASE 0: PEUPLEMENT INITIAL DE LA BASE 📦        ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");
        
        runInitializationPhase();
        
        System.out.println("\n✅ Phase d'initialisation terminée !");
        printStats();
        
        System.out.println("\n╔════════════════════════════════════════════════════════════╗");
        System.out.println("║     🔄 PASSAGE EN MODE CONTINU (événements planifiés) 🔄   ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        // ========== PHASE 1 : Données de base ==========
        System.out.println("📌 Phase 1: Création des données de base (continu)...");

        // Créer des jeux fréquemment
        scheduler.scheduleAtFixedRate(
                this::produceGameReleased,
                0,
                config.getGameReleaseInterval(),
                TimeUnit.MILLISECONDS
        );

        // Créer des joueurs moins souvent
        scheduler.scheduleAtFixedRate(
                this::producePlayerCreated,
                0,
                config.getPlayerCreateInterval(),
                TimeUnit.MILLISECONDS
        );

        // ========== PHASE 2 : Événements dépendants ==========
        System.out.println("📌 Phase 2: Événements dépendants (délai: " + 
                config.getDependentInitialDelay() + "ms)...");

        // Achats
        scheduler.scheduleAtFixedRate(
                this::producePurchase,
                config.getDependentInitialDelay(),
                config.getPurchaseInterval(),
                TimeUnit.MILLISECONDS
        );

        // Sessions de jeu
        scheduler.scheduleAtFixedRate(
                this::produceSession,
                config.getDependentInitialDelay(),
                config.getSessionInterval(),
                TimeUnit.MILLISECONDS
        );

        // Ratings
        scheduler.scheduleAtFixedRate(
                this::produceRating,
                config.getDependentPhase2Delay(),
                config.getRatingInterval(),
                TimeUnit.MILLISECONDS
        );

        // Crashs
        scheduler.scheduleAtFixedRate(
                this::produceCrash,
                config.getDependentPhase2Delay(),
                config.getCrashInterval(),
                TimeUnit.MILLISECONDS
        );

        // ========== PHASE 3 : Événements rares ==========
        System.out.println("📌 Phase 3: Événements rares (délai: " + 
                config.getDependentPhase3Delay() + "ms)...");

        // Patches
        scheduler.scheduleAtFixedRate(
                this::producePatch,
                config.getDependentPhase3Delay(),
                config.getPatchInterval(),
                TimeUnit.MILLISECONDS
        );

        // DLCs
        scheduler.scheduleAtFixedRate(
                this::produceDlc,
                config.getDependentPhase3Delay() * 2,
                config.getDlcInterval(),
                TimeUnit.MILLISECONDS
        );

        // Reviews
        scheduler.scheduleAtFixedRate(
                this::produceReview,
                config.getDependentPhase2Delay(),
                config.getReviewInterval(),
                TimeUnit.MILLISECONDS
        );

        // ========== MONITORING ==========
        scheduler.scheduleAtFixedRate(
                this::printStats,
                config.getStatsInterval(),
                config.getStatsInterval(),
                TimeUnit.MILLISECONDS
        );

        System.out.println("\n✅ Orchestrateur démarré avec succès !\n");
        System.out.println("📊 Les statistiques seront affichées toutes les " + 
                (config.getStatsInterval() / 1000) + " secondes.\n");
        System.out.println("💡 Appuyez sur Ctrl+C pour arrêter.\n");
    }

    // ========== PHASE D'INITIALISATION ==========

    /**
     * Exécute la phase d'initialisation pour peupler rapidement la base de données.
     * Cette phase crée séquentiellement : éditeurs → jeux → joueurs → achats → sessions → ratings → etc.
     */
    private void runInitializationPhase() {
        // Charger les éditeurs depuis le CSV pour utiliser les mêmes IDs que l'UI
        System.out.println("🏢 Chargement des éditeurs depuis le CSV...");
        try {
            PublisherIngestion ingestion = new PublisherIngestion();
            List<Publisher> csvPublishers = ingestion.readAll();
            // Prendre un échantillon aléatoire d'éditeurs du CSV
            java.util.Collections.shuffle(csvPublishers);
            int count = Math.min(INIT_PUBLISHERS_COUNT, csvPublishers.size());
            for (int i = 0; i < count; i++) {
                Publisher p = csvPublishers.get(i);
                PublisherInfo pub = new PublisherInfo(p.getId(), p.getName());
                dataStore.addPublisher(pub);
            }
        } catch (Exception e) {
            System.out.println("   ⚠️ Impossible de charger les éditeurs du CSV, génération aléatoire...");
            for (int i = 0; i < INIT_PUBLISHERS_COUNT; i++) {
                PublisherInfo pub = generator.generatePublisher();
                dataStore.addPublisher(pub);
            }
        }
        System.out.println("   ✅ " + dataStore.getPublisherCount() + " éditeurs chargés\n");

        System.out.println("🎮 Création de " + INIT_GAMES_COUNT + " jeux...");
        for (int i = 0; i < INIT_GAMES_COUNT; i++) {
            produceGameReleasedSync();
            sleep(50); // Petit délai pour éviter de surcharger Kafka
        }
        System.out.println("   ✅ " + gameCount.get() + " jeux créés\n");

        System.out.println("👤 Création de " + INIT_PLAYERS_COUNT + " joueurs...");
        for (int i = 0; i < INIT_PLAYERS_COUNT; i++) {
            producePlayerCreatedSync();
            sleep(50);
        }
        System.out.println("   ✅ " + playerCount.get() + " joueurs créés\n");

        System.out.println("💰 Création de " + INIT_PURCHASES_COUNT + " achats...");
        for (int i = 0; i < INIT_PURCHASES_COUNT; i++) {
            producePurchaseSync();
            sleep(30);
        }
        System.out.println("   ✅ " + purchaseCount.get() + " achats créés\n");

        System.out.println("🎯 Création de " + INIT_SESSIONS_COUNT + " sessions de jeu...");
        for (int i = 0; i < INIT_SESSIONS_COUNT; i++) {
            produceSessionSync();
            sleep(30);
        }
        System.out.println("   ✅ " + sessionCount.get() + " sessions créées\n");

        System.out.println("⭐ Création de " + INIT_RATINGS_COUNT + " évaluations...");
        for (int i = 0; i < INIT_RATINGS_COUNT; i++) {
            produceRatingSync();
            sleep(30);
        }
        System.out.println("   ✅ " + ratingCount.get() + " évaluations créées\n");

        System.out.println("💥 Création de " + INIT_CRASHS_COUNT + " rapports de crash...");
        for (int i = 0; i < INIT_CRASHS_COUNT; i++) {
            produceCrashSync();
            sleep(30);
        }
        System.out.println("   ✅ " + crashCount.get() + " crashs créés\n");

        System.out.println("🔧 Création de " + INIT_PATCHES_COUNT + " patches...");
        for (int i = 0; i < INIT_PATCHES_COUNT; i++) {
            producePatchSync();
            sleep(50);
        }
        System.out.println("   ✅ " + patchCount.get() + " patches créés\n");

        System.out.println("📦 Création de " + INIT_DLCS_COUNT + " DLCs...");
        for (int i = 0; i < INIT_DLCS_COUNT; i++) {
            produceDlcSync();
            sleep(50);
        }
        System.out.println("   ✅ " + dlcCount.get() + " DLCs créés\n");

        System.out.println("📝 Création de " + INIT_REVIEWS_COUNT + " avis...");
        for (int i = 0; i < INIT_REVIEWS_COUNT; i++) {
            produceReviewSync();
            sleep(30);
        }
        System.out.println("   ✅ " + reviewCount.get() + " avis créés\n");

        // Flush pour s'assurer que tout est envoyé
        producer.flush();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ========== PRODUCTEURS SYNCHRONES (pour l'initialisation) ==========

    private void produceGameReleasedSync() {
        try {
            GameReleasedEvent event = generator.generateGameReleased();
            dataStore.addGame(new GameInfo(
                    event.getGameId().toString(),
                    event.getGameName().toString(),
                    event.getPublisherId().toString(),
                    event.getPublisherName().toString(),
                    event.getPlatform().toString(),
                    event.getGenre().toString(),
                    event.getInitialPrice(),
                    event.getInitialVersion().toString()
            ));
            producer.send(new ProducerRecord<>(TOPIC_GAME_RELEASED, 
                    event.getGameId().toString(), event));
            gameCount.incrementAndGet();
        } catch (Exception e) {
            System.err.println("❌ Erreur: " + e.getMessage());
        }
    }

    private void producePlayerCreatedSync() {
        try {
            PlayerCreatedEvent event = generator.generatePlayerCreated();
            dataStore.addPlayer(new PlayerInfo(
                    event.getId().toString(),
                    event.getUsername().toString(),
                    event.getEmail() != null ? event.getEmail().toString() : null,
                    event.getDistributionPlatformId() != null ? 
                            event.getDistributionPlatformId().toString() : null
            ));
            producer.send(new ProducerRecord<>(TOPIC_PLAYER_CREATED, 
                    event.getId().toString(), event));
            playerCount.incrementAndGet();
        } catch (Exception e) {
            System.err.println("❌ Erreur: " + e.getMessage());
        }
    }

    private void producePurchaseSync() {
        if (!dataStore.hasMinimumData()) return;
        try {
            GameInfo game = dataStore.getRandomGame();
            PlayerInfo player = dataStore.getRandomPlayer();
            GamePurchaseEvent event = generator.generatePurchase(game, player);
            dataStore.addPurchase(new PurchaseInfo(
                    event.getPurchaseId().toString(),
                    game.gameId(), game.gameName(),
                    player.playerId(), player.username(),
                    event.getPricePaid()
            ));
            producer.send(new ProducerRecord<>(TOPIC_PURCHASE, player.playerId(), event));
            purchaseCount.incrementAndGet();
        } catch (Exception e) {
            System.err.println("❌ Erreur: " + e.getMessage());
        }
    }

    private void produceSessionSync() {
        if (!dataStore.hasPurchases()) return;
        try {
            // Utilise un achat existant pour s'assurer que le joueur possède le jeu
            PurchaseInfo purchase = dataStore.getRandomPurchase();
            GameInfo game = findGameById(purchase.gameId());
            PlayerInfo player = findPlayerById(purchase.playerId());
            if (game == null || player == null) return;
            
            GameSessionEvent event = generator.generateSession(game, player);
            producer.send(new ProducerRecord<>(TOPIC_SESSION, player.playerId(), event));
            sessionCount.incrementAndGet();
        } catch (Exception e) {
            System.err.println("❌ Erreur session: " + e.getMessage());
        }
    }

    private void produceRatingSync() {
        if (!dataStore.hasPurchases()) return;
        try {
            // Utilise un achat existant pour s'assurer que le joueur possède le jeu
            PurchaseInfo purchase = dataStore.getRandomPurchase();
            GameInfo game = findGameById(purchase.gameId());
            PlayerInfo player = findPlayerById(purchase.playerId());
            if (game == null || player == null) return;
            
            NewRatingEvent event = generator.generateRating(game, player);
            producer.send(new ProducerRecord<>(TOPIC_RATING, game.gameId(), event));
            ratingCount.incrementAndGet();
        } catch (Exception e) {
            System.err.println("❌ Erreur rating: " + e.getMessage());
        }
    }

    private void produceCrashSync() {
        if (!dataStore.hasPurchases()) return;
        try {
            // Utilise un achat existant pour s'assurer que le joueur possède le jeu
            PurchaseInfo purchase = dataStore.getRandomPurchase();
            GameInfo game = findGameById(purchase.gameId());
            PlayerInfo player = findPlayerById(purchase.playerId());
            if (game == null || player == null) return;
            
            CrashReportEvent event = generator.generateCrash(game, player);
            producer.send(new ProducerRecord<>(TOPIC_CRASH, game.gameId(), event));
            crashCount.incrementAndGet();
        } catch (Exception e) {
            System.err.println("❌ Erreur crash: " + e.getMessage());
        }
    }
    
    // Méthodes utilitaires pour trouver les entités par ID
    private GameInfo findGameById(String gameId) {
        return dataStore.getAllGames().stream()
                .filter(g -> g.gameId().equals(gameId))
                .findFirst().orElse(null);
    }
    
    private PlayerInfo findPlayerById(String playerId) {
        return dataStore.getAllPlayers().stream()
                .filter(p -> p.playerId().equals(playerId))
                .findFirst().orElse(null);
    }

    private void producePatchSync() {
        if (!dataStore.hasGames()) return;
        try {
            GameInfo game = dataStore.getRandomGame();
            PatchPublishedEvent event = generator.generatePatch(game);
            producer.send(new ProducerRecord<>(TOPIC_PATCH, game.gameId(), event));
            patchCount.incrementAndGet();
        } catch (Exception e) {
            System.err.println("❌ Erreur: " + e.getMessage());
        }
    }

    private void produceDlcSync() {
        if (!dataStore.hasGames()) return;
        try {
            GameInfo game = dataStore.getRandomGame();
            DlcPublishedEvent event = generator.generateDlc(game);
            producer.send(new ProducerRecord<>(TOPIC_DLC, game.gameId(), event));
            dlcCount.incrementAndGet();
        } catch (Exception e) {
            System.err.println("❌ Erreur: " + e.getMessage());
        }
    }

    private void produceReviewSync() {
        if (!dataStore.hasMinimumData()) return;
        try {
            GameInfo game = dataStore.getRandomGame();
            PlayerInfo player = dataStore.getRandomPlayer();
            ReviewPublishedEvent event = generator.generateReview(game, player);
            producer.send(new ProducerRecord<>(TOPIC_REVIEW, game.gameId(), event));
            reviewCount.incrementAndGet();
        } catch (Exception e) {
            System.err.println("❌ Erreur: " + e.getMessage());
        }
    }

    // ========== PRODUCTEURS D'ÉVÉNEMENTS ==========

    /**
     * Produit un événement GameReleased.
     */
    private void produceGameReleased() {
        if (!running) return;

        try {
            GameReleasedEvent event = generator.generateGameReleased();

            // Stocker en mémoire pour les événements dépendants
            dataStore.addGame(new GameInfo(
                    event.getGameId().toString(),
                    event.getGameName().toString(),
                    event.getPublisherId().toString(),
                    event.getPublisherName().toString(),
                    event.getPlatform().toString(),
                    event.getGenre().toString(),
                    event.getInitialPrice(),
                    event.getInitialVersion().toString()
            ));

            producer.send(new ProducerRecord<>(TOPIC_GAME_RELEASED, 
                    event.getGameId().toString(), event), this::handleSendResult);

            int count = gameCount.incrementAndGet();
            System.out.println("🎮 [" + count + "] Jeu créé: " + event.getGameName() + 
                    " (" + event.getGenre() + ", " + event.getInitialPrice() + "€)");

        } catch (Exception e) {
            System.err.println("❌ Erreur création jeu: " + e.getMessage());
        }
    }

    /**
     * Produit un événement PlayerCreated.
     */
    private void producePlayerCreated() {
        if (!running) return;

        try {
            PlayerCreatedEvent event = generator.generatePlayerCreated();

            // Stocker en mémoire
            dataStore.addPlayer(new PlayerInfo(
                    event.getId().toString(),
                    event.getUsername().toString(),
                    event.getEmail() != null ? event.getEmail().toString() : null,
                    event.getDistributionPlatformId() != null ? 
                            event.getDistributionPlatformId().toString() : null
            ));

            producer.send(new ProducerRecord<>(TOPIC_PLAYER_CREATED, 
                    event.getId().toString(), event), this::handleSendResult);

            int count = playerCount.incrementAndGet();
            System.out.println("👤 [" + count + "] Joueur créé: " + event.getUsername() + 
                    " (" + event.getDistributionPlatformId() + ")");

        } catch (Exception e) {
            System.err.println("❌ Erreur création joueur: " + e.getMessage());
        }
    }

    /**
     * Produit un événement GamePurchase.
     */
    private void producePurchase() {
        if (!running || !dataStore.hasMinimumData()) {
            if (!dataStore.hasMinimumData()) {
                System.out.println("⏳ En attente de données suffisantes (jeux: " + 
                        dataStore.getGameCount() + ", joueurs: " + dataStore.getPlayerCount() + ")...");
            }
            return;
        }

        try {
            GameInfo game = dataStore.getRandomGame();
            PlayerInfo player = dataStore.getRandomPlayer();

            GamePurchaseEvent event = generator.generatePurchase(game, player);

            // Stocker l'achat
            dataStore.addPurchase(new PurchaseInfo(
                    event.getPurchaseId().toString(),
                    game.gameId(),
                    game.gameName(),
                    player.playerId(),
                    player.username(),
                    event.getPricePaid()
            ));

            producer.send(new ProducerRecord<>(TOPIC_PURCHASE, 
                    player.playerId(), event), this::handleSendResult);

            int count = purchaseCount.incrementAndGet();
            System.out.println("💰 [" + count + "] Achat: " + player.username() + 
                    " → " + game.gameName() + " (" + event.getPricePaid() + "€)");

        } catch (Exception e) {
            System.err.println("❌ Erreur achat: " + e.getMessage());
        }
    }

    /**
     * Produit un événement GameSession.
     */
    private void produceSession() {
        if (!running || !dataStore.hasPurchases()) return;

        try {
            // Utilise un achat existant pour s'assurer que le joueur possède le jeu
            PurchaseInfo purchase = dataStore.getRandomPurchase();
            GameInfo game = findGameById(purchase.gameId());
            PlayerInfo player = findPlayerById(purchase.playerId());
            if (game == null || player == null) return;

            GameSessionEvent event = generator.generateSession(game, player);

            producer.send(new ProducerRecord<>(TOPIC_SESSION, 
                    player.playerId(), event), this::handleSendResult);

            int count = sessionCount.incrementAndGet();
            System.out.println("🎯 [" + count + "] Session: " + player.username() + 
                    " joue à " + game.gameName() + " (" + event.getSessionDuration() + " min)");

        } catch (Exception e) {
            System.err.println("❌ Erreur session: " + e.getMessage());
        }
    }

    /**
     * Produit un événement NewRating.
     */
    private void produceRating() {
        if (!running || !dataStore.hasPurchases()) return;

        try {
            // Utilise un achat existant pour s'assurer que le joueur possède le jeu
            PurchaseInfo purchase = dataStore.getRandomPurchase();
            GameInfo game = findGameById(purchase.gameId());
            PlayerInfo player = findPlayerById(purchase.playerId());
            if (game == null || player == null) return;

            NewRatingEvent event = generator.generateRating(game, player);

            producer.send(new ProducerRecord<>(TOPIC_RATING, 
                    game.gameId(), event), this::handleSendResult);

            int count = ratingCount.incrementAndGet();
            String stars = "⭐".repeat(event.getRating());
            System.out.println("⭐ [" + count + "] Rating: " + player.username() + 
                    " note " + game.gameName() + " " + stars);

        } catch (Exception e) {
            System.err.println("❌ Erreur rating: " + e.getMessage());
        }
    }

    /**
     * Produit un événement CrashReport.
     */
    private void produceCrash() {
        if (!running || !dataStore.hasPurchases()) return;

        try {
            // Utilise un achat existant pour s'assurer que le joueur possède le jeu
            PurchaseInfo purchase = dataStore.getRandomPurchase();
            GameInfo game = findGameById(purchase.gameId());
            PlayerInfo player = findPlayerById(purchase.playerId());
            if (game == null || player == null) return;

            CrashReportEvent event = generator.generateCrash(game, player);

            producer.send(new ProducerRecord<>(TOPIC_CRASH, 
                    game.gameId(), event), this::handleSendResult);

            int count = crashCount.incrementAndGet();
            System.out.println("💥 [" + count + "] Crash: " + game.gameName() + 
                    " [" + event.getSeverity() + "] - " + event.getErrorType());

        } catch (Exception e) {
            System.err.println("❌ Erreur crash: " + e.getMessage());
        }
    }

    /**
     * Produit un événement PatchPublished.
     */
    private void producePatch() {
        if (!running || !dataStore.hasGames()) return;

        try {
            GameInfo game = dataStore.getRandomGame();

            PatchPublishedEvent event = generator.generatePatch(game);

            producer.send(new ProducerRecord<>(TOPIC_PATCH, 
                    game.gameId(), event), this::handleSendResult);

            int count = patchCount.incrementAndGet();
            System.out.println("🔧 [" + count + "] Patch: " + game.gameName() + 
                    " " + event.getOldVersion() + " → " + event.getNewVersion());

        } catch (Exception e) {
            System.err.println("❌ Erreur patch: " + e.getMessage());
        }
    }

    /**
     * Produit un événement DlcPublished.
     */
    private void produceDlc() {
        if (!running || !dataStore.hasGames()) return;

        try {
            GameInfo game = dataStore.getRandomGame();

            DlcPublishedEvent event = generator.generateDlc(game);

            producer.send(new ProducerRecord<>(TOPIC_DLC, 
                    game.gameId(), event), this::handleSendResult);

            int count = dlcCount.incrementAndGet();
            System.out.println("📦 [" + count + "] DLC: " + event.getDlcName() + 
                    " (" + event.getPrice() + "€)");

        } catch (Exception e) {
            System.err.println("❌ Erreur DLC: " + e.getMessage());
        }
    }

    /**
     * Produit un événement ReviewPublished.
     */
    private void produceReview() {
        if (!running || !dataStore.hasMinimumData()) return;

        try {
            GameInfo game = dataStore.getRandomGame();
            PlayerInfo player = dataStore.getRandomPlayer();

            ReviewPublishedEvent event = generator.generateReview(game, player);

            producer.send(new ProducerRecord<>(TOPIC_REVIEW, 
                    game.gameId(), event), this::handleSendResult);

            int count = reviewCount.incrementAndGet();
            String stars = "⭐".repeat(event.getRating());
            System.out.println("📝 [" + count + "] Review: " + player.username() + 
                    " sur " + game.gameName() + " " + stars);

        } catch (Exception e) {
            System.err.println("❌ Erreur review: " + e.getMessage());
        }
    }

    /**
     * Callback pour gérer les résultats d'envoi Kafka.
     */
    private void handleSendResult(RecordMetadata metadata, Exception exception) {
        if (exception != null) {
            System.err.println("❌ Erreur Kafka: " + exception.getMessage());
        }
    }

    /**
     * Affiche les statistiques de l'orchestrateur.
     */
    private void printStats() {
        long elapsed = (System.currentTimeMillis() - startTime.get()) / 1000;
        long minutes = elapsed / 60;
        long seconds = elapsed % 60;

        System.out.println("\n╔════════════════════════════════════════════════════════════╗");
        System.out.println("║                    📊 STATISTIQUES                          ║");
        System.out.println("╠════════════════════════════════════════════════════════════╣");
        System.out.printf("║  ⏱️  Temps écoulé: %02d:%02d                                    ║%n", minutes, seconds);
        System.out.println("╠════════════════════════════════════════════════════════════╣");
        System.out.printf("║  🎮 Jeux créés:      %6d    👤 Joueurs créés:  %6d     ║%n", 
                gameCount.get(), playerCount.get());
        System.out.printf("║  💰 Achats:          %6d    🎯 Sessions:       %6d     ║%n", 
                purchaseCount.get(), sessionCount.get());
        System.out.printf("║  ⭐ Ratings:         %6d    💥 Crashs:         %6d     ║%n", 
                ratingCount.get(), crashCount.get());
        System.out.printf("║  🔧 Patches:         %6d    📦 DLCs:           %6d     ║%n", 
                patchCount.get(), dlcCount.get());
        System.out.printf("║  📝 Reviews:         %6d                                  ║%n", reviewCount.get());
        System.out.println("╠════════════════════════════════════════════════════════════╣");
        int total = gameCount.get() + playerCount.get() + purchaseCount.get() + 
                sessionCount.get() + ratingCount.get() + crashCount.get() + 
                patchCount.get() + dlcCount.get() + reviewCount.get();
        double rate = elapsed > 0 ? (double) total / elapsed : 0;
        System.out.printf("║  📈 Total événements: %6d (%.2f evt/s)                   ║%n", total, rate);
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");
    }

    /**
     * Arrête proprement l'orchestrateur.
     */
    public void stop() {
        if (!running) return;

        running = false;
        System.out.println("\n🛑 Arrêt de l'orchestrateur...");

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                System.out.println("⚠️ Arrêt forcé du scheduler.");
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        producer.flush();
        producer.close();

        printStats();
        System.out.println("✅ Orchestrateur arrêté proprement.\n");
    }

    /**
     * Vérifie si l'orchestrateur est en cours d'exécution.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Retourne le DataStore pour consultation externe.
     */
    public InMemoryDataStore getDataStore() {
        return dataStore;
    }

    // ========== MAIN ==========

    public static void main(String[] args) {
        System.out.println("\n" +
                "╔═══════════════════════════════════════════════════════════════╗\n" +
                "║     🎮 STEAM PROJECT - EVENT ORCHESTRATOR 🎮                 ║\n" +
                "║     Générateur d'événements Kafka planifiés                   ║\n" +
                "╚═══════════════════════════════════════════════════════════════╝\n");

        // Charger configuration
        SchedulerConfig config;
        if (args.length > 0) {
            try {
                config = new SchedulerConfig(args[0]);
                System.out.println("📄 Configuration chargée depuis: " + args[0]);
            } catch (Exception e) {
                System.out.println("⚠️ Impossible de charger " + args[0] + ", utilisation des valeurs par défaut.");
                config = new SchedulerConfig();
            }
        } else {
            config = new SchedulerConfig();
            System.out.println("📄 Utilisation de la configuration par défaut.");
        }

        ScheduledEventOrchestrator orchestrator = new ScheduledEventOrchestrator(config);

        // Shutdown hook pour arrêt propre (Ctrl+C)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n⚠️ Signal d'arrêt reçu...");
            orchestrator.stop();
        }, "ShutdownHook"));

        // Démarrer l'orchestrateur
        orchestrator.start();

        // Garder l'application en vie
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            orchestrator.stop();
            Thread.currentThread().interrupt();
        }
    }
}
