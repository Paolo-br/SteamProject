# Guide Kafka Streams - SteamProject

Ce guide explique en détail le fonctionnement de tous les fichiers du répertoire `/streams/*` et comment tester chaque composant.

---

## 📁 Structure du Répertoire `/streams/`

```
src/main/java/org/steamproject/infra/kafka/streams/
├── handlers/                       # Handlers HTTP pour les endpoints REST
│   ├── PlayerStreamsHandler.java   # Endpoints /api/players/*
│   ├── PurchaseStreamsHandler.java # Endpoint POST /api/purchase
│   └── PatchesStreamsHandler.java  # Endpoints /api/patches/*
├── GamePatchesStreams.java         # Projection patches par jeu
├── PlatformCatalogStreams.java     # Projection catalogue par plateforme
├── PlayerStreamsProjection.java    # Projection joueurs, sessions, reviews, crashes
├── PublisherGamesStreams.java      # Projection jeux par éditeur
├── StreamsRestService.java         # Service REST unifié (port 8082)
└── UserLibraryStreams.java         # Projection bibliothèque par joueur
```

---

## 🏗️ Architecture Globale

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           KAFKA TOPICS (Événements)                         │
├─────────────────────────────────────────────────────────────────────────────┤
│ player-created-events │ game-purchase-events │ game-session-events          │
│ crash-report-events   │ new-rating-events    │ review-published-events      │
│ patch-published-events│ game-released.events │ platform-catalog.events      │
│ game-published.events │ dlc-published.events │ game-updated.events          │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         KAFKA STREAMS (Topologies)                          │
├───────────────────┬───────────────────┬───────────────────┬─────────────────┤
│ PlayerStreams     │ UserLibrary       │ PlatformCatalog   │ PublisherGames  │
│ Projection        │ Streams           │ Streams           │ Streams         │
│                   │                   │                   │                 │
│ ┌───────────────┐ │ ┌───────────────┐ │ ┌───────────────┐ │ ┌─────────────┐ │
│ │ players-store │ │ │user-library-  │ │ │platform-      │ │ │publisher-   │ │
│ │ sessions-store│ │ │store          │ │ │catalog-store  │ │ │games-store  │ │
│ │ crashes-store │ │ └───────────────┘ │ └───────────────┘ │ └─────────────┘ │
│ │ reviews-store │ │                   │                   │                 │
│ └───────────────┘ │                   │                   │                 │
├───────────────────┼───────────────────┼───────────────────┼─────────────────┤
│                                 GamePatchesStreams                          │
│                            ┌──────────────────────┐                         │
│                            │  game-patches-store  │                         │
│                            └──────────────────────┘                         │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                       REST API (Interactive Queries)                        │
├─────────────────────────────────────────────────────────────────────────────┤
│  PurchaseRestService (port 8080)    │   StreamsRestService (port 8082)      │
│  - POST /api/purchase               │   - GET /api/library/{playerId}       │
│  - GET /api/players                 │   - GET /api/publishers/{id}/games    │
│  - GET /api/players/{id}/sessions   │   - GET /api/platforms/{id}/catalog   │
│  - GET /api/players/{id}/reviews    │   - GET /api/patches                  │
│  - GET /api/players/{id}/crashes    │   - GET /api/patches/{gameId}         │
│  - GET /api/players/{id}/library    │   - GET /api/games/{gameId}/version   │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 📦 Fichiers Kafka Streams - Descriptions Détaillées

### 1. `PlayerStreamsProjection.java`

**Purpose:** Projection centrale des données joueurs avec multiples state stores.

**Topics consommés:**
- `player-created-events` → `players-store`
- `game-session-events` → `sessions-store`
- `crash-report-events` → `crashes-store`
- `new-rating-events` + `review-published-events` → `reviews-store`

**State Stores:**
| Store Name | Clé | Valeur |
|------------|-----|--------|
| `players-store` | `playerId` | JSON du joueur (id, username, email, etc.) |
| `sessions-store` | `playerId` | JSON array des sessions de jeu |
| `crashes-store` | `playerId` | JSON array des crash reports |
| `reviews-store` | `playerId` | JSON array des reviews et notes |

**Méthodes statiques exposées:**
```java
PlayerStreamsProjection.getPlayer(String playerId)     // Retourne Map<String, Object>
PlayerStreamsProjection.getAllPlayers()                // Retourne List<Map<String, Object>>
PlayerStreamsProjection.getSessions(String playerId)   // Retourne List<Map<String, Object>>
PlayerStreamsProjection.getCrashes(String playerId)    // Retourne List<Map<String, Object>>
PlayerStreamsProjection.getReviews(String playerId)    // Retourne List<Map<String, Object>>
```

**Lancer individuellement:**
```bash
./gradlew runPlayerStreams
```

---

### 2. `UserLibraryStreams.java`

**Purpose:** Projection de la bibliothèque de jeux par joueur (achats).

**Topic consommé:** `game-purchase-events`

**State Store:**
| Store Name | Clé | Valeur |
|------------|-----|--------|
| `user-library-store` | `playerId` | JSON array des jeux achetés |

**Format des données:**
```json
[
  {
    "purchaseId": "uuid-123",
    "gameId": "game-456",
    "gameName": "The Witcher 3",
    "purchaseDate": "2024-01-15T10:30:00Z",
    "pricePaid": 29.99
  }
]
```

**Lancer individuellement:**
```bash
./gradlew runUserLibraryStreams
```

---

### 3. `PlatformCatalogStreams.java`

**Purpose:** Projection du catalogue de jeux par plateforme de distribution.

**Topic consommé:** `platform-catalog.events`

**State Store:**
| Store Name | Clé | Valeur |
|------------|-----|--------|
| `platform-catalog-store` | `platformId` | JSON array des jeux du catalogue |

**Format des données:** `["gameId|gameName|", "gameId2|gameName2|"]`

**Lancer individuellement:**
```bash
./gradlew runPlatformCatalogStreams
```

---

### 4. `PublisherGamesStreams.java`

**Purpose:** Projection des jeux par éditeur (publisher).

**Topics consommés:**
- `game-released.events`
- `game-published.events`
- `game-updated.events`
- `patch-published.events`
- `dlc-published.events`

**State Store:**
| Store Name | Clé | Valeur |
|------------|-----|--------|
| `publisher-games-store` | `publisherId` | JSON array des jeux publiés |

**Format des données:** `["gameId|gameName|releaseYear", ...]`

**Lancer individuellement:**
```bash
./gradlew runPublisherGamesStreams
```

---

### 5. `GamePatchesStreams.java`

**Purpose:** Projection des patches par jeu. Maintient l'historique des versions.

**Topic consommé:** `patch-published-events`

**State Store:**
| Store Name | Clé | Valeur |
|------------|-----|--------|
| `game-patches-store` | `gameId` | JSON array des patches |

**Format des données:**
```json
[
  {
    "patchId": "game-123-patch-1705312800000",
    "gameId": "game-123",
    "oldVersion": "1.0.0",
    "newVersion": "1.1.0",
    "changeLog": "Bug fixes and performance improvements",
    "timestamp": 1705312800000,
    "releaseDate": "2024-01-15T10:00:00Z"
  }
]
```

**Méthodes statiques exposées:**
```java
GamePatchesStreams.getPatches(String gameId)      // Retourne List<Map<String, Object>>
GamePatchesStreams.getAllPatches()                // Retourne Map<String, List<Map>>
GamePatchesStreams.getLatestPatch(String gameId)  // Retourne Map<String, Object>
GamePatchesStreams.getCurrentVersion(String gameId) // Retourne String
```

**Lancer individuellement:**
```bash
./gradlew runGamePatchesStreams
```

---

### 6. `StreamsRestService.java`

**Purpose:** Service REST unifié qui démarre tous les streams et expose les endpoints.

**Port:** `8082` (configurable via `-Dhttp.port`)

**Streams démarrés:**
- `UserLibraryStreams`
- `PublisherGamesStreams`
- `PlatformCatalogStreams`
- `GamePatchesStreams`

**Endpoints exposés:**

| Endpoint | Description | Store Source |
|----------|-------------|--------------|
| `GET /api/library/{playerId}` | Bibliothèque d'un joueur | `user-library-store` |
| `GET /api/publishers/{id}/games` | Jeux d'un éditeur | `publisher-games-store` |
| `GET /api/platforms/{id}/catalog` | Catalogue d'une plateforme | `platform-catalog-store` |
| `GET /api/publishers-list` | Liste de tous les éditeurs | `publisher-games-store` |
| `GET /api/catalog` | Catalogue complet | Fusion des stores |
| `GET /api/patches` | Tous les patches | `game-patches-store` |
| `GET /api/patches/{gameId}` | Patches d'un jeu | `game-patches-store` |
| `GET /api/patches/{gameId}/latest` | Dernier patch | `game-patches-store` |
| `GET /api/games/{gameId}/version` | Version actuelle | `game-patches-store` |

**Lancer le service unifié:**
```bash
./gradlew runStreamsRest
```

---

## 📂 Handlers HTTP

### `handlers/PlayerStreamsHandler.java`

Gère les endpoints relatifs aux joueurs via `PlayerStreamsProjection`.

| Endpoint | Description |
|----------|-------------|
| `GET /api/players` | Liste tous les joueurs |
| `GET /api/players/{id}/library` | Bibliothèque (via consumer classique) |
| `GET /api/players/{id}/sessions` | Sessions de jeu |
| `GET /api/players/{id}/reviews` | Reviews et notes |
| `GET /api/players/{id}/crashes` | Crash reports |

---

### `handlers/PurchaseStreamsHandler.java`

Gère la création d'achats avec validation Kafka Streams.

| Endpoint | Méthode | Description |
|----------|---------|-------------|
| `POST /api/purchase` | POST | Créer un achat |

**Paramètres (query ou JSON body):**
- `playerId` (obligatoire)
- `gameId` (obligatoire)
- `price` (optionnel)

**Validation:**
- Vérifie que le joueur existe via `PlayerStreamsProjection.getPlayer()`
- Vérifie que le jeu existe via `GameProjection` (consumer classique)

---

### `handlers/PatchesStreamsHandler.java`

Gère les endpoints relatifs aux patches via `GamePatchesStreams`.

| Endpoint | Description |
|----------|-------------|
| `GET /api/patches` | Tous les patches |
| `GET /api/patches/{gameId}` | Patches d'un jeu |
| `GET /api/patches/{gameId}/latest` | Dernier patch |
| `GET /api/games/{gameId}/patches` | Alias pour patches d'un jeu |
| `GET /api/games/{gameId}/version` | Version actuelle |

---

## 🧪 Guide de Test Complet

### Prérequis

1. **Démarrer l'infrastructure Kafka:**
```bash
docker-compose up -d
```

2. **Vérifier que les services tournent:**
```bash
docker-compose ps
# Doit afficher: zookeeper, kafka, schema-registry
```

---

### Étape 1: Créer des données de test

#### Créer un joueur
```bash
./gradlew runPlayerProducer -Pmode=create -Ptest.player.id=player-test-001 -Ptest.player.username=TestPlayer
```

#### Publier un jeu
```bash
./gradlew runPublishGame -PgameId=game-test-001
```

#### Acheter un jeu
```bash
./gradlew runPlayerProducer -Pmode=purchase -Ptest.player.id=player-test-001 -Ptest.game.id=game-test-001
```

#### Lancer une session de jeu
```bash
./gradlew runPlayerProducer -Pmode=playsession -Ptest.player.id=player-test-001 -Ptest.game.id=game-test-001
```

#### Publier un patch
```bash
./gradlew runPublishPatch -PgameId=game-test-001
```

#### Noter un jeu
```bash
./gradlew runPlayerProducer -Pmode=rate -Ptest.player.id=player-test-001 -Ptest.game.id=game-test-001
```

#### Signaler un crash
```bash
./gradlew runPlayerProducer -Pmode=crash -Ptest.player.id=player-test-001 -Ptest.game.id=game-test-001
```

---

### Étape 2: Démarrer les services REST

#### Option A: Service unifié (recommandé)
```bash
# Terminal 1 - Démarre tous les streams + REST sur port 8082
./gradlew runStreamsRest
```

#### Option B: Services séparés
```bash
# Terminal 1 - Player projection + REST port 8080
./gradlew runPurchaseRest

# Terminal 2 - Streams REST port 8082
./gradlew runStreamsRest
```

---

### Étape 3: Tester les endpoints

#### Tester les joueurs
```bash
# Liste tous les joueurs
curl http://localhost:8080/api/players

# Sessions d'un joueur
curl http://localhost:8080/api/players/player-test-001/sessions

# Reviews d'un joueur
curl http://localhost:8080/api/players/player-test-001/reviews

# Crashes d'un joueur
curl http://localhost:8080/api/players/player-test-001/crashes

# Bibliothèque d'un joueur
curl http://localhost:8080/api/players/player-test-001/library
```

#### Tester les achats
```bash
# Créer un achat via query params
curl -X POST "http://localhost:8080/api/purchase?playerId=player-test-001&gameId=game-test-002&price=29.99"

# Créer un achat via JSON body
curl -X POST http://localhost:8080/api/purchase -H "Content-Type: application/json" -d "{\"playerId\": \"player-test-001\", \"gameId\": \"game-test-003\", \"price\": 49.99}"
```

#### Tester la bibliothèque (port 8082)
```bash
# Bibliothèque d'un joueur
curl http://localhost:8082/api/library/player-test-001
```

#### Tester les éditeurs
```bash
# Liste des éditeurs
curl http://localhost:8082/api/publishers-list

# Jeux d'un éditeur
curl http://localhost:8082/api/publishers/pub-001/games
```

#### Tester les plateformes
```bash
# Catalogue d'une plateforme
curl http://localhost:8082/api/platforms/STEAM/catalog

# Catalogue complet
curl http://localhost:8082/api/catalog
```

#### Tester les patches
```bash
# Tous les patches
curl http://localhost:8082/api/patches

# Patches d'un jeu
curl http://localhost:8082/api/patches/game-test-001

# Dernier patch
curl http://localhost:8082/api/patches/game-test-001/latest

# Version actuelle
curl http://localhost:8082/api/games/game-test-001/version
```

---

### Étape 4: Scénario de test complet

Exécutez ces commandes dans l'ordre pour un test end-to-end:

```bash
# 1. Créer un joueur
./gradlew runPlayerProducer -Pmode=create -Ptest.player.id=e2e-player -Ptest.player.username=E2EPlayer

# 2. Publier un jeu
./gradlew runPublishGame -PgameId=e2e-game-001

# 3. Attendre que les événements soient traités (2s)

# 4. Acheter le jeu
./gradlew runPlayerProducer -Pmode=purchase -Ptest.player.id=e2e-player -Ptest.game.id=e2e-game-001

# 5. Jouer au jeu
./gradlew runPlayerProducer -Pmode=playsession -Ptest.player.id=e2e-player -Ptest.game.id=e2e-game-001

# 6. Publier un patch
./gradlew runPublishPatch -PgameId=e2e-game-001

# 7. Noter le jeu
./gradlew runPlayerProducer -Pmode=rate -Ptest.player.id=e2e-player -Ptest.game.id=e2e-game-001

# 8. Publier une review
./gradlew runPlayerProducer -Pmode=review_publish -Ptest.player.id=e2e-player -Ptest.game.id=e2e-game-001

# 9. Attendre le traitement (3s)

# 10. Vérifier les endpoints
curl http://localhost:8080/api/players
curl http://localhost:8082/api/library/e2e-player
curl http://localhost:8080/api/players/e2e-player/sessions
curl http://localhost:8080/api/players/e2e-player/reviews
curl http://localhost:8082/api/patches/e2e-game-001
curl http://localhost:8082/api/games/e2e-game-001/version
```

---

## 📊 Résumé des Tâches Gradle

| Tâche | Description |
|-------|-------------|
| `runPlayerStreams` | Lance PlayerStreamsProjection |
| `runUserLibraryStreams` | Lance UserLibraryStreams |
| `runPlatformCatalogStreams` | Lance PlatformCatalogStreams |
| `runPublisherGamesStreams` | Lance PublisherGamesStreams |
| `runGamePatchesStreams` | Lance GamePatchesStreams |
| `runStreamsRest` | Service REST unifié (port 8082) |
| `runPurchaseRest` | Service REST principal (port 8080) |
| `runPlayerProducer` | Producteur d'événements joueur |
| `runPublishGame` | Publier un jeu |
| `runPublishPatch` | Publier un patch |
| `runPublishDlc` | Publier un DLC |

---

## 🔧 Configuration

### Variables d'environnement

| Variable | Valeur par défaut | Description |
|----------|-------------------|-------------|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Serveurs Kafka |
| `SCHEMA_REGISTRY_URL` | `http://localhost:8081` | URL du Schema Registry |

### Propriétés système

| Propriété | Description |
|-----------|-------------|
| `-Dhttp.port=8082` | Port HTTP pour StreamsRestService |
| `-Dmode=create` | Mode du PlayerProducerApp |
| `-Ptest.player.id=xxx` | ID du joueur de test |
| `-Ptest.game.id=xxx` | ID du jeu de test |
| `-PgameId=xxx` | ID du jeu pour les événements publisher |

---

## 🔍 Modes du PlayerProducerApp

| Mode | Description | Topic |
|------|-------------|-------|
| `create` | Crée un nouveau joueur | `player-created-events` |
| `purchase` | Achat d'un jeu | `game-purchase-events` |
| `dlc_purchase` | Achat d'un DLC | `dlc-purchase-events` |
| `launch` | Lancement d'un jeu | (internal) |
| `stop` | Arrêt d'un jeu | (internal) |
| `playsession` | Session de jeu complète | `game-session-events` |
| `crash` | Signaler un crash | `crash-report-events` |
| `rate` | Noter un jeu | `new-rating-events` |
| `review_publish` | Publier une review | `review-published-events` |
| `review_vote` | Voter sur une review | (internal) |

---

## ⚠️ Dépannage

### Les stores sont vides

1. Vérifiez que Kafka est démarré: `docker-compose ps`
2. Vérifiez les topics: `docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092`
3. Attendez quelques secondes après avoir envoyé des événements (les streams ont besoin de temps pour traiter)

### Erreur "Store not ready"

Les Kafka Streams prennent 10-30 secondes pour initialiser leurs stores. Attendez le message "Store ready" dans les logs.

### Erreur de connexion Schema Registry

Vérifiez que le Schema Registry est accessible:
```bash
curl http://localhost:8081/subjects
```

### Port déjà utilisé

Si le port 8080 ou 8082 est déjà utilisé, changez-le:
```bash
./gradlew runStreamsRest -Dhttp.port=8083
```

---

## 📚 Concepts Clés

### Interactive Queries

Les Kafka Streams permettent d'interroger les state stores directement via l'API `store()`:

```java
ReadOnlyKeyValueStore<String, String> store = streams.store(
    StoreQueryParameters.fromNameAndType(STORE_NAME, QueryableStoreTypes.keyValueStore())
);
String value = store.get(key);
```

### State Stores Materialized

Chaque topology crée un ou plusieurs stores matérialisés qui persistent les données agrégées:

```java
.aggregate(
    () -> "[]",  // Valeur initiale
    (key, value, aggregate) -> { ... },  // Aggregateur
    Materialized.<String, String, KeyValueStore<...>>as(STORE_NAME)
)
```

### Avro Serialization

Tous les événements utilisent Avro avec le Schema Registry pour la sérialisation:

```java
KafkaAvroSerializer avroSerializer = new KafkaAvroSerializer();
KafkaAvroDeserializer avroDeserializer = new KafkaAvroDeserializer();
serdeConfig.put("specific.avro.reader", true);
```

---

## 📋 Récapitulatif des State Stores

| Store | Classe | Topic Source | Clé | Usage |
|-------|--------|--------------|-----|-------|
| `players-store` | PlayerStreamsProjection | player-created-events | playerId | Infos joueur |
| `sessions-store` | PlayerStreamsProjection | game-session-events | playerId | Historique sessions |
| `crashes-store` | PlayerStreamsProjection | crash-report-events | playerId | Crash reports |
| `reviews-store` | PlayerStreamsProjection | review-published-events | playerId | Reviews joueur |
| `user-library-store` | UserLibraryStreams | game-purchase-events | playerId | Bibliothèque |
| `platform-catalog-store` | PlatformCatalogStreams | platform-catalog.events | platformId | Catalogue |
| `publisher-games-store` | PublisherGamesStreams | game-released.events + ... | publisherId | Jeux éditeur |
| `game-patches-store` | GamePatchesStreams | patch-published-events | gameId | Patches jeu |

---

*Dernière mise à jour: Janvier 2025*
