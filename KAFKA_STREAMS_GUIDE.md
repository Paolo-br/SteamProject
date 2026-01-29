# Guide d'implémentation Kafka Streams

Ce guide explique comment implémenter **Kafka Streams** dans le projet SteamProject. Il est destiné à un développeur ayant une connaissance de base du projet et de Kafka.

---

## Table des matières

1. [Qu'est-ce que Kafka Streams ?](#1-quest-ce-que-kafka-streams-)
2. [Architecture dans le projet](#2-architecture-dans-le-projet)
3. [Configuration et dépendances](#3-configuration-et-dépendances)
4. [Concepts clés](#4-concepts-clés)
5. [Implémentation pas à pas](#5-implémentation-pas-à-pas)
6. [Exemples concrets du projet](#6-exemples-concrets-du-projet)
7. [Bonnes pratiques](#7-bonnes-pratiques)
8. [Tests et debug](#8-tests-et-debug)

---

## 1. Qu'est-ce que Kafka Streams ?

### Définition

**Kafka Streams** est une bibliothèque cliente Java pour construire des applications et microservices qui transforment des données en temps réel. Contrairement à Kafka classique (producer/consumer), Kafka Streams permet de :

- **Transformer** des flux de données (map, filter, flatMap)
- **Agréger** des données (count, reduce, aggregate)
- **Joindre** des flux entre eux (join, leftJoin)
- **Maintenir un état** via des KTables et des State Stores

### Différence avec Producer/Consumer classique

| Aspect | Producer/Consumer | Kafka Streams |
|--------|-------------------|---------------|
| **Rôle** | Envoyer/Lire des messages | Transformer des flux en temps réel |
| **État** | Stateless | Stateful (KTable, State Store) |
| **Traitement** | Message par message | Pipeline de transformations |
| **Cas d'usage** | Ingestion simple | Agrégations, jointures, projections |

### Quand utiliser Kafka Streams ?

✅ Agrégation de données en temps réel (ex: compter les achats par joueur)  
✅ Enrichissement de données (ex: joindre un achat avec les infos du joueur)  
✅ Création de vues matérialisées (ex: bibliothèque d'un joueur)  
✅ Détection de patterns (ex: alertes sur crashs fréquents)  

❌ Simple lecture/écriture de messages → utiliser Producer/Consumer  
❌ Traitement batch → utiliser Spark, Flink  

---

## 2. Architecture dans le projet

### Structure des fichiers

```
src/main/java/org/steamproject/infra/kafka/
├── consumer/           # Consommateurs Kafka classiques
├── producer/           # Producteurs Kafka
└── streams/            # Applications Kafka Streams ← C'EST ICI
    ├── UserLibraryStreams.java      # Projection: bibliothèque utilisateur
    └── StreamsRestService.java      # API REST pour requêtes interactives
```

### Flux de données

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Producers     │     │  Kafka Topics   │     │ Kafka Streams   │
│ (événements)    │────▶│ purchase.events │────▶│ (agrégations)   │
│                 │     │ rating.events   │     │                 │
│                 │     │ crash.events    │     │                 │
└─────────────────┘     └─────────────────┘     └────────┬────────┘
                                                         │
                                                         ▼
                                               ┌─────────────────┐
                                               │  State Stores   │
                                               │ (KTables)       │
                                               │                 │
                                               │ user-library    │
                                               │ game-ratings    │
                                               │ crash-counts    │
                                               └─────────────────┘
```

---

## 3. Configuration et dépendances

### Dépendances Gradle (déjà présentes)

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.apache.kafka:kafka-clients:3.6.1")
    implementation("org.apache.kafka:kafka-streams:3.6.1")
    implementation("io.confluent:kafka-avro-serializer:7.4.1")
}
```

### Configuration de base

```java
Properties props = new Properties();

// Identifiant unique de l'application Streams
props.put(StreamsConfig.APPLICATION_ID_CONFIG, "mon-streams-app");

// Serveurs Kafka
props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

// Sérialisation par défaut (clé/valeur)
props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

// Schema Registry pour Avro
props.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://localhost:8081");
```

### Variables d'environnement (Docker)

```yaml
# docker-compose.yml
environment:
  KAFKA_BOOTSTRAP_SERVERS: kafka:29092
  SCHEMA_REGISTRY_URL: http://schema-registry:8081
```

---

## 4. Concepts clés

### 4.1 KStream vs KTable

| Concept | Description | Analogie |
|---------|-------------|----------|
| **KStream** | Flux continu d'événements | Journal d'événements |
| **KTable** | Vue matérialisée avec état | Table de base de données |

```java
// KStream: chaque message est traité indépendamment
KStream<String, GamePurchaseEvent> purchases = builder.stream("purchase.events");

// KTable: dernière valeur par clé, avec état
KTable<String, Long> purchaseCounts = purchases
    .groupByKey()
    .count();
```

### 4.2 State Store

Un **State Store** est un stockage local persistant qui permet :
- De maintenir l'état des agrégations
- D'effectuer des requêtes interactives (Interactive Queries)

```java
// Création d'un store matérialisé
.aggregate(
    () -> initialValue,
    (key, value, aggregate) -> newAggregate,
    Materialized.<String, String, KeyValueStore<Bytes, byte[]>>as("my-store-name")
)
```

### 4.3 Serdes (Serializer/Deserializer)

Les **Serdes** définissent comment sérialiser/désérialiser les clés et valeurs :

```java
// Types de base
Serdes.String()
Serdes.Long()
Serdes.Integer()

// Avro (pour nos événements)
Serde<Object> avroSerde = Serdes.serdeFrom(avroSerializer, avroDeserializer);
```

### 4.4 Topology

La **topology** est le graphe de traitement des données :

```java
StreamsBuilder builder = new StreamsBuilder();

// Définir la topology
KStream<String, Event> stream = builder.stream("input-topic");
stream
    .filter((key, value) -> value != null)
    .mapValues(value -> transform(value))
    .to("output-topic");

// Construire et démarrer
KafkaStreams streams = new KafkaStreams(builder.build(), props);
streams.start();
```

---

## 5. Implémentation pas à pas

### Étape 1 : Créer la classe Streams

```java
package org.steamproject.infra.kafka.streams;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import java.util.Properties;

public class MonNouveauStreams {
    
    public static final String STORE_NAME = "mon-store";
    private static volatile KafkaStreams streamsInstance;

    public static void main(String[] args) {
        startStreams();
    }

    public static KafkaStreams startStreams() {
        if (streamsInstance != null) return streamsInstance;
        
        // Configuration
        Properties props = buildConfig();
        
        // Topology
        StreamsBuilder builder = new StreamsBuilder();
        buildTopology(builder);
        
        // Démarrage
        streamsInstance = new KafkaStreams(builder.build(), props);
        streamsInstance.start();
        
        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (streamsInstance != null) streamsInstance.close();
        }));
        
        return streamsInstance;
    }
    
    private static Properties buildConfig() {
        String bootstrap = System.getenv().getOrDefault(
            "KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        String schema = System.getenv().getOrDefault(
            "SCHEMA_REGISTRY_URL", "http://localhost:8081");
            
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "mon-nouveau-streams");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        // ... autres configs
        return props;
    }
    
    private static void buildTopology(StreamsBuilder builder) {
        // À implémenter selon le cas d'usage
    }
    
    public static KafkaStreams getStreams() {
        return streamsInstance;
    }
}
```

### Étape 2 : Configurer le Serde Avro

```java
private static Serde<Object> createAvroSerde(String schemaRegistryUrl) {
    KafkaAvroSerializer avroSerializer = new KafkaAvroSerializer();
    KafkaAvroDeserializer avroDeserializer = new KafkaAvroDeserializer();
    
    Map<String, Object> serdeConfig = new HashMap<>();
    serdeConfig.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
    serdeConfig.put("specific.avro.reader", true); // Pour SpecificRecord
    
    avroSerializer.configure(serdeConfig, false);
    avroDeserializer.configure(serdeConfig, false);
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    Serde<Object> avroSerde = Serdes.serdeFrom(
        (Serializer) avroSerializer,
        (Deserializer) avroDeserializer
    );
    
    return avroSerde;
}
```

### Étape 3 : Définir la topology

```java
private static void buildTopology(StreamsBuilder builder) {
    Serde<Object> avroSerde = createAvroSerde("http://localhost:8081");
    
    // Lire depuis un topic
    KStream<String, Object> events = builder.stream(
        "mon-topic",
        Consumed.with(Serdes.String(), avroSerde)
    );
    
    // Transformer et agréger
    events
        .filter((key, value) -> value != null)
        .groupByKey()
        .aggregate(
            () -> "initial",
            (key, value, aggregate) -> {
                // Logique d'agrégation
                return aggregate + "," + value.toString();
            },
            Materialized.<String, String, KeyValueStore<Bytes, byte[]>>as(STORE_NAME)
                .withKeySerde(Serdes.String())
                .withValueSerde(Serdes.String())
        );
}
```

### Étape 4 : Ajouter la tâche Gradle

```kotlin
// build.gradle.kts
tasks.register<JavaExec>("runMonNouveauStreams") {
    group = "application"
    description = "Run Mon Nouveau Streams app"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.steamproject.infra.kafka.streams.MonNouveauStreams")
    dependsOn("generateAvroJava", "classes")
}
```

### Étape 5 : Lancer l'application

```bash
# Via Gradle
./gradlew runMonNouveauStreams

# Ou via Docker
docker-compose up mon-streams-service
```

---

## 6. Exemples concrets du projet

## Tâches assignées — Streaming & Projections (à suivre)

But : fournir une topologie Kafka Streams stable, stores matérialisés et API REST de projection.

Consignes générales :
- Ne modifiez pas la logique métier (fichiers dans `src/main/java/org/steamproject/service/*`).
- Ne touchez pas à l'UI (`src/main/kotlin/ui/**`).
- Lisez : `README.md`, `EVENTS_GUIDE.md`, `KAFKA_STREAMS_GUIDE.md` (ce fichier).

Checklist détaillée (ordre recommandé) :
1. Valider les schémas Avro dans `src/main/avro/*.avsc` et la configuration du Schema Registry.
2. Stabiliser `UserLibraryStreams` :
    - Assurer la configuration du Serde Avro (`specific.avro.reader=true`) et l'URL du Schema Registry.
    - Gérer les erreurs de désérialisation (log, skip, dead-letter si besoin).
    - Confirmer le nom du store matérialisé `user-library-store` et sa API d'accès.
3. Ajouter/valider d'autres stores matérialisés nécessaires (catalogue, purchases) avec noms contractuels.
4. Exposer endpoints REST de projection stables :
    - `GET /api/catalog` — retourne le catalogue projeté.
    - `GET /api/players` — liste des joueurs projetés.
    - `GET /api/players/{id}/library` — lire `user-library-store`.
    - `GET /api/purchases` — achats récents/projetés.
    - `GET /health` — healthcheck de la projection.
5. Tests d'intégration locaux :
    - Démarrer l'infra (`docker-compose up -d`) puis démarrer la topologie.
    - Produire événements tests (`runPlayerProducer`, `runPublishGame`) et vérifier les endpoints via `curl`.
6. Documentation : mettre à jour ce guide (`KAFKA_STREAMS_GUIDE.md`) avec commandes, noms de stores et exemples `curl`.

Livrable attendu : topologie démarrable via Gradle, stores matérialisés accessibles, endpoints REST documentés.

### Exemple 1 : Bibliothèque utilisateur (UserLibraryStreams)

**Objectif** : Maintenir la liste des jeux possédés par chaque joueur.

**Topic source** : `purchase.events` (GamePurchaseEvent)  
**State Store** : `user-library-store`  
**Clé** : `playerId`  
**Valeur** : JSON array des jeux achetés

```java
// Fichier existant: UserLibraryStreams.java
KStream<String, Object> purchases = builder.stream(
    "purchase.events", 
    Consumed.with(Serdes.String(), avroSerde)
);

purchases.groupByKey()
    .aggregate(
        () -> "[]",  // Initialisation: tableau vide
        (playerId, purchaseObj, aggregate) -> {
            GamePurchaseEvent purchase = (GamePurchaseEvent) purchaseObj;
            ArrayNode arr = (ArrayNode) om.readTree(aggregate);
            ObjectNode obj = om.createObjectNode();
            obj.put("purchaseId", purchase.getPurchaseId().toString());
            obj.put("gameId", purchase.getGameId().toString());
            obj.put("gameName", purchase.getGameName().toString());
            obj.put("purchaseDate", Instant.ofEpochMilli(purchase.getTimestamp()).toString());
            obj.put("pricePaid", purchase.getPricePaid());
            arr.add(obj);
            return om.writeValueAsString(arr);
        },
        Materialized.<String, String, KeyValueStore<Bytes, byte[]>>as(STORE_NAME)
);
```

### Exemple 2 : Agrégation des crashs par jeu (À implémenter)

**Objectif** : Compter les crashs par jeu et par sévérité pour la page Incidents.

**Topic source** : `crash.events` (CrashReportEvent)  
**State Store** : `crash-aggregation-store`  
**Clé** : `gameId`

```java
package org.steamproject.infra.kafka.streams;

import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.common.utils.Bytes;
import org.steamproject.events.CrashReportEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class CrashAggregationStreams {
    
    public static final String STORE_NAME = "crash-aggregation-store";
    private static volatile KafkaStreams streamsInstance;
    
    public static void main(String[] args) {
        startStreams();
    }
    
    public static KafkaStreams startStreams() {
        if (streamsInstance != null) return streamsInstance;
        
        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        String schema = System.getenv().getOrDefault("SCHEMA_REGISTRY_URL", "http://localhost:8081");
        
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "crash-aggregation-streams");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        
        StreamsBuilder builder = new StreamsBuilder();
        Serde<Object> avroSerde = createAvroSerde(schema);
        ObjectMapper om = new ObjectMapper();
        
        // Lire les événements de crash
        KStream<String, Object> crashes = builder.stream(
            "crash.events", 
            Consumed.with(Serdes.String(), avroSerde)
        );
        
        // Re-key par gameId et agréger
        crashes
            .selectKey((key, value) -> {
                CrashReportEvent crash = (CrashReportEvent) value;
                return crash.getGameId().toString();
            })
            .groupByKey()
            .aggregate(
                // Initialisation: JSON avec compteurs à 0
                () -> {
                    ObjectNode init = om.createObjectNode();
                    init.put("totalCrashes", 0);
                    init.put("criticalCount", 0);
                    init.put("majorCount", 0);
                    init.put("minorCount", 0);
                    return init.toString();
                },
                // Agrégation: incrémenter les compteurs
                (gameId, crashObj, aggregate) -> {
                    try {
                        CrashReportEvent crash = (CrashReportEvent) crashObj;
                        ObjectNode agg = (ObjectNode) om.readTree(aggregate);
                        
                        // Incrémenter le total
                        agg.put("totalCrashes", agg.get("totalCrashes").asInt() + 1);
                        agg.put("gameName", crash.getGameName().toString());
                        agg.put("lastCrashTime", crash.getTimestamp());
                        
                        // Incrémenter par sévérité
                        String severity = crash.getSeverity().toString();
                        switch (severity) {
                            case "CRITICAL":
                                agg.put("criticalCount", agg.get("criticalCount").asInt() + 1);
                                break;
                            case "MAJOR":
                                agg.put("majorCount", agg.get("majorCount").asInt() + 1);
                                break;
                            case "MINOR":
                                agg.put("minorCount", agg.get("minorCount").asInt() + 1);
                                break;
                        }
                        
                        return om.writeValueAsString(agg);
                    } catch (Exception e) {
                        return aggregate;
                    }
                },
                Materialized.<String, String, KeyValueStore<Bytes, byte[]>>as(STORE_NAME)
                    .withKeySerde(Serdes.String())
                    .withValueSerde(Serdes.String())
            );
        
        streamsInstance = new KafkaStreams(builder.build(), props);
        streamsInstance.start();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (streamsInstance != null) streamsInstance.close();
        }));
        
        System.out.println("CrashAggregationStreams started with store: " + STORE_NAME);
        return streamsInstance;
    }
    
    public static KafkaStreams getStreams() {
        return streamsInstance;
    }
}
```

### Exemple 3 : Moyenne des notes par jeu (À implémenter)

**Objectif** : Calculer la note moyenne et le nombre d'avis par jeu.

**Topic source** : `rating.events` (NewRatingEvent)  
**State Store** : `game-ratings-store`

```java
package org.steamproject.infra.kafka.streams;

import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import org.steamproject.events.NewRatingEvent;

public class GameRatingsStreams {
    
    public static final String STORE_NAME = "game-ratings-store";
    
    private static void buildTopology(StreamsBuilder builder, Serde<Object> avroSerde) {
        ObjectMapper om = new ObjectMapper();
        
        KStream<String, Object> ratings = builder.stream(
            "rating.events",
            Consumed.with(Serdes.String(), avroSerde)
        );
        
        // Re-key par gameId
        ratings
            .selectKey((key, value) -> {
                NewRatingEvent rating = (NewRatingEvent) value;
                return rating.getGameId().toString();
            })
            .groupByKey()
            .aggregate(
                // Init: totalRating=0, count=0, avgRating=0.0
                () -> {
                    ObjectNode init = om.createObjectNode();
                    init.put("totalRating", 0);
                    init.put("count", 0);
                    init.put("avgRating", 0.0);
                    init.put("recommendedCount", 0);
                    return init.toString();
                },
                // Aggregator
                (gameId, ratingObj, aggregate) -> {
                    try {
                        NewRatingEvent rating = (NewRatingEvent) ratingObj;
                        ObjectNode agg = (ObjectNode) om.readTree(aggregate);
                        
                        int newTotal = agg.get("totalRating").asInt() + rating.getRating();
                        int newCount = agg.get("count").asInt() + 1;
                        double newAvg = (double) newTotal / newCount;
                        int recommended = agg.get("recommendedCount").asInt() 
                            + (rating.getIsRecommended() ? 1 : 0);
                        
                        agg.put("gameName", rating.getGameName().toString());
                        agg.put("totalRating", newTotal);
                        agg.put("count", newCount);
                        agg.put("avgRating", Math.round(newAvg * 100.0) / 100.0);
                        agg.put("recommendedCount", recommended);
                        agg.put("recommendedPct", 
                            Math.round((double) recommended / newCount * 100.0));
                        
                        return om.writeValueAsString(agg);
                    } catch (Exception e) {
                        return aggregate;
                    }
                },
                Materialized.<String, String, KeyValueStore<Bytes, byte[]>>as(STORE_NAME)
            );
    }
}
```

### Exemple 4 : Jointure de flux (enrichissement)

**Objectif** : Enrichir les achats avec les infos du jeu.

```java
// Topic 1: purchase.events (GamePurchaseEvent)
// Topic 2: game.events (table des jeux)

KStream<String, GamePurchaseEvent> purchases = builder.stream("purchase.events");
KTable<String, GameInfo> games = builder.table("game.events");

// Jointure: enrichir chaque achat avec les infos du jeu
KStream<String, EnrichedPurchase> enriched = purchases
    .selectKey((key, value) -> value.getGameId().toString()) // Re-key par gameId
    .join(
        games,
        (purchase, game) -> {
            // Créer un objet enrichi
            return new EnrichedPurchase(
                purchase,
                game.getGenre(),
                game.getPublisher(),
                game.getReleaseDate()
            );
        }
    );

enriched.to("enriched-purchases");
```

---

## 7. Bonnes pratiques

### Configuration

```java
// Toujours utiliser des variables d'environnement
String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");

// Configurer la tolérance aux pannes
props.put(StreamsConfig.NUM_STANDBY_REPLICAS_CONFIG, 1);
props.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, 3);

// Configurer le commit interval (défaut: 30s)
props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1000);
```

### Gestion des erreurs

```java
// Handler d'erreur personnalisé
props.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
    LogAndContinueExceptionHandler.class);

// Ou implémenter son propre handler
public class MyExceptionHandler implements DeserializationExceptionHandler {
    @Override
    public DeserializationHandlerResponse handle(ProcessorContext context,
                                                   ConsumerRecord<byte[], byte[]> record,
                                                   Exception exception) {
        log.error("Erreur de désérialisation: {}", exception.getMessage());
        // Continuer ou arrêter selon la criticité
        return DeserializationHandlerResponse.CONTINUE;
    }
}
```

### Nommage des stores

```java
// Convention: <domaine>-<entité>-store
public static final String STORE_NAME = "user-library-store";
public static final String CRASH_STORE = "crash-aggregation-store";
public static final String RATINGS_STORE = "game-ratings-store";
```

### Interactive Queries

Pour interroger un State Store depuis l'extérieur :

```java
// Obtenir le store
ReadOnlyKeyValueStore<String, String> store = 
    streams.store(
        StoreQueryParameters.fromNameAndType(
            STORE_NAME, 
            QueryableStoreTypes.keyValueStore()
        )
    );

// Récupérer une valeur
String userLibrary = store.get("player-123");

// Itérer sur toutes les entrées
try (KeyValueIterator<String, String> iter = store.all()) {
    while (iter.hasNext()) {
        KeyValue<String, String> entry = iter.next();
        System.out.println(entry.key + " -> " + entry.value);
    }
}
```

---

## 8. Tests et debug

### Logs

```java
// Activer les logs Kafka Streams
props.put(StreamsConfig.LOG_DIR_CONFIG, "./logs");

// Dans logback.xml ou log4j2.xml
<logger name="org.apache.kafka.streams" level="DEBUG"/>
```

### TopologyTestDriver (tests unitaires)

```java
@Test
void testAggregation() {
    StreamsBuilder builder = new StreamsBuilder();
    buildTopology(builder);
    
    try (TopologyTestDriver driver = new TopologyTestDriver(builder.build(), props)) {
        TestInputTopic<String, GamePurchaseEvent> inputTopic = 
            driver.createInputTopic("purchase.events", ...);
            
        KeyValueStore<String, String> store = 
            driver.getKeyValueStore(STORE_NAME);
        
        // Envoyer un événement
        inputTopic.pipeInput("player-1", testPurchaseEvent);
        
        // Vérifier le résultat
        String result = store.get("player-1");
        assertNotNull(result);
        assertTrue(result.contains("game-123"));
    }
}
```

### Debug avec docker-compose

```bash
# Voir les topics
docker-compose exec kafka kafka-topics --list --bootstrap-server localhost:9092

# Consommer un topic
docker-compose exec kafka kafka-console-consumer \
    --bootstrap-server localhost:9092 \
    --topic purchase.events \
    --from-beginning

# Vérifier les groupes de consommateurs
docker-compose exec kafka kafka-consumer-groups \
    --bootstrap-server localhost:9092 \
    --describe --all-groups
```

---

## Résumé des streams à implémenter

| Nom | Topic source | Store | Description |
|-----|--------------|-------|-------------|
| `UserLibraryStreams` ✅ | `purchase.events` | `user-library-store` | Bibliothèque par joueur |
| `CrashAggregationStreams` | `crash.events` | `crash-aggregation-store` | Crashs agrégés par jeu |
| `GameRatingsStreams` | `rating.events` | `game-ratings-store` | Notes moyennes par jeu |
| `SalesAggregationStreams` | `purchase.events` | `sales-store` | Ventes par jeu/région |
| `PlayerActivityStreams` | `game-session.events` | `player-activity-store` | Temps de jeu par joueur |

---

## Checklist pour un nouveau Streams

- [ ] Créer la classe dans `src/main/java/org/steamproject/infra/kafka/streams/`
- [ ] Définir le `STORE_NAME` et `APPLICATION_ID_CONFIG` uniques
- [ ] Configurer le Serde Avro si nécessaire
- [ ] Définir la topology (source → transformations → sink/store)
- [ ] Ajouter le shutdown hook
- [ ] Ajouter la tâche Gradle dans `build.gradle.kts`
- [ ] Ajouter le service dans `docker-compose.yml` si nécessaire
- [ ] Tester avec `TopologyTestDriver`
- [ ] Documenter dans ce fichier

---

## Ressources

- [Documentation officielle Kafka Streams](https://kafka.apache.org/documentation/streams/)
- [Confluent Kafka Streams Developer Guide](https://docs.confluent.io/platform/current/streams/developer-guide/dsl-api.html)
- [Schema Registry](https://docs.confluent.io/platform/current/schema-registry/index.html)
