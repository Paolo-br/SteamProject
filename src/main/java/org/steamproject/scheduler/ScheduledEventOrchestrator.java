package org.steamproject.scheduler;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.steamproject.events.*;
import org.steamproject.scheduler.InMemoryDataStore.*;

import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
    private static final String TOPIC_PURCHASE = "game-purchase-events";
    private static final String TOPIC_SESSION = "game-session-events";
    private static final String TOPIC_RATING = "new-rating-events";
    private static final String TOPIC_CRASH = "crash-report-events";
    private static final String TOPIC_PATCH = "patch-published-events";
    private static final String TOPIC_DLC = "dlc-published-events";
    private static final String TOPIC_DLC_PURCHASE = "dlc-purchase-events";
    private static final String TOPIC_REVIEW = "review-published-events";
    private static final String TOPIC_REVIEW_VOTE = "review-voted-events";

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
    private final AtomicInteger dlcPurchaseCount = new AtomicInteger(0);
    private final AtomicInteger reviewCount = new AtomicInteger(0);
    private final AtomicInteger reviewVoteCount = new AtomicInteger(0);
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
            Thread t = new Thread(r, "scheduler-event-producer");
            t.setDaemon(true);
            return t;
        });
        this.producer = createProducer();
        this.dataStore = new InMemoryDataStore();
        this.generator = new FakeDataGenerator();
        this.generator.setDataStore(this.dataStore); // Connect generator to data store for publishers
        
        // Charger les éditeurs existants depuis le CSV pour cohérence des IDs
        loadPublishersFromCsv();
    }
    
    /**
     * Charge les éditeurs depuis le fichier CSV (vgsales.csv) pour assurer
     * la cohérence des IDs entre le scheduler et les projections REST.
     */
    private void loadPublishersFromCsv() {
        try {
            var ingestion = new org.steamproject.ingestion.PublisherIngestion();
            var publishers = ingestion.readAll();
            System.out.println("📚 Chargement de " + publishers.size() + " éditeurs depuis le CSV...");
            for (var pub : publishers) {
                dataStore.addPublisher(new InMemoryDataStore.PublisherInfo(
                    pub.getId(),
                    pub.getName()
                ));
            }
            System.out.println("   ✅ " + dataStore.getPublisherCount() + " éditeurs chargés dans le dataStore\n");
        } catch (Exception e) {
            System.err.println("⚠️ Impossible de charger les éditeurs depuis le CSV: " + e.getMessage());
            e.printStackTrace();
        }
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
    private static final int INIT_PUBLISHERS_COUNT = 5;  // Créer d'abord les éditeurs!
    private static final int INIT_GAMES_COUNT = 10;
    private static final int INIT_PLAYERS_COUNT = 15;
    private static final int INIT_PURCHASES_COUNT = 25;
    private static final int INIT_SESSIONS_COUNT = 20;
    private static final int INIT_RATINGS_COUNT = 15;
    private static final int INIT_CRASHS_COUNT = 8;
    private static final int INIT_PATCHES_COUNT = 5;
    private static final int INIT_DLCS_COUNT = 5;
    private static final int INIT_DLC_PURCHASES_COUNT = 8;
    private static final int INIT_REVIEWS_COUNT = 10;
    private static final int INIT_REVIEW_VOTES_COUNT = 15;

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

        // Achats de DLC (contrainte Kafka Streams: le joueur doit posséder le jeu de base)
        scheduler.scheduleAtFixedRate(
                this::produceDlcPurchase,
                config.getDependentPhase3Delay() * 2 + 5000, // Après les premiers DLCs
                config.getDlcInterval() / 2, // Plus fréquent que la publication de DLC
                TimeUnit.MILLISECONDS
        );

        // Reviews
        scheduler.scheduleAtFixedRate(
                this::produceReview,
                config.getDependentPhase2Delay(),
                config.getReviewInterval(),
                TimeUnit.MILLISECONDS
        );

        // Votes sur les reviews (fréquents)
        scheduler.scheduleAtFixedRate(
                this::produceReviewVote,
                config.getDependentPhase2Delay() + 5000, // Après les premières reviews
                config.getReviewVoteInterval(),
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

        System.out.println("🎮 Création de " + INIT_GAMES_COUNT + " jeux...");
        for (int i = 0; i < INIT_GAMES_COUNT; i++) {
            produceGameReleasedSync();
        }
        System.out.println("   ✅ " + gameCount.get() + " jeux créés\n");

        System.out.println("👤 Création de " + INIT_PLAYERS_COUNT + " joueurs...");
        for (int i = 0; i < INIT_PLAYERS_COUNT; i++) {
            producePlayerCreatedSync();
        }
        System.out.println("   ✅ " + playerCount.get() + " joueurs créés\n");

        System.out.println("💰 Création de " + INIT_PURCHASES_COUNT + " achats...");
        for (int i = 0; i < INIT_PURCHASES_COUNT; i++) {
            producePurchaseSync();
        }
        System.out.println("   ✅ " + purchaseCount.get() + " achats créés\n");

        System.out.println("🎯 Création de " + INIT_SESSIONS_COUNT + " sessions de jeu...");
        for (int i = 0; i < INIT_SESSIONS_COUNT; i++) {
            produceSessionSync();
        }
        System.out.println("   ✅ " + sessionCount.get() + " sessions créées\n");

        System.out.println("⭐ Création de " + INIT_RATINGS_COUNT + " évaluations...");
        for (int i = 0; i < INIT_RATINGS_COUNT; i++) {
            produceRatingSync();
        }
        System.out.println("   ✅ " + ratingCount.get() + " évaluations créées\n");

        System.out.println("💥 Création de " + INIT_CRASHS_COUNT + " rapports de crash...");
        for (int i = 0; i < INIT_CRASHS_COUNT; i++) {
            produceCrashSync();
        }
        System.out.println("   ✅ " + crashCount.get() + " crashs créés\n");

        System.out.println("🔧 Création de " + INIT_PATCHES_COUNT + " patches...");
        for (int i = 0; i < INIT_PATCHES_COUNT; i++) {
            producePatchSync();
        }
        System.out.println("   ✅ " + patchCount.get() + " patches créés\n");

        System.out.println("📦 Création de " + INIT_DLCS_COUNT + " DLCs...");
        for (int i = 0; i < INIT_DLCS_COUNT; i++) {
            produceDlcSync();
        }
        System.out.println("   ✅ " + dlcCount.get() + " DLCs créés\n");

        System.out.println("🎁 Création de " + INIT_DLC_PURCHASES_COUNT + " achats de DLC (contrainte: jeu de base requis)...");
        for (int i = 0; i < INIT_DLC_PURCHASES_COUNT; i++) {
            produceDlcPurchaseSync();
        }
        System.out.println("   ✅ " + dlcPurchaseCount.get() + " achats de DLC créés\n");

        System.out.println("📝 Création de " + INIT_REVIEWS_COUNT + " avis...");
        for (int i = 0; i < INIT_REVIEWS_COUNT; i++) {
            produceReviewSync();
        }
        System.out.println("   ✅ " + reviewCount.get() + " avis créés\n");

        System.out.println("👍 Création de " + INIT_REVIEW_VOTES_COUNT + " votes sur les avis...");
        for (int i = 0; i < INIT_REVIEW_VOTES_COUNT; i++) {
            produceReviewVoteSync();
        }
        System.out.println("   ✅ " + reviewVoteCount.get() + " votes créés\n");

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
            GameReleasedEvent evt = generator.generateGameReleased();
            producer.send(new ProducerRecord<>(TOPIC_GAME_RELEASED, evt.getGameId().toString(), evt)).get();
            
            // Stocker en mémoire
            dataStore.addGame(new GameInfo(
                    evt.getGameId().toString(),
                    evt.getGameName().toString(),
                    evt.getPublisherId().toString(),
                    evt.getPublisherName().toString(),
                    evt.getPlatform().toString(),
                    evt.getGenre().toString(),
                    evt.getInitialPrice(),
                    evt.getInitialVersion().toString()
            ));
            gameCount.incrementAndGet();
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la création du jeu: " + e.getMessage());
        }
    }

    private void producePlayerCreatedSync() {
        try {
            PlayerCreatedEvent evt = generator.generatePlayerCreated();
            producer.send(new ProducerRecord<>(TOPIC_PLAYER_CREATED, evt.getId().toString(), evt)).get();
            
            dataStore.addPlayer(new PlayerInfo(
                    evt.getId().toString(),
                    evt.getUsername().toString(),
                    evt.getEmail().toString(),
                    evt.getDistributionPlatformId() != null ? evt.getDistributionPlatformId().toString() : null
            ));
            playerCount.incrementAndGet();
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la création du joueur: " + e.getMessage());
        }
    }

    private void producePurchaseSync() {
        if (!dataStore.hasMinimumData()) return;
        try {
            GameInfo game = dataStore.getRandomGame();
            PlayerInfo player = dataStore.getRandomPlayer();
            GamePurchaseEvent evt = generator.generatePurchase(game, player);
            
            producer.send(new ProducerRecord<>(TOPIC_PURCHASE, evt.getPurchaseId().toString(), evt)).get();
            
            dataStore.addPurchase(new PurchaseInfo(
                    evt.getPurchaseId().toString(),
                    game.gameId(),
                    game.gameName(),
                    player.playerId(),
                    player.username(),
                    evt.getPricePaid()
            ));
            
            // Enregistrer l'achat dans les métriques du jeu
            dataStore.recordPurchase(game.gameId());
            
            purchaseCount.incrementAndGet();
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la création de l'achat: " + e.getMessage());
        }
    }

    private void produceSessionSync() {
        if (!dataStore.hasPurchases()) return;
        try {
            PurchaseInfo purchase = dataStore.getRandomPurchase();
            GameInfo game = findGameById(purchase.gameId());
            PlayerInfo player = findPlayerById(purchase.playerId());
            if (game == null || player == null) return;
            
            GameSessionEvent evt = generator.generateSession(game, player);
            producer.send(new ProducerRecord<>(TOPIC_SESSION, evt.getSessionId().toString(), evt)).get();
            
            // Enregistrer le temps de jeu dans les métriques du jeu (global)
            dataStore.recordPlaytime(game.gameId(), evt.getSessionDuration());
            // Enregistrer le temps de jeu pour ce joueur sur ce jeu spécifique
            dataStore.recordPlayerPlaytime(player.playerId(), game.gameId(), evt.getSessionDuration());
            
            sessionCount.incrementAndGet();
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la création de la session: " + e.getMessage());
        }
    }

    private void produceRatingSync() {
        if (!dataStore.hasPurchases()) return;
        try {
            PurchaseInfo purchase = dataStore.getRandomPurchase();
            GameInfo game = findGameById(purchase.gameId());
            PlayerInfo player = findPlayerById(purchase.playerId());
            if (game == null || player == null) return;
            
            // Vérifier que le joueur a joué au moins 10h à ce jeu
            if (!dataStore.canPlayerReviewGame(player.playerId(), game.gameId())) {
                // Pas assez de temps de jeu, on ne génère pas de rating
                return;
            }
            
            NewRatingEvent evt = generator.generateRating(game, player);
            producer.send(new ProducerRecord<>(TOPIC_RATING, evt.getPlayerId().toString(), evt)).get();
            ratingCount.incrementAndGet();
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la création de la note: " + e.getMessage());
        }
    }

    private void produceCrashSync() {
        if (!dataStore.hasGames() || !dataStore.hasPlayers()) return;
        try {
            GameInfo game = dataStore.getRandomGame();
            PlayerInfo player = dataStore.getRandomPlayer();
            
            CrashReportEvent evt = generator.generateCrash(game, player);
            producer.send(new ProducerRecord<>(TOPIC_CRASH, evt.getCrashId().toString(), evt)).get();
            
            // Enregistrer l'incident dans les métriques du jeu
            dataStore.recordIncident(game.gameId());
            
            crashCount.incrementAndGet();
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la création du crash: " + e.getMessage());
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
            
            // Déterminer le type de patch basé sur les métriques
            PatchType patchType = determinePatchType(game.gameId());
            PatchPublishedEvent evt = generator.generatePatch(game, patchType);
            
            producer.send(new ProducerRecord<>(TOPIC_PATCH, evt.getGameId().toString(), evt)).get();
            
            // Réinitialiser les compteurs appropriés après publication
            resetMetricsAfterPatch(game.gameId(), patchType);
            
            patchCount.incrementAndGet();
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la création du patch: " + e.getMessage());
        }
    }

    private void produceDlcSync() {
        if (!dataStore.hasGames()) return;
        try {
            GameInfo game = dataStore.getRandomGame();
            DlcPublishedEvent evt = generator.generateDlc(game);
            producer.send(new ProducerRecord<>(TOPIC_DLC, evt.getDlcId().toString(), evt)).get();
            
            // Stocker le DLC pour les achats ultérieurs
            dataStore.addDlc(new InMemoryDataStore.DlcInfo(
                evt.getDlcId().toString(),
                evt.getDlcName().toString(),
                evt.getGameId().toString(),
                evt.getPublisherId().toString(),
                evt.getPlatform().toString(),
                evt.getPrice()
            ));
            
            dlcCount.incrementAndGet();
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la création du DLC: " + e.getMessage());
        }
    }

    /**
     * Produit un achat de DLC de manière synchrone.
     * CONTRAINTE KAFKA STREAMS: Le joueur doit posséder le jeu de base pour acheter le DLC.
     */
    private void produceDlcPurchaseSync() {
        if (!dataStore.hasDlcs() || !dataStore.hasPurchases()) return;
        try {
            // Récupérer un joueur qui a déjà acheté des jeux
            PurchaseInfo existingPurchase = dataStore.getRandomPurchase();
            PlayerInfo player = findPlayerById(existingPurchase.playerId());
            if (player == null) return;
            
            // Trouver un DLC pour un jeu que le joueur possède
            InMemoryDataStore.DlcInfo dlc = dataStore.getRandomDlcForPlayer(player.playerId());
            if (dlc == null) {
                // Aucun DLC disponible pour les jeux que ce joueur possède
                return;
            }
            
            // Vérification de la contrainte: le joueur doit posséder le jeu de base
            if (!dataStore.playerOwnsGame(player.playerId(), dlc.gameId())) {
                System.out.println("⚠️ Joueur " + player.username() + " ne possède pas le jeu de base pour le DLC " + dlc.dlcName());
                return;
            }
            
            // Vérifier que le joueur n'a pas déjà ce DLC
            if (dataStore.playerOwnsDlc(player.playerId(), dlc.dlcId())) {
                return;
            }
            
            DlcPurchaseEvent evt = generator.generateDlcPurchase(dlc, player);
            producer.send(new ProducerRecord<>(TOPIC_DLC_PURCHASE, evt.getPlayerId().toString(), evt)).get();
            
            // Stocker l'achat de DLC
            dataStore.addDlcPurchase(new InMemoryDataStore.DlcPurchaseInfo(
                evt.getPurchaseId().toString(),
                dlc.dlcId(),
                dlc.dlcName(),
                dlc.gameId(),
                player.playerId(),
                player.username(),
                evt.getPricePaid()
            ));
            
            dlcPurchaseCount.incrementAndGet();
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de l'achat du DLC: " + e.getMessage());
        }
    }

    private void produceReviewSync() {
        if (!dataStore.hasPurchases()) return;
        try {
            PurchaseInfo purchase = dataStore.getRandomPurchase();
            GameInfo game = findGameById(purchase.gameId());
            PlayerInfo player = findPlayerById(purchase.playerId());
            if (game == null || player == null) return;
            
            // Vérifier que le joueur a joué au moins 10h à ce jeu
            if (!dataStore.canPlayerReviewGame(player.playerId(), game.gameId())) {
                // Pas assez de temps de jeu, on ne génère pas de review
                return;
            }
            
            ReviewPublishedEvent evt = generator.generateReview(game, player);
            producer.send(new ProducerRecord<>(TOPIC_REVIEW, evt.getReviewId().toString(), evt)).get();
            
            // Stocker la review pour les votes ultérieurs
            dataStore.addReview(new InMemoryDataStore.ReviewInfo(
                evt.getReviewId().toString(),
                evt.getGameId().toString(),
                evt.getPlayerId().toString(),
                evt.getPlayerUsername().toString()
            ));
            
            reviewCount.incrementAndGet();
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la création de l'avis: " + e.getMessage());
        }
    }

    private void produceReviewVoteSync() {
        if (!dataStore.hasReviews() || !dataStore.hasPlayers()) return;
        try {
            InMemoryDataStore.ReviewInfo review = dataStore.getRandomReview();
            PlayerInfo voter = dataStore.getRandomPlayer();
            
            // Un joueur ne vote pas sur sa propre évaluation
            if (voter.playerId().equals(review.playerId())) {
                voter = dataStore.getRandomPlayer(); // Réessaye avec un autre joueur
                if (voter.playerId().equals(review.playerId())) return; // Abandonne si même joueur
            }
            
            ReviewVotedEvent evt = generator.generateReviewVote(review.reviewId(), voter.playerId());
            producer.send(new ProducerRecord<>(TOPIC_REVIEW_VOTE, review.reviewId(), evt)).get();
            reviewVoteCount.incrementAndGet();
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la création du vote: " + e.getMessage());
        }
    }

    // ========== PRODUCTEURS D'ÉVÉNEMENTS ==========

    /**
     * Produit un événement GameReleased.
     */
    private void produceGameReleased() {
        try {
            GameReleasedEvent evt = generator.generateGameReleased();
            String gameId = evt.getGameId().toString();
            
            producer.send(new ProducerRecord<>(TOPIC_GAME_RELEASED, gameId, evt), this::handleSendResult);
            
            // Stocker en mémoire pour les événements dépendants
            dataStore.addGame(new GameInfo(
                    gameId,
                    evt.getGameName().toString(),
                    evt.getPublisherId().toString(),
                    evt.getPublisherName().toString(),
                    evt.getPlatform().toString(),
                    evt.getGenre().toString(),
                    evt.getInitialPrice(),
                    evt.getInitialVersion().toString()
            ));
            
            gameCount.incrementAndGet();
            System.out.println("🎮 Jeu créé: " + evt.getGameName() + 
                    " par " + evt.getPublisherName() + 
                    " (" + evt.getPlatform() + ")");
                    
        } catch (Exception e) {
            System.err.println("❌ Erreur GameReleased: " + e.getMessage());
        }
    }

    /**
     * Produit un événement PlayerCreated.
     */
    private void producePlayerCreated() {
        try {
            PlayerCreatedEvent evt = generator.generatePlayerCreated();
            String playerId = evt.getId().toString();
            
            producer.send(new ProducerRecord<>(TOPIC_PLAYER_CREATED, playerId, evt), this::handleSendResult);
            
            // Stocker en mémoire
            dataStore.addPlayer(new PlayerInfo(
                    playerId,
                    evt.getUsername().toString(),
                    evt.getEmail().toString(),
                    evt.getDistributionPlatformId() != null ? evt.getDistributionPlatformId().toString() : null
            ));
            
            playerCount.incrementAndGet();
            System.out.println("👤 Joueur créé: " + evt.getUsername() + " (" + evt.getEmail() + ")");
            
        } catch (Exception e) {
            System.err.println("❌ Erreur PlayerCreated: " + e.getMessage());
        }
    }

    /**
     * Produit un événement GamePurchase.
     */
    private void producePurchase() {
        if (!dataStore.hasMinimumData()) {
            return; // Attendre d'avoir assez de données
        }
        
        try {
            GameInfo game = dataStore.getRandomGame();
            PlayerInfo player = dataStore.getRandomPlayer();
            
            if (game == null || player == null) return;
            
            GamePurchaseEvent evt = generator.generatePurchase(game, player);
            
            producer.send(new ProducerRecord<>(TOPIC_PURCHASE, evt.getPurchaseId().toString(), evt), 
                    this::handleSendResult);
            
            // Stocker l'achat pour les sessions/ratings
            dataStore.addPurchase(new PurchaseInfo(
                    evt.getPurchaseId().toString(),
                    game.gameId(),
                    game.gameName(),
                    player.playerId(),
                    player.username(),
                    evt.getPricePaid()
            ));
            
            // Enregistrer l'achat dans les métriques du jeu
            dataStore.recordPurchase(game.gameId());
            
            purchaseCount.incrementAndGet();
            System.out.println("💰 Achat: " + player.username() + " → " + game.gameName() + 
                    " (" + evt.getPricePaid() + "€)");
                    
        } catch (Exception e) {
            System.err.println("❌ Erreur Purchase: " + e.getMessage());
        }
    }

    /**
     * Produit un événement GameSession.
     */
    private void produceSession() {
        if (!dataStore.hasPurchases()) return;
        
        try {
            PurchaseInfo purchase = dataStore.getRandomPurchase();
            GameInfo game = findGameById(purchase.gameId());
            PlayerInfo player = findPlayerById(purchase.playerId());
            
            if (game == null || player == null) return;
            
            GameSessionEvent evt = generator.generateSession(game, player);
            producer.send(new ProducerRecord<>(TOPIC_SESSION, evt.getSessionId().toString(), evt), 
                    this::handleSendResult);
            
            // Enregistrer le temps de jeu dans les métriques du jeu (global)
            dataStore.recordPlaytime(game.gameId(), evt.getSessionDuration());
            // Enregistrer le temps de jeu pour ce joueur sur ce jeu spécifique
            dataStore.recordPlayerPlaytime(player.playerId(), game.gameId(), evt.getSessionDuration());
            
            sessionCount.incrementAndGet();
            System.out.println("🎯 Session: " + player.username() + " joue à " + game.gameName() + 
                    " (" + evt.getSessionDuration() + " min, total: " + 
                    dataStore.getPlayerPlaytimeHours(player.playerId(), game.gameId()) + "h)");
                    
        } catch (Exception e) {
            System.err.println("❌ Erreur Session: " + e.getMessage());
        }
    }

    /**
     * Produit un événement NewRating.
     * Requiert que le joueur ait joué au moins 10h au jeu.
     */
    private void produceRating() {
        if (!dataStore.hasPurchases()) return;
        
        try {
            PurchaseInfo purchase = dataStore.getRandomPurchase();
            GameInfo game = findGameById(purchase.gameId());
            PlayerInfo player = findPlayerById(purchase.playerId());
            
            if (game == null || player == null) return;
            
            // Vérifier que le joueur a joué au moins 10h à ce jeu
            if (!dataStore.canPlayerReviewGame(player.playerId(), game.gameId())) {
                // Pas assez de temps de jeu, on skip silencieusement
                return;
            }
            
            NewRatingEvent evt = generator.generateRating(game, player);
            producer.send(new ProducerRecord<>(TOPIC_RATING, evt.getPlayerId().toString(), evt), 
                    this::handleSendResult);
            
            ratingCount.incrementAndGet();
            System.out.println("⭐ Rating: " + player.username() + " note " + game.gameName() + 
                    " → " + evt.getRating() + "/5 (après " + 
                    dataStore.getPlayerPlaytimeHours(player.playerId(), game.gameId()) + "h de jeu)");
                    
        } catch (Exception e) {
            System.err.println("❌ Erreur Rating: " + e.getMessage());
        }
    }

    /**
     * Produit un événement CrashReport.
     */
    private void produceCrash() {
        if (!dataStore.hasGames() || !dataStore.hasPlayers()) return;
        
        try {
            GameInfo game = dataStore.getRandomGame();
            PlayerInfo player = dataStore.getRandomPlayer();
            
            CrashReportEvent evt = generator.generateCrash(game, player);
            producer.send(new ProducerRecord<>(TOPIC_CRASH, evt.getCrashId().toString(), evt), 
                    this::handleSendResult);
            
            // Enregistrer l'incident dans les métriques du jeu
            dataStore.recordIncident(game.gameId());
            
            crashCount.incrementAndGet();
            System.out.println("💥 Crash: " + game.gameName() + " - " + evt.getErrorType() + 
                    " [" + evt.getSeverity() + "]");
                    
        } catch (Exception e) {
            System.err.println("❌ Erreur Crash: " + e.getMessage());
        }
    }

    /**
     * Produit un événement PatchPublished.
     * Le type de patch est déterminé par les métriques du jeu:
     * - FIX: après 3+ incidents (crashes)
     * - OPTIMIZATION: après 5+ achats
     * - ADD: après 100h+ de temps de jeu cumulé
     */
    private void producePatch() {
        if (!dataStore.hasGames()) return;
        
        try {
            GameInfo game = dataStore.getRandomGame();
            
            // Déterminer le type de patch basé sur les métriques
            PatchType patchType = determinePatchType(game.gameId());
            PatchPublishedEvent evt = generator.generatePatch(game, patchType);
            
            producer.send(new ProducerRecord<>(TOPIC_PATCH, evt.getGameId().toString(), evt), 
                    this::handleSendResult);
            
            // Réinitialiser les compteurs appropriés après publication
            resetMetricsAfterPatch(game.gameId(), patchType);
            
            patchCount.incrementAndGet();
            System.out.println("🔧 Patch [" + patchType + "]: " + game.gameName() + " " + 
                    evt.getOldVersion() + " → " + evt.getNewVersion() + 
                    " (" + evt.getSizeInMB() + " MB)");
                    
        } catch (Exception e) {
            System.err.println("❌ Erreur Patch: " + e.getMessage());
        }
    }
    
    /**
     * Détermine le type de patch à générer basé sur les métriques du jeu.
     * Priorité: FIX > OPTIMIZATION > ADD (on corrige les bugs en priorité)
     */
    private PatchType determinePatchType(String gameId) {
        // Priorité 1: Correction de bugs si trop d'incidents
        if (dataStore.isEligibleForFix(gameId)) {
            return PatchType.FIX;
        }
        // Priorité 2: Optimisation si le jeu se vend bien
        if (dataStore.isEligibleForOptimization(gameId)) {
            return PatchType.OPTIMIZATION;
        }
        // Priorité 3: Ajout de contenu si les joueurs jouent beaucoup
        if (dataStore.isEligibleForAdd(gameId)) {
            return PatchType.ADD;
        }
        // Par défaut: type aléatoire
        PatchType[] types = PatchType.values();
        return types[ThreadLocalRandom.current().nextInt(types.length)];
    }
    
    /**
     * Réinitialise les compteurs appropriés après publication d'un patch.
     */
    private void resetMetricsAfterPatch(String gameId, PatchType patchType) {
        switch (patchType) {
            case FIX:
                dataStore.resetAfterFix(gameId);
                break;
            case OPTIMIZATION:
                dataStore.resetAfterOptimization(gameId);
                break;
            case ADD:
                dataStore.resetAfterAdd(gameId);
                break;
        }
    }

    /**
     * Produit un événement DlcPublished.
     */
    private void produceDlc() {
        if (!dataStore.hasGames()) return;
        
        try {
            GameInfo game = dataStore.getRandomGame();
            DlcPublishedEvent evt = generator.generateDlc(game);
            
            producer.send(new ProducerRecord<>(TOPIC_DLC, evt.getDlcId().toString(), evt), 
                    this::handleSendResult);
            
            // Stocker le DLC pour les achats ultérieurs
            dataStore.addDlc(new InMemoryDataStore.DlcInfo(
                evt.getDlcId().toString(),
                evt.getDlcName().toString(),
                evt.getGameId().toString(),
                evt.getPublisherId().toString(),
                evt.getPlatform().toString(),
                evt.getPrice()
            ));
            
            dlcCount.incrementAndGet();
            System.out.println("📦 DLC: " + evt.getDlcName() + " (" + evt.getPrice() + "€, " + 
                    evt.getSizeInMB() + " MB)");
                    
        } catch (Exception e) {
            System.err.println("❌ Erreur DLC: " + e.getMessage());
        }
    }

    /**
     * Produit un événement DlcPurchase.
     * CONTRAINTE KAFKA STREAMS: Le joueur doit posséder le jeu de base pour acheter le DLC.
     */
    private void produceDlcPurchase() {
        if (!dataStore.hasDlcs() || !dataStore.hasPurchases()) return;
        
        try {
            // Récupérer un joueur qui a déjà acheté des jeux
            PurchaseInfo existingPurchase = dataStore.getRandomPurchase();
            PlayerInfo player = findPlayerById(existingPurchase.playerId());
            if (player == null) return;
            
            // Trouver un DLC pour un jeu que le joueur possède (contrainte Kafka Streams)
            InMemoryDataStore.DlcInfo dlc = dataStore.getRandomDlcForPlayer(player.playerId());
            if (dlc == null) {
                // Aucun DLC disponible pour les jeux que ce joueur possède
                return;
            }
            
            // Double vérification de la contrainte: le joueur doit posséder le jeu de base
            if (!dataStore.playerOwnsGame(player.playerId(), dlc.gameId())) {
                System.out.println("⚠️ Contrainte Kafka Streams: " + player.username() + 
                    " ne possède pas le jeu de base pour " + dlc.dlcName());
                return;
            }
            
            // Vérifier que le joueur n'a pas déjà ce DLC
            if (dataStore.playerOwnsDlc(player.playerId(), dlc.dlcId())) {
                return;
            }
            
            DlcPurchaseEvent evt = generator.generateDlcPurchase(dlc, player);
            producer.send(new ProducerRecord<>(TOPIC_DLC_PURCHASE, evt.getPlayerId().toString(), evt), 
                    this::handleSendResult);
            
            // Stocker l'achat de DLC
            dataStore.addDlcPurchase(new InMemoryDataStore.DlcPurchaseInfo(
                evt.getPurchaseId().toString(),
                dlc.dlcId(),
                dlc.dlcName(),
                dlc.gameId(),
                player.playerId(),
                player.username(),
                evt.getPricePaid()
            ));
            
            dlcPurchaseCount.incrementAndGet();
            System.out.println("🎁 Achat DLC: " + player.username() + " → " + dlc.dlcName() + 
                    " (" + evt.getPricePaid() + "€) [Jeu de base possédé ✓]");
                    
        } catch (Exception e) {
            System.err.println("❌ Erreur DLC Purchase: " + e.getMessage());
        }
    }

    /**
     * Produit un événement ReviewPublished.
     * Requiert que le joueur ait joué au moins 10h au jeu.
     */
    private void produceReview() {
        if (!dataStore.hasPurchases()) return;
        
        try {
            PurchaseInfo purchase = dataStore.getRandomPurchase();
            GameInfo game = findGameById(purchase.gameId());
            PlayerInfo player = findPlayerById(purchase.playerId());
            
            if (game == null || player == null) return;
            
            // Vérifier que le joueur a joué au moins 10h à ce jeu
            if (!dataStore.canPlayerReviewGame(player.playerId(), game.gameId())) {
                // Pas assez de temps de jeu, on skip silencieusement
                return;
            }
            
            ReviewPublishedEvent evt = generator.generateReview(game, player);
            producer.send(new ProducerRecord<>(TOPIC_REVIEW, evt.getReviewId().toString(), evt), 
                    this::handleSendResult);
            
            // Stocker la review pour les votes ultérieurs
            dataStore.addReview(new InMemoryDataStore.ReviewInfo(
                evt.getReviewId().toString(),
                evt.getGameId().toString(),
                evt.getPlayerId().toString(),
                evt.getPlayerUsername().toString()
            ));
            
            reviewCount.incrementAndGet();
            System.out.println("📝 Review: " + player.username() + " → " + game.gameName() + 
                    " (" + evt.getRating() + "/5, après " + 
                    dataStore.getPlayerPlaytimeHours(player.playerId(), game.gameId()) + "h de jeu)");
                    
        } catch (Exception e) {
            System.err.println("❌ Erreur Review: " + e.getMessage());
        }
    }

    /**
     * Produit un événement ReviewVoted (vote sur l'utilité d'une évaluation).
     */
    private void produceReviewVote() {
        if (!dataStore.hasReviews() || !dataStore.hasPlayers()) return;
        
        try {
            InMemoryDataStore.ReviewInfo review = dataStore.getRandomReview();
            PlayerInfo voter = dataStore.getRandomPlayer();
            
            // Un joueur ne vote pas sur sa propre évaluation
            if (voter.playerId().equals(review.playerId())) {
                voter = dataStore.getRandomPlayer();
                if (voter.playerId().equals(review.playerId())) return;
            }
            
            ReviewVotedEvent evt = generator.generateReviewVote(review.reviewId(), voter.playerId());
            producer.send(new ProducerRecord<>(TOPIC_REVIEW_VOTE, review.reviewId(), evt), 
                    this::handleSendResult);
            
            reviewVoteCount.incrementAndGet();
            String voteType = evt.getIsHelpful() ? "👍" : "👎";
            System.out.println(voteType + " Vote: " + voter.username() + " → avis #" + 
                    review.reviewId().substring(0, 8) + "...");
                    
        } catch (Exception e) {
            System.err.println("❌ Erreur Vote: " + e.getMessage());
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
        long elapsed = System.currentTimeMillis() - startTime.get();
        long minutes = elapsed / 60000;
        long seconds = (elapsed % 60000) / 1000;
        
        System.out.println("\n╔════════════════════════════════════════════════════════════╗");
        System.out.println("║                    📊 STATISTIQUES 📊                       ║");
        System.out.println("╠════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Durée: %02d:%02d                                              ║%n", minutes, seconds);
        System.out.println("╠════════════════════════════════════════════════════════════╣");
        System.out.printf("║  🎮 Jeux:      %5d  │  👤 Joueurs:   %5d              ║%n", 
                gameCount.get(), playerCount.get());
        System.out.printf("║  💰 Achats:    %5d  │  🎯 Sessions:  %5d              ║%n", 
                purchaseCount.get(), sessionCount.get());
        System.out.printf("║  ⭐ Ratings:   %5d  │  💥 Crashs:    %5d              ║%n", 
                ratingCount.get(), crashCount.get());
        System.out.printf("║  🔧 Patches:   %5d  │  📦 DLCs:      %5d              ║%n", 
                patchCount.get(), dlcCount.get());
        System.out.printf("║  🎁 DLC Achats:%5d  │  📝 Reviews:   %5d              ║%n", 
                dlcPurchaseCount.get(), reviewCount.get());
        System.out.printf("║  👍 Votes:     %5d  │                                   ║%n", 
                reviewVoteCount.get());
        System.out.println("╠════════════════════════════════════════════════════════════╣");
        System.out.printf("║  📂 DataStore: %d éditeurs, %d jeux, %d joueurs, %d achats  ║%n",
                dataStore.getPublisherCount(), dataStore.getGameCount(), 
                dataStore.getPlayerCount(), dataStore.getPurchaseCount());
        System.out.printf("║  📂 DLCs: %d publiés, %d achetés                            ║%n",
                dataStore.getDlcCount(), dataStore.getDlcPurchaseCount());
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
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
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
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║          ORCHESTRATEUR D'ÉVÉNEMENTS STEAM PROJECT          ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");
        
        SchedulerConfig config;
        
        // Charger la configuration si un fichier est passé en argument
        if (args.length > 0) {
            try {
                config = new SchedulerConfig(args[0]);
                System.out.println("📄 Configuration chargée depuis: " + args[0]);
            } catch (Exception e) {
                System.err.println("⚠️ Impossible de charger " + args[0] + ": " + e.getMessage());
                System.out.println("📄 Utilisation de la configuration par défaut.");
                config = new SchedulerConfig();
            }
        } else {
            // Try to load from classpath
            try {
                java.io.InputStream is = ScheduledEventOrchestrator.class.getResourceAsStream("/scheduler.properties");
                if (is != null) {
                    java.util.Properties props = new java.util.Properties();
                    props.load(is);
                    is.close();
                    config = new SchedulerConfig("src/main/resources/scheduler.properties");
                    System.out.println("📄 Configuration chargée depuis le classpath.");
                } else {
                    config = new SchedulerConfig();
                    System.out.println("📄 Utilisation de la configuration par défaut.");
                }
            } catch (Exception e) {
                config = new SchedulerConfig();
                System.out.println("📄 Utilisation de la configuration par défaut.");
            }
        }
        
        ScheduledEventOrchestrator orchestrator = new ScheduledEventOrchestrator(config);
        
        // Hook pour arrêt propre
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n📢 Signal d'arrêt reçu...");
            orchestrator.stop();
        }));
        
        // Démarrer
        orchestrator.start();
        
        // Garder le programme en vie
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            orchestrator.stop();
        }
    }
}
