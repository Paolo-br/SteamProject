# Guide : Génération d'événements planifiés avec ScheduledExecutorService

Ce guide explique comment créer un système de génération d'événements périodiques pour simuler l'activité de la plateforme Steam.

---

## Table des matières

1. [Objectif et architecture](#1-objectif-et-architecture)
2. [Qu'est-ce que ScheduledExecutorService ?](#2-quest-ce-que-scheduledexecutorservice-)
3. [Stratégie de génération des événements](#3-stratégie-de-génération-des-événements)
4. [Implémentation complète](#4-implémentation-complète)
5. [Configuration des fréquences](#5-configuration-des-fréquences)
6. [Gestion des dépendances entre événements](#6-gestion-des-dépendances-entre-événements)
7. [Lancement et arrêt](#7-lancement-et-arrêt)

---

## 1. Objectif et architecture

### Objectif

Créer un système qui génère automatiquement des événements Kafka à intervalles réguliers pour simuler une plateforme de jeux vidéo active :

1. **Phase 1 - Initialisation** : Créer des jeux et des joueurs (données de base)
2. **Phase 2 - Activité** : Une fois les données de base créées, générer les événements dépendants (achats, crashs, DLC, patches, ratings, sessions)

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    ScheduledEventOrchestrator                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐       │
│  │ GameProducer │    │PlayerProducer│    │  DataStore   │       │
│  │  (fréquent)  │    │   (modéré)   │    │ (en mémoire) │       │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘       │
│         │                   │                   │                │
│         └───────────────────┴───────────────────┘                │
│                             │                                    │
│                    ┌────────▼────────┐                          │
│                    │ Données prêtes? │                          │
│                    └────────┬────────┘                          │
│                             │ OUI                               │
│         ┌───────────────────┼───────────────────┐               │
│         ▼                   ▼                   ▼               │
│  ┌────────────┐     ┌────────────┐      ┌────────────┐         │
│  │  Purchase  │     │   Crash    │      │   Patch    │         │
│  │  Producer  │     │  Producer  │      │  Producer  │         │
│  └────────────┘     └────────────┘      └────────────┘         │
│         │                   │                   │               │
│  ┌────────────┐     ┌────────────┐      ┌────────────┐         │
│  │   Rating   │     │    DLC     │      │  Session   │         │
│  │  Producer  │     │  Producer  │      │  Producer  │         │
│  └────────────┘     └────────────┘      └────────────┘         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │  Kafka Topics   │
                    │                 │
                    │ • game.events   │
                    │ • player.events │
                    │ • purchase.events│
                    │ • crash.events  │
                    │ • patch.events  │
                    │ • dlc.events    │
                    │ • rating.events │
                    │ • session.events│
                    └─────────────────┘
```

---

## 2. Qu'est-ce que ScheduledExecutorService ?

### Définition

`ScheduledExecutorService` est une interface Java qui permet d'exécuter des tâches :
- **À intervalle fixe** (`scheduleAtFixedRate`)
- **Avec un délai fixe entre exécutions** (`scheduleWithFixedDelay`)
- **Une seule fois après un délai** (`schedule`)

### Méthodes principales

```java
// Exécution répétée à intervalle fixe (peu importe la durée de la tâche)
scheduler.scheduleAtFixedRate(
    () -> doSomething(),    // Tâche à exécuter
    0,                       // Délai initial avant première exécution
    5,                       // Période entre chaque exécution
    TimeUnit.SECONDS         // Unité de temps
);

// Exécution répétée avec délai fixe APRÈS la fin de chaque tâche
scheduler.scheduleWithFixedDelay(
    () -> doSomething(),
    0,                       // Délai initial
    5,                       // Délai après chaque exécution
    TimeUnit.SECONDS
);

// Exécution unique après un délai
scheduler.schedule(
    () -> doOnce(),
    10,                      // Délai
    TimeUnit.SECONDS
);
```

### Différence scheduleAtFixedRate vs scheduleWithFixedDelay

```
scheduleAtFixedRate (période = 5s) :
|--tâche--|     |--tâche--|     |--tâche--|
0s        2s    5s        7s    10s
           ↑ 3s d'attente  ↑ 3s d'attente

scheduleWithFixedDelay (délai = 5s) :
|--tâche--|          |--tâche--|          |--tâche--|
0s        2s         7s        9s         14s
           ↑ 5s délai          ↑ 5s délai
```

**Recommandation** : Utiliser `scheduleAtFixedRate` pour une fréquence constante.

---

## 3. Stratégie de génération des événements

### Ordre des événements

Les événements ont des **dépendances** :

```
┌─────────────────┐     ┌─────────────────┐
│  GameReleased   │     │  PlayerCreated  │
│  (jeux créés)   │     │ (joueurs créés) │
└────────┬────────┘     └────────┬────────┘
         │                       │
         └───────────┬───────────┘
                     │
         ┌───────────▼───────────┐
         │   PRÉREQUIS REMPLIS   │
         │  (jeux ET joueurs     │
         │   existent)           │
         └───────────┬───────────┘
                     │
    ┌────────────────┼────────────────┐
    │                │                │
    ▼                ▼                ▼
┌────────┐     ┌──────────┐     ┌──────────┐
│Purchase│     │  Rating  │     │ Session  │
│(achats)│     │ (notes)  │     │(sessions)│
└────────┘     └──────────┘     └──────────┘
    │
    │ (après des achats)
    ▼
┌──────────────────────────────────────────┐
│  Événements post-achat                   │
│  • CrashReport (bugs rencontrés)         │
│  • PatchPublished (corrections)          │
│  • DlcPublished (contenus additionnels)  │
└──────────────────────────────────────────┘
```

### Fréquences suggérées

| Événement | Fréquence | Justification |
|-----------|-----------|---------------|
| `GameReleased` | Toutes les 2-5s | Beaucoup de jeux à créer |
| `PlayerCreated` | Toutes les 10-30s | Moins de joueurs |
| `GamePurchase` | Toutes les 1-3s | Activité intense |
| `GameSession` | Toutes les 2-5s | Joueurs actifs |
| `NewRating` | Toutes les 5-10s | Après avoir joué |
| `CrashReport` | Toutes les 10-30s | Occasionnel |
| `PatchPublished` | Toutes les 30-60s | Rare |
| `DlcPublished` | Toutes les 60-120s | Très rare |

---

## 4. Implémentation complète

### 4.1 DataStore : Stockage en mémoire des entités créées

```java
package org.steamproject.infra.kafka.producer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stockage thread-safe des jeux et joueurs créés.
 * Permet aux producteurs dépendants de récupérer des entités existantes.
 */
public class InMemoryDataStore {
    
    // CopyOnWriteArrayList = thread-safe pour lectures fréquentes
    private final List<GameInfo> games = new CopyOnWriteArrayList<>();
    private final List<PlayerInfo> players = new CopyOnWriteArrayList<>();
    
    // --- Jeux ---
    
    public void addGame(GameInfo game) {
        games.add(game);
    }
    
    public GameInfo getRandomGame() {
        if (games.isEmpty()) return null;
        int index = ThreadLocalRandom.current().nextInt(games.size());
        return games.get(index);
    }
    
    public List<GameInfo> getAllGames() {
        return List.copyOf(games);
    }
    
    public int getGameCount() {
        return games.size();
    }
    
    // --- Joueurs ---
    
    public void addPlayer(PlayerInfo player) {
        players.add(player);
    }
    
    public PlayerInfo getRandomPlayer() {
        if (players.isEmpty()) return null;
        int index = ThreadLocalRandom.current().nextInt(players.size());
        return players.get(index);
    }
    
    public List<PlayerInfo> getAllPlayers() {
        return List.copyOf(players);
    }
    
    public int getPlayerCount() {
        return players.size();
    }
    
    // --- Vérification des prérequis ---
    
    public boolean hasMinimumData() {
        return games.size() >= 5 && players.size() >= 3;
    }
    
    public boolean hasGames() {
        return !games.isEmpty();
    }
    
    public boolean hasPlayers() {
        return !players.isEmpty();
    }
    
    // --- Classes internes ---
    
    public record GameInfo(
        String gameId,
        String gameName,
        String publisherId,
        String publisherName,
        String platform,
        String genre,
        double price,
        String currentVersion
    ) {}
    
    public record PlayerInfo(
        String playerId,
        String username,
        String platformId
    ) {}
}
```

### 4.2 Générateur de données fake avec DataFaker

```java
package org.steamproject.infra.kafka.producer;

import net.datafaker.Faker;
import org.steamproject.events.*;
import org.steamproject.infra.kafka.producer.InMemoryDataStore.*;

import java.time.Instant;
import java.util.*;

/**
 * Génère des événements Avro avec des données réalistes via DataFaker.
 */
public class FakeDataGenerator {
    
    private final Faker faker = new Faker();
    private final Random random = new Random();
    
    // Listes de valeurs possibles
    private static final List<String> PLATFORMS = List.of("PC", "PS5", "Xbox Series X", "Nintendo Switch");
    private static final List<String> GENRES = List.of("Action", "RPG", "FPS", "Strategy", "Sports", "Adventure", "Simulation");
    private static final List<String> DISTRIBUTIONS = List.of("Steam", "Epic Games", "GOG", "PlayStation Store", "Xbox Store");
    private static final List<String> SEVERITIES = List.of("CRITICAL", "MAJOR", "MINOR");
    private static final List<String> ERROR_TYPES = List.of("NULL_POINTER", "MEMORY_LEAK", "GRAPHICS_ERROR", "NETWORK_TIMEOUT", "SAVE_CORRUPTION");
    
    // ========== ÉVÉNEMENTS DE BASE ==========
    
    public GameReleasedEvent generateGameReleased() {
        String gameId = UUID.randomUUID().toString();
        String publisherId = UUID.randomUUID().toString();
        
        return GameReleasedEvent.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setGameId(gameId)
            .setGameName(faker.videoGame().title())
            .setPublisherId(publisherId)
            .setPublisherName(faker.company().name())
            .setPlatform(randomFrom(PLATFORMS))
            .setPlatforms(randomSublist(DISTRIBUTIONS, 1, 3))
            .setGenre(randomFrom(GENRES))
            .setInitialPrice(randomPrice(9.99, 69.99))
            .setInitialVersion("1.0.0")
            .setReleaseYear(2024 + random.nextInt(3))
            .setTimestamp(Instant.now().toEpochMilli())
            .build();
    }
    
    public PlayerCreatedEvent generatePlayerCreated() {
        return PlayerCreatedEvent.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setUsername(faker.internet().username())
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
    
    // ========== ÉVÉNEMENTS DÉPENDANTS ==========
    
    public GamePurchaseEvent generatePurchase(GameInfo game, PlayerInfo player) {
        return GamePurchaseEvent.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setPurchaseId(UUID.randomUUID().toString())
            .setGameId(game.gameId())
            .setGameName(game.gameName())
            .setPlayerId(player.playerId())
            .setPlayerUsername(player.username())
            .setPlatform(game.platform())
            .setPublisherId(game.publisherId())
            .setPublisherName(game.publisherName())
            .setPricePaid(game.price() * (random.nextBoolean() ? 1.0 : 0.8)) // 20% de chance de promo
            .setRegion(randomFrom(List.of("NA", "EU", "JP", "OTHER")))
            .setTimestamp(Instant.now().toEpochMilli())
            .build();
    }
    
    public CrashReportEvent generateCrash(GameInfo game, PlayerInfo player) {
        return CrashReportEvent.newBuilder()
            .setCrashId(UUID.randomUUID().toString())
            .setGameId(game.gameId())
            .setGameName(game.gameName())
            .setPlayerId(player.playerId())
            .setEditeurId(game.publisherId())
            .setEditeurName(game.publisherName())
            .setGameVersion(game.currentVersion())
            .setPlatform(game.platform())
            .setSeverity(Severity.valueOf(randomFrom(SEVERITIES)))
            .setErrorType(ErrorType.valueOf(randomFrom(ERROR_TYPES)))
            .setErrorMessage(faker.lorem().sentence())
            .setTimestamp(Instant.now().toEpochMilli())
            .build();
    }
    
    public NewRatingEvent generateRating(GameInfo game, PlayerInfo player) {
        int rating = 1 + random.nextInt(5); // 1-5 étoiles
        int playtime = random.nextInt(200); // 0-200 heures
        
        return NewRatingEvent.newBuilder()
            .setGameId(game.gameId())
            .setGameName(game.gameName())
            .setPlayerId(player.playerId())
            .setPlayerUsername(player.username())
            .setRating(rating)
            .setComment(random.nextBoolean() ? faker.lorem().paragraph() : null)
            .setPlaytime(playtime)
            .setIsRecommended(rating >= 3)
            .setTimestamp(Instant.now().toEpochMilli())
            .build();
    }
    
    public GameSessionEvent generateSession(GameInfo game, PlayerInfo player) {
        return GameSessionEvent.newBuilder()
            .setSessionId(UUID.randomUUID().toString())
            .setGameId(game.gameId())
            .setGameName(game.gameName())
            .setPlayerId(player.playerId())
            .setPlayerUsername(player.username())
            .setSessionDuration(random.nextInt(180) + 10) // 10-190 minutes
            .setSessionType(SessionType.values()[random.nextInt(SessionType.values().length)])
            .setTimestamp(Instant.now().toEpochMilli())
            .build();
    }
    
    public PatchPublishedEvent generatePatch(GameInfo game) {
        String oldVersion = game.currentVersion();
        String newVersion = incrementVersion(oldVersion);
        
        List<Change> changes = new ArrayList<>();
        int numChanges = 1 + random.nextInt(5);
        for (int i = 0; i < numChanges; i++) {
            changes.add(Change.newBuilder()
                .setType(PatchType.values()[random.nextInt(PatchType.values().length)])
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
    
    public DlcPublishedEvent generateDlc(GameInfo game) {
        return DlcPublishedEvent.newBuilder()
            .setDlcId(UUID.randomUUID().toString())
            .setGameId(game.gameId())
            .setPublisherId(game.publisherId())
            .setPlatform(game.platform())
            .setDlcName(game.gameName() + " - " + faker.lorem().word() + " DLC")
            .setPrice(randomPrice(4.99, 29.99))
            .setReleaseTimestamp(Instant.now().toEpochMilli())
            .build();
    }
    
    // ========== UTILITAIRES ==========
    
    private <T> T randomFrom(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }
    
    private List<String> randomSublist(List<String> list, int min, int max) {
        int count = min + random.nextInt(max - min + 1);
        List<String> shuffled = new ArrayList<>(list);
        Collections.shuffle(shuffled);
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }
    
    private double randomPrice(double min, double max) {
        double price = min + random.nextDouble() * (max - min);
        return Math.round(price * 100.0) / 100.0;
    }
    
    private String incrementVersion(String version) {
        String[] parts = version.split("\\.");
        int patch = Integer.parseInt(parts[2]) + 1;
        return parts[0] + "." + parts[1] + "." + patch;
    }
}
```

### 4.3 Orchestrateur principal

```java
package org.steamproject.infra.kafka.producer;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.steamproject.events.*;
import org.steamproject.infra.kafka.producer.InMemoryDataStore.*;

import java.util.Properties;
import java.util.concurrent.*;

/**
 * Orchestrateur central qui planifie la génération de tous les événements.
 */
public class ScheduledEventOrchestrator {
    
    // Configuration Kafka
    private final String bootstrapServers;
    private final String schemaRegistryUrl;
    
    // Topics
    private static final String TOPIC_GAME = "game.events";
    private static final String TOPIC_PLAYER = "player.events";
    private static final String TOPIC_PURCHASE = "purchase.events";
    private static final String TOPIC_CRASH = "crash.events";
    private static final String TOPIC_RATING = "rating.events";
    private static final String TOPIC_SESSION = "session.events";
    private static final String TOPIC_PATCH = "patch.events";
    private static final String TOPIC_DLC = "dlc.events";
    
    // Composants
    private final ScheduledExecutorService scheduler;
    private final KafkaProducer<String, Object> producer;
    private final InMemoryDataStore dataStore;
    private final FakeDataGenerator generator;
    
    // Compteurs pour logging
    private final java.util.concurrent.atomic.AtomicInteger gameCount = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicInteger playerCount = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicInteger purchaseCount = new java.util.concurrent.atomic.AtomicInteger(0);
    
    public ScheduledEventOrchestrator(String bootstrapServers, String schemaRegistryUrl) {
        this.bootstrapServers = bootstrapServers;
        this.schemaRegistryUrl = schemaRegistryUrl;
        this.scheduler = Executors.newScheduledThreadPool(8);
        this.producer = createProducer();
        this.dataStore = new InMemoryDataStore();
        this.generator = new FakeDataGenerator();
    }
    
    private KafkaProducer<String, Object> createProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
        props.put("schema.registry.url", schemaRegistryUrl);
        return new KafkaProducer<>(props);
    }
    
    /**
     * Démarre la génération planifiée de tous les événements.
     */
    public void start() {
        System.out.println("🚀 Démarrage de l'orchestrateur d'événements...");
        
        // ========== PHASE 1 : Données de base ==========
        
        // Créer des jeux fréquemment (toutes les 2 secondes)
        scheduler.scheduleAtFixedRate(
            this::produceGameReleased,
            0, 2, TimeUnit.SECONDS
        );
        
        // Créer des joueurs moins souvent (toutes les 10 secondes)
        scheduler.scheduleAtFixedRate(
            this::producePlayerCreated,
            0, 10, TimeUnit.SECONDS
        );
        
        // ========== PHASE 2 : Événements dépendants ==========
        // Démarrent après un délai pour laisser le temps de créer des jeux/joueurs
        
        // Achats (toutes les 1.5 secondes, après 15s de délai)
        scheduler.scheduleAtFixedRate(
            this::producePurchase,
            15, 1500, TimeUnit.MILLISECONDS
        );
        
        // Sessions de jeu (toutes les 2 secondes, après 15s)
        scheduler.scheduleAtFixedRate(
            this::produceSession,
            15, 2, TimeUnit.SECONDS
        );
        
        // Ratings (toutes les 5 secondes, après 20s)
        scheduler.scheduleAtFixedRate(
            this::produceRating,
            20, 5, TimeUnit.SECONDS
        );
        
        // Crashs (toutes les 10 secondes, après 20s)
        scheduler.scheduleAtFixedRate(
            this::produceCrash,
            20, 10, TimeUnit.SECONDS
        );
        
        // Patches (toutes les 30 secondes, après 30s)
        scheduler.scheduleAtFixedRate(
            this::producePatch,
            30, 30, TimeUnit.SECONDS
        );
        
        // DLCs (toutes les 60 secondes, après 60s)
        scheduler.scheduleAtFixedRate(
            this::produceDlc,
            60, 60, TimeUnit.SECONDS
        );
        
        // ========== MONITORING ==========
        
        // Afficher les stats toutes les 30 secondes
        scheduler.scheduleAtFixedRate(
            this::printStats,
            30, 30, TimeUnit.SECONDS
        );
        
        System.out.println("✅ Orchestrateur démarré avec succès !");
    }
    
    // ========== PRODUCTEURS D'ÉVÉNEMENTS ==========
    
    private void produceGameReleased() {
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
            
            producer.send(new ProducerRecord<>(TOPIC_GAME, event.getGameId().toString(), event));
            int count = gameCount.incrementAndGet();
            System.out.println("🎮 [" + count + "] Jeu créé: " + event.getGameName());
            
        } catch (Exception e) {
            System.err.println("❌ Erreur création jeu: " + e.getMessage());
        }
    }
    
    private void producePlayerCreated() {
        try {
            PlayerCreatedEvent event = generator.generatePlayerCreated();
            
            // Stocker en mémoire
            dataStore.addPlayer(new PlayerInfo(
                event.getId().toString(),
                event.getUsername().toString(),
                event.getDistributionPlatformId() != null ? event.getDistributionPlatformId().toString() : null
            ));
            
            producer.send(new ProducerRecord<>(TOPIC_PLAYER, event.getId().toString(), event));
            int count = playerCount.incrementAndGet();
            System.out.println("👤 [" + count + "] Joueur créé: " + event.getUsername());
            
        } catch (Exception e) {
            System.err.println("❌ Erreur création joueur: " + e.getMessage());
        }
    }
    
    private void producePurchase() {
        if (!dataStore.hasMinimumData()) {
            System.out.println("⏳ En attente de données (jeux/joueurs)...");
            return;
        }
        
        try {
            GameInfo game = dataStore.getRandomGame();
            PlayerInfo player = dataStore.getRandomPlayer();
            
            GamePurchaseEvent event = generator.generatePurchase(game, player);
            producer.send(new ProducerRecord<>(TOPIC_PURCHASE, player.playerId(), event));
            
            int count = purchaseCount.incrementAndGet();
            System.out.println("💰 [" + count + "] Achat: " + player.username() + " → " + game.gameName());
            
        } catch (Exception e) {
            System.err.println("❌ Erreur achat: " + e.getMessage());
        }
    }
    
    private void produceSession() {
        if (!dataStore.hasMinimumData()) return;
        
        try {
            GameInfo game = dataStore.getRandomGame();
            PlayerInfo player = dataStore.getRandomPlayer();
            
            GameSessionEvent event = generator.generateSession(game, player);
            producer.send(new ProducerRecord<>(TOPIC_SESSION, player.playerId(), event));
            
            System.out.println("🎯 Session: " + player.username() + " joue à " + game.gameName());
            
        } catch (Exception e) {
            System.err.println("❌ Erreur session: " + e.getMessage());
        }
    }
    
    private void produceRating() {
        if (!dataStore.hasMinimumData()) return;
        
        try {
            GameInfo game = dataStore.getRandomGame();
            PlayerInfo player = dataStore.getRandomPlayer();
            
            NewRatingEvent event = generator.generateRating(game, player);
            producer.send(new ProducerRecord<>(TOPIC_RATING, game.gameId(), event));
            
            System.out.println("⭐ Rating: " + player.username() + " note " + game.gameName() + " (" + event.getRating() + "/5)");
            
        } catch (Exception e) {
            System.err.println("❌ Erreur rating: " + e.getMessage());
        }
    }
    
    private void produceCrash() {
        if (!dataStore.hasMinimumData()) return;
        
        try {
            GameInfo game = dataStore.getRandomGame();
            PlayerInfo player = dataStore.getRandomPlayer();
            
            CrashReportEvent event = generator.generateCrash(game, player);
            producer.send(new ProducerRecord<>(TOPIC_CRASH, game.gameId(), event));
            
            System.out.println("💥 Crash: " + game.gameName() + " [" + event.getSeverity() + "]");
            
        } catch (Exception e) {
            System.err.println("❌ Erreur crash: " + e.getMessage());
        }
    }
    
    private void producePatch() {
        if (!dataStore.hasGames()) return;
        
        try {
            GameInfo game = dataStore.getRandomGame();
            
            PatchPublishedEvent event = generator.generatePatch(game);
            producer.send(new ProducerRecord<>(TOPIC_PATCH, game.gameId(), event));
            
            System.out.println("🔧 Patch: " + game.gameName() + " " + event.getOldVersion() + " → " + event.getNewVersion());
            
        } catch (Exception e) {
            System.err.println("❌ Erreur patch: " + e.getMessage());
        }
    }
    
    private void produceDlc() {
        if (!dataStore.hasGames()) return;
        
        try {
            GameInfo game = dataStore.getRandomGame();
            
            DlcPublishedEvent event = generator.generateDlc(game);
            producer.send(new ProducerRecord<>(TOPIC_DLC, game.gameId(), event));
            
            System.out.println("📦 DLC: " + event.getDlcName() + " (" + event.getPrice() + "€)");
            
        } catch (Exception e) {
            System.err.println("❌ Erreur DLC: " + e.getMessage());
        }
    }
    
    private void printStats() {
        System.out.println("\n========== 📊 STATISTIQUES ==========");
        System.out.println("🎮 Jeux créés: " + dataStore.getGameCount());
        System.out.println("👤 Joueurs créés: " + dataStore.getPlayerCount());
        System.out.println("💰 Achats effectués: " + purchaseCount.get());
        System.out.println("======================================\n");
    }
    
    /**
     * Arrête proprement l'orchestrateur.
     */
    public void stop() {
        System.out.println("🛑 Arrêt de l'orchestrateur...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        producer.flush();
        producer.close();
        System.out.println("✅ Orchestrateur arrêté.");
    }
    
    // ========== MAIN ==========
    
    public static void main(String[] args) {
        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        String schema = System.getenv().getOrDefault("SCHEMA_REGISTRY_URL", "http://localhost:8081");
        
        ScheduledEventOrchestrator orchestrator = new ScheduledEventOrchestrator(bootstrap, schema);
        
        // Shutdown hook pour arrêt propre
        Runtime.getRuntime().addShutdownHook(new Thread(orchestrator::stop));
        
        orchestrator.start();
        
        // Garder l'application en vie
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            orchestrator.stop();
        }
    }
}
```

---

## 5. Configuration des fréquences

### Fichier de configuration (optionnel)

Créer un fichier `scheduler-config.properties` :

```properties
# Fréquences en millisecondes
game.release.interval=2000
player.create.interval=10000
purchase.interval=1500
session.interval=2000
rating.interval=5000
crash.interval=10000
patch.interval=30000
dlc.interval=60000

# Délais initiaux (Phase 2)
dependent.events.initial.delay=15000

# Seuils minimum pour démarrer Phase 2
minimum.games=5
minimum.players=3
```

### Lecture dynamique

```java
public class SchedulerConfig {
    private final Properties props;
    
    public SchedulerConfig(String path) throws IOException {
        props = new Properties();
        try (InputStream is = new FileInputStream(path)) {
            props.load(is);
        }
    }
    
    public long getGameReleaseInterval() {
        return Long.parseLong(props.getProperty("game.release.interval", "2000"));
    }
    
    public long getPurchaseInterval() {
        return Long.parseLong(props.getProperty("purchase.interval", "1500"));
    }
    
    // ... autres getters
}
```

---

## 6. Gestion des dépendances entre événements

### Approche 1 : Vérification simple (utilisée dans le code)

```java
private void producePurchase() {
    // Ne produit que si des jeux ET joueurs existent
    if (!dataStore.hasMinimumData()) {
        return;
    }
    // ... produire l'événement
}
```

### Approche 2 : Démarrage différé

```java
// Les événements dépendants démarrent après un délai
scheduler.scheduleAtFixedRate(
    this::producePurchase,
    15,  // ⬅️ Délai initial de 15 secondes
    2, 
    TimeUnit.SECONDS
);
```

### Approche 3 : Listener sur le DataStore (avancé)

```java
public class InMemoryDataStore {
    private final List<Runnable> onDataReadyListeners = new ArrayList<>();
    
    public void addOnDataReadyListener(Runnable listener) {
        onDataReadyListeners.add(listener);
    }
    
    public void addGame(GameInfo game) {
        games.add(game);
        checkAndNotify();
    }
    
    private void checkAndNotify() {
        if (hasMinimumData()) {
            onDataReadyListeners.forEach(Runnable::run);
            onDataReadyListeners.clear(); // Ne notifier qu'une fois
        }
    }
}

// Utilisation
dataStore.addOnDataReadyListener(() -> {
    System.out.println("✅ Données prêtes, démarrage des événements dépendants...");
    startDependentEventSchedulers();
});
```

---

## 7. Lancement et arrêt

### Tâche Gradle

Ajouter dans `build.gradle.kts` :

```kotlin
tasks.register<JavaExec>("runEventOrchestrator") {
    group = "application"
    description = "Run the scheduled event orchestrator"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.steamproject.infra.kafka.producer.ScheduledEventOrchestrator")
    dependsOn("generateAvroJava", "classes")
}
```

### Lancement

```bash
# Via Gradle
./gradlew runEventOrchestrator

# Avec variables d'environnement custom
KAFKA_BOOTSTRAP_SERVERS=kafka:29092 SCHEMA_REGISTRY_URL=http://schema-registry:8081 ./gradlew runEventOrchestrator
```

### Arrêt propre

L'application gère `SIGTERM` et `SIGINT` (Ctrl+C) grâce au shutdown hook :

```java
Runtime.getRuntime().addShutdownHook(new Thread(orchestrator::stop));
```

### Docker Compose (optionnel)

```yaml
services:
  event-orchestrator:
    build: .
    command: ["java", "-cp", "/app/libs/*", "org.steamproject.infra.kafka.producer.ScheduledEventOrchestrator"]
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      SCHEMA_REGISTRY_URL: http://schema-registry:8081
    depends_on:
      - kafka
      - schema-registry
```

---

## Résumé

| Classe | Rôle |
|--------|------|
| `InMemoryDataStore` | Stocke les jeux/joueurs créés pour les événements dépendants |
| `FakeDataGenerator` | Génère des événements Avro avec DataFaker |
| `ScheduledEventOrchestrator` | Orchestre tous les producteurs avec des fréquences configurables |

| Événement | Délai initial | Fréquence | Dépendances |
|-----------|---------------|-----------|-------------|
| `GameReleased` | 0s | 2s | Aucune |
| `PlayerCreated` | 0s | 10s | Aucune |
| `GamePurchase` | 15s | 1.5s | Jeux + Joueurs |
| `GameSession` | 15s | 2s | Jeux + Joueurs |
| `NewRating` | 20s | 5s | Jeux + Joueurs |
| `CrashReport` | 20s | 10s | Jeux + Joueurs |
| `PatchPublished` | 30s | 30s | Jeux |
| `DlcPublished` | 60s | 60s | Jeux |

---

## Checklist

- [ ] Créer `InMemoryDataStore.java`
- [ ] Créer `FakeDataGenerator.java`
- [ ] Créer `ScheduledEventOrchestrator.java`
- [ ] Ajouter la tâche Gradle `runEventOrchestrator`
- [ ] Vérifier que tous les topics Kafka existent
- [ ] Tester avec `./gradlew runEventOrchestrator`
- [ ] Ajuster les fréquences selon les besoins
