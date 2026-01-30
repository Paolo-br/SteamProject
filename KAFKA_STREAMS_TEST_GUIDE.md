# Guide de Test - Kafka Streams pour PlayerStreamsProjection

Ce guide vous explique comment tester le flux Kafka Streams qui remplace les consumers classiques pour la gestion des données des joueurs.

---

## 📋 Table des matières

1. [Architecture mise en place](#architecture-mise-en-place)
2. [Prérequis](#prérequis)
3. [Démarrage des services](#démarrage-des-services)
4. [Tests des flux Kafka Streams](#tests-des-flux-kafka-streams)
5. [Vérification des données](#vérification-des-données)
6. [Dépannage](#dépannage)

---

## Architecture mise en place

### ✅ Avant (Consumer classique)

```
┌─────────────────┐
│  Kafka Topics   │
│  - player-      │
│    created-     │
│    events       │
│  - game-        │
│    session-     │
│    events       │
│  - crash-       │
│    report-      │
│    events       │
│  - review-      │
│    events       │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ PlayerConsumer  │ ◄─ Consumer classique (thread)
│  (thread)       │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ PlayerProjection│ ◄─ Stockage en mémoire (ConcurrentHashMap)
│   (Singleton)   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ PlayersHandler  │ ◄─ API REST
│   (REST API)    │
└─────────────────┘
```

### ✨ Après (Kafka Streams)

```
┌─────────────────────────────────────────┐
│            Kafka Topics                 │
│  - player-created-events                │
│  - game-session-events                  │
│  - crash-report-events                  │
│  - new-rating-events                    │
│  - review-published-events              │
└────────────┬────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────┐
│   PlayerStreamsProjection (Kafka Streams)│
│                                          │
│  ┌──────────────────────────────────┐   │
│  │ Topology (StreamsBuilder)        │   │
│  │                                  │   │
│  │  • player-created-events         │   │
│  │    └─> players-store (KTable)    │   │
│  │                                  │   │
│  │  • game-session-events           │   │
│  │    └─> sessions-store (KTable)   │   │
│  │                                  │   │
│  │  • crash-report-events           │   │
│  │    └─> crashes-store (KTable)    │   │
│  │                                  │   │
│  │  • rating/review events          │   │
│  │    └─> reviews-store (KTable)    │   │
│  └──────────────────────────────────┘   │
│                                          │
│  State Stores (Materialized Views):     │
│  • players-store  : playerId -> JSON     │
│  • sessions-store : playerId -> [JSON]   │
│  • crashes-store  : playerId -> [JSON]   │
│  • reviews-store  : playerId -> [JSON]   │
└────────────┬─────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────┐
│   Interactive Queries (API)             │
│   • getAllPlayers()                     │
│   • getSessions(playerId)               │
│   • getCrashes(playerId)                │
│   • getReviews(playerId)                │
└────────────┬─────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────┐
│   PlayersHandler (REST API)             │
│   GET /api/players                      │
│   GET /api/players/{id}/sessions        │
│   GET /api/players/{id}/reviews         │
│   GET /api/players/{id}/crashes         │
└─────────────────────────────────────────┘
```

### 🔑 Différences clés

| Aspect | Consumer Classique | Kafka Streams |
|--------|-------------------|---------------|
| **Architecture** | Pull model (poll) | Push model (stream processing) |
| **État** | Stockage manuel (HashMap) | State Stores matérialisés |
| **Scalabilité** | Thread unique | Multi-threads, distribué |
| **Requêtes** | Direct sur HashMap | Interactive Queries sur State Stores |
| **Tolérance aux pannes** | État perdu si crash | État sauvegardé dans Kafka (changelog topics) |
| **Traitement** | Message par message | Pipeline de transformations |

---

## Prérequis

Avant de commencer, assurez-vous d'avoir :

- ✅ **Docker Desktop** installé et démarré
- ✅ **Java 23** (ou compatible)
- ✅ **Gradle** configuré
- ✅ Ports disponibles : `2181`, `9092`, `8081`, `8080`

---

## Démarrage des services

### Étape 1 : Démarrer l'infrastructure Kafka

```powershell
# Dans le répertoire du projet SteamProject
cd C:\Users\raph_\Desktop\Polytech\ET4\S7\Java\Projet\SteamProject

# Démarrer tous les services Docker (Zookeeper, Kafka, Schema Registry)
docker-compose up -d

# Vérifier que tous les conteneurs sont en cours d'exécution
docker ps
```

**Sortie attendue :**
```
CONTAINER ID   IMAGE                                   STATUS       PORTS
...            confluentinc/cp-schema-registry:7.4.1   Up           0.0.0.0:8081->8081/tcp
...            confluentinc/cp-kafka:7.4.1             Up           0.0.0.0:9092->9092/tcp
...            confluentinc/cp-zookeeper:7.4.1         Up           0.0.0.0:2181->2181/tcp
```

### Étape 2 : Compiler le projet

```powershell
# Générer les classes Avro et compiler
.\gradlew.bat generateAvroJava classes
```

**✅ Succès attendu :** `BUILD SUCCESSFUL`

---

## Tests des flux Kafka Streams

### Test 1 : Démarrer le service REST avec Kafka Streams

```powershell
# Lancer le service REST (qui démarre automatiquement PlayerStreamsProjection)
.\gradlew.bat runPurchaseConsumer
```

**📝 Ce qui se passe :**
1. Le service démarre `PlayerStreamsProjection` en arrière-plan
2. Kafka Streams crée 4 State Stores :
   - `players-store`
   - `sessions-store`
   - `crashes-store`
   - `reviews-store`
3. L'API REST écoute sur `http://localhost:8080`

**Sortie attendue :**
```
Starting PlayerStreamsProjection...
PlayerStreamsProjection started with stores: players-store, sessions-store, crashes-store, reviews-store
Purchase REST service listening on http://localhost:8080/api/players/{playerId}/library
```

---

### Test 2 : Créer un joueur (Producer)

**Dans un NOUVEAU terminal PowerShell :**

```powershell
cd C:\Users\raph_\Desktop\Polytech\ET4\S7\Java\Projet\SteamProject

# Créer un joueur de test
.\gradlew.bat runPlayerProducer
```

**Sortie attendue :**
```
Sent PlayerCreatedEvent id=d8289706-feb4-41ad-a3fa-bb4292d6bd72
BUILD SUCCESSFUL
```

**📝 Ce qui se passe :**
1. Un événement `PlayerCreatedEvent` est envoyé au topic `player-created-events`
2. Kafka Streams consomme cet événement
3. Les données sont stockées dans `players-store` (State Store)

---

### Test 3 : Vérifier les données via l'API REST

**Dans un 3ème terminal ou via un navigateur :**

```powershell
# Lister tous les joueurs
curl http://localhost:8080/api/players

# OU avec PowerShell
Invoke-RestMethod -Uri "http://localhost:8080/api/players" -Method GET | ConvertTo-Json
```

**Sortie attendue :**
```json
[
  {
    "id": "d8289706-feb4-41ad-a3fa-bb4292d6bd72",
    "username": "player123",
    "email": "player123@example.com",
    "registrationDate": "2026-01-30T10:30:00Z",
    "firstName": "John",
    "lastName": "Doe",
    "dateOfBirth": "1990-01-01",
    "timestamp": 1738234200000,
    "gdprConsent": true,
    "gdprConsentDate": "2026-01-30T10:30:00Z"
  }
]
```

---

### Test 4 : Créer des événements de session

```powershell
# Créer une session de jeu
.\gradlew.bat runPlayerProducer -Dmode=session -Dtest.player.id=d8289706-feb4-41ad-a3fa-bb4292d6bd72
```

**Vérifier les sessions :**
```powershell
curl http://localhost:8080/api/players/d8289706-feb4-41ad-a3fa-bb4292d6bd72/sessions
```

**Sortie attendue :**
```json
[
  {
    "sessionId": "session-123",
    "gameId": "game-456",
    "gameName": "The Witcher 3",
    "duration": 120,
    "sessionType": "NORMAL",
    "timestamp": 1738234500000
  }
]
```

---

### Test 5 : Créer des événements de crash

```powershell
# Créer un rapport de crash
.\gradlew.bat runPlayerProducer -Dmode=crash -Dtest.player.id=d8289706-feb4-41ad-a3fa-bb4292d6bd72
```

**Vérifier les crashes :**
```powershell
curl http://localhost:8080/api/players/d8289706-feb4-41ad-a3fa-bb4292d6bd72/crashes
```

**Sortie attendue :**
```json
[
  {
    "crashId": "crash-789",
    "gameId": "game-456",
    "gameName": "The Witcher 3",
    "platform": "PC",
    "severity": "HIGH",
    "errorType": "NullPointerException",
    "errorMessage": "Error at line 42",
    "timestamp": 1738234700000
  }
]
```

---

### Test 6 : Créer des reviews

```powershell
# Créer une review
.\gradlew.bat runPlayerProducer -Dmode=review -Dtest.player.id=d8289706-feb4-41ad-a3fa-bb4292d6bd72
```

**Vérifier les reviews :**
```powershell
curl http://localhost:8080/api/players/d8289706-feb4-41ad-a3fa-bb4292d6bd72/reviews
```

**Sortie attendue :**
```json
[
  {
    "reviewId": "review-101",
    "gameId": "game-456",
    "rating": 5,
    "title": "Excellent game!",
    "text": "Amazing story and graphics",
    "isSpoiler": false,
    "timestamp": 1738234900000
  }
]
```

---

## Vérification des données

### Vérifier les State Stores Kafka Streams

Les State Stores sont stockés localement dans :
```
C:\Users\raph_\AppData\Local\Temp\kafka-streams\player-streams-projection\
```

Vous pouvez voir les dossiers :
- `0_0\rocksdb\players-store\`
- `0_0\rocksdb\sessions-store\`
- `0_0\rocksdb\crashes-store\`
- `0_0\rocksdb\reviews-store\`

### Vérifier les Changelog Topics dans Kafka

Kafka Streams crée automatiquement des **changelog topics** pour sauvegarder l'état :

```powershell
# Lister les topics Kafka
docker exec steamproject-kafka-1 kafka-topics --list --bootstrap-server localhost:9092
```

**Topics attendus :**
```
player-streams-projection-players-store-changelog
player-streams-projection-sessions-store-changelog
player-streams-projection-crashes-store-changelog
player-streams-projection-reviews-store-changelog
```

---

## Dépannage

### ❌ Erreur : "Could not find Schema Registry"

**Solution :**
```powershell
docker-compose up -d schema-registry
```

### ❌ Erreur : "Store not yet ready"

**Cause :** Kafka Streams n'a pas encore initialisé les stores.

**Solution :** Attendre quelques secondes (~3-5s) après le démarrage.

### ❌ Pas de données dans l'API

**Vérifications :**
1. Le service `runPurchaseConsumer` est-il en cours d'exécution ?
2. Avez-vous bien envoyé des événements avec `runPlayerProducer` ?
3. Les topics Kafka existent-ils ?

```powershell
# Vérifier les messages dans un topic
docker exec steamproject-kafka-1 kafka-console-consumer --topic player-created-events --from-beginning --bootstrap-server localhost:9092 --max-messages 5
```

### ❌ Build Gradle échoue

**Solution :** Vérifier que Java 23 est bien configuré :
```powershell
.\gradlew.bat --version
```

Si Java 21 est demandé, modifiez [`build.gradle.kts`](build.gradle.kts#L54) :
```kotlin
kotlin {
    jvmToolchain(23)  // Utiliser Java 23
}
```

---

## 🎯 Résumé des commandes

| Action | Commande |
|--------|----------|
| **Démarrer Kafka** | `docker-compose up -d` |
| **Compiler** | `.\gradlew.bat generateAvroJava classes` |
| **Démarrer le service REST + Streams** | `.\gradlew.bat runPurchaseConsumer` |
| **Créer un joueur** | `.\gradlew.bat runPlayerProducer` |
| **Voir les joueurs** | `curl http://localhost:8080/api/players` |
| **Voir les sessions** | `curl http://localhost:8080/api/players/{id}/sessions` |
| **Voir les crashes** | `curl http://localhost:8080/api/players/{id}/crashes` |
| **Voir les reviews** | `curl http://localhost:8080/api/players/{id}/reviews` |
| **Arrêter Kafka** | `docker-compose down` |

---

## 📚 Concepts Kafka Streams utilisés

### 1. **KStream**
Flux d'événements (infini). Chaque message est une mise à jour.

```java
KStream<String, Object> playerCreatedStream = builder.stream("player-created-events", ...)
```

### 2. **KTable (via aggregate)**
Vue matérialisée (état actuel). Les messages sont agrégés par clé.

```java
playerCreatedStream
    .groupByKey()
    .aggregate(
        () -> "{}",           // Valeur initiale
        (key, value, agg) -> { /* logique d'agrégation */ },
        Materialized.as("players-store")  // State Store
    )
```

### 3. **State Stores**
Stockage local des données avec sauvegarde automatique dans Kafka (changelog topics).

### 4. **Interactive Queries**
Requêtes interactives sur les State Stores depuis l'extérieur de Kafka Streams.

```java
ReadOnlyKeyValueStore<String, String> store = streamsInstance.store(
    StoreQueryParameters.fromNameAndType("players-store", QueryableStoreTypes.keyValueStore())
);
String playerJson = store.get(playerId);
```

---

## ✅ Avantages de Kafka Streams vs Consumer Classique

| Avantage | Description |
|----------|-------------|
| **Scalabilité** | Kafka Streams peut distribuer le traitement sur plusieurs instances |
| **Tolérance aux pannes** | État sauvegardé automatiquement dans Kafka (changelog topics) |
| **Requêtes interactives** | Accès direct aux State Stores sans passer par Kafka |
| **Stateful processing** | Agrégations, jointures, fenêtres temporelles |
| **Exactement une fois** | Garanties transactionnelles |
| **Moins de code** | Pas besoin de gérer manuellement les offsets et le commit |

---

## 🎓 Pour aller plus loin

- Ajouter des **fenêtres temporelles** (hopping, tumbling)
- Implémenter des **jointures** entre streams (ex: enrichir les achats avec les infos du jeu)
- Utiliser **Kafka Streams DSL** pour des transformations complexes
- Monitorer avec **Kafka Streams Metrics** et **JMX**
- Déployer sur plusieurs instances pour la scalabilité

---

**✨ Bravo ! Vous avez maintenant un flux Kafka Streams fonctionnel pour gérer les données des joueurs !**
