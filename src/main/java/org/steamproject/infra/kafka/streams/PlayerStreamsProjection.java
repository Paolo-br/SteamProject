package org.steamproject.infra.kafka.streams;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.steamproject.events.*;
import java.util.Arrays;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Kafka Streams application for Player projections.
 * Consumes multiple player-related topics and maintains a materialized view of player data.
 * Exposes data via interactive queries to ProjectionDataService.
 */
public class PlayerStreamsProjection {
    public static final String PLAYERS_STORE = "players-store";
    public static final String SESSIONS_STORE = "sessions-store";
    public static final String CRASHES_STORE = "crashes-store";
    public static final String REVIEWS_STORE = "reviews-store";
    public static final String PURCHASES_STORE = "purchases-store";
    
    private static volatile KafkaStreams streamsInstance;
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        startStreams();
    }

    public static KafkaStreams startStreams() {
        if (streamsInstance != null) {
            return streamsInstance;
        }

        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        String schema = System.getenv().getOrDefault("SCHEMA_REGISTRY_URL", "http://localhost:8081");

        Properties props = new Properties();
        String autoReset = System.getProperty("kafka.auto.offset.reset", "latest");
        String configuredAppId = System.getProperty("kafka.streams.application.id", "");
        String appId;
        if (configuredAppId != null && !configuredAppId.isBlank()) {
            appId = configuredAppId;
        } else if ("latest".equalsIgnoreCase(autoReset)) {
            appId = "player-streams-projection-" + System.currentTimeMillis();
        } else {
            appId = "player-streams-projection";
        }
        System.out.println("Using Kafka Streams application.id=" + appId + " (auto.offset.reset=" + autoReset + ")");
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, appId);
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schema);

        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, System.getProperty("kafka.auto.offset.reset", "latest"));

        StreamsBuilder builder = new StreamsBuilder();

        // Create Avro Serde
        Map<String, Object> serdeConfig = new HashMap<>();
        serdeConfig.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schema);
        serdeConfig.put("specific.avro.reader", true);
        
        KafkaAvroSerializer avroSerializer = new KafkaAvroSerializer();
        KafkaAvroDeserializer avroDeserializer = new KafkaAvroDeserializer();
        avroSerializer.configure(serdeConfig, false);
        avroDeserializer.configure(serdeConfig, false);
        
        @SuppressWarnings({"unchecked", "rawtypes"})
        final Serde<Object> avroSerde = Serdes.serdeFrom(
            (org.apache.kafka.common.serialization.Serializer) avroSerializer,
            (org.apache.kafka.common.serialization.Deserializer) avroDeserializer
        );

        // ==================== PLAYERS STORE ====================
        // Consume PlayerCreatedEvent and build a KTable of player data
        // Using mapValues + toTable instead of aggregate for better semantics
        KStream<String, Object> playerCreatedStream = builder.stream(
            "player-created-events",
            Consumed.with(Serdes.String(), avroSerde)
        );

        // Transform each PlayerCreatedEvent to JSON and materialize as KTable
        playerCreatedStream
            .mapValues(eventObj -> {
                PlayerCreatedEvent event = (PlayerCreatedEvent) eventObj;
                try {
                    ObjectNode playerJson = mapper.createObjectNode();
                    String playerId = event.getId().toString();
                    playerJson.put("id", playerId);
                    playerJson.put("playerId", playerId);  // Add playerId for compatibility
                    playerJson.put("username", event.getUsername() != null ? event.getUsername().toString() : "");
                    playerJson.put("email", event.getEmail() != null ? event.getEmail().toString() : "");
                    playerJson.put("registrationDate", event.getRegistrationDate() != null ? 
                        event.getRegistrationDate().toString() : "");
                    playerJson.put("firstName", event.getFirstName() != null ? event.getFirstName().toString() : "");
                    playerJson.put("lastName", event.getLastName() != null ? event.getLastName().toString() : "");
                    playerJson.put("dateOfBirth", event.getDateOfBirth() != null ? event.getDateOfBirth().toString() : "");
                    playerJson.put("timestamp", event.getTimestamp());
                    playerJson.put("gdprConsent", event.getGdprConsent());
                    playerJson.put("gdprConsentDate", event.getGdprConsentDate() != null ? 
                        event.getGdprConsentDate().toString() : "");
                    return mapper.writeValueAsString(playerJson);
                } catch (Exception e) {
                    System.err.println("Error processing PlayerCreatedEvent: " + e.getMessage());
                    return "{}";
                }
            })
            .groupByKey()
            .reduce(
                (oldValue, newValue) -> newValue,  // Keep the latest value for each key
                Materialized.<String, String, KeyValueStore<org.apache.kafka.common.utils.Bytes, byte[]>>as(PLAYERS_STORE)
                    .withKeySerde(Serdes.String())
                    .withValueSerde(Serdes.String())
            );

        // ==================== SESSIONS STORE ====================
        // Consume GameSessionEvent and aggregate sessions per player
        KStream<String, Object> sessionStream = builder.stream(
            "game-session-events",
            Consumed.with(Serdes.String(), avroSerde)
        );

        sessionStream
            .groupByKey()
            .aggregate(
                () -> "[]",
                (playerId, eventObj, aggregate) -> {
                    GameSessionEvent event = (GameSessionEvent) eventObj;
                    try {
                        ArrayNode sessions = (ArrayNode) mapper.readTree(aggregate);
                        ObjectNode sessionJson = mapper.createObjectNode();
                        sessionJson.put("sessionId", event.getSessionId().toString());
                        sessionJson.put("gameId", event.getGameId().toString());
                        sessionJson.put("gameName", event.getGameName() != null ? event.getGameName().toString() : "");
                        sessionJson.put("duration", event.getSessionDuration());
                        sessionJson.put("sessionType", event.getSessionType() != null ? 
                            event.getSessionType().toString() : "NORMAL");
                        sessionJson.put("timestamp", event.getTimestamp());
                        sessions.add(sessionJson);
                        return mapper.writeValueAsString(sessions);
                    } catch (Exception e) {
                        System.err.println("Error processing GameSessionEvent: " + e.getMessage());
                        return aggregate;
                    }
                },
                Materialized.<String, String, KeyValueStore<org.apache.kafka.common.utils.Bytes, byte[]>>as(SESSIONS_STORE)
                    .withKeySerde(Serdes.String())
                    .withValueSerde(Serdes.String())
            );

        // ==================== CRASHES STORE ====================
        // Consume CrashReportEvent and aggregate crashes per player
        KStream<String, Object> crashStream = builder.stream(
            "crash-report-events",
            Consumed.with(Serdes.String(), avroSerde)
        );

        crashStream
            .groupByKey()
            .aggregate(
                () -> "[]",
                (playerId, eventObj, aggregate) -> {
                    CrashReportEvent event = (CrashReportEvent) eventObj;
                    try {
                        ArrayNode crashes = (ArrayNode) mapper.readTree(aggregate);
                        ObjectNode crashJson = mapper.createObjectNode();
                        crashJson.put("crashId", event.getCrashId().toString());
                        crashJson.put("gameId", event.getGameId().toString());
                        crashJson.put("gameName", event.getGameName() != null ? event.getGameName().toString() : "");
                        crashJson.put("platform", event.getPlatform() != null ? event.getPlatform().toString() : "");
                        crashJson.put("severity", event.getSeverity() != null ? event.getSeverity().toString() : "");
                        crashJson.put("errorType", event.getErrorType() != null ? event.getErrorType().toString() : "");
                        crashJson.put("errorMessage", event.getErrorMessage() != null ? 
                            event.getErrorMessage().toString() : "");
                        crashJson.put("timestamp", event.getTimestamp());
                        crashes.add(crashJson);
                        return mapper.writeValueAsString(crashes);
                    } catch (Exception e) {
                        System.err.println("Error processing CrashReportEvent: " + e.getMessage());
                        return aggregate;
                    }
                },
                Materialized.<String, String, KeyValueStore<org.apache.kafka.common.utils.Bytes, byte[]>>as(CRASHES_STORE)
                    .withKeySerde(Serdes.String())
                    .withValueSerde(Serdes.String())
            );

        // ==================== REVIEWS STORE ====================
        // Consume NewRatingEvent and ReviewPublishedEvent
        KStream<String, Object> ratingStream = builder.stream(
            Arrays.asList("new-rating-events", "review-published-events"),
            Consumed.with(Serdes.String(), avroSerde)
        );

        ratingStream
            .groupByKey()
            .aggregate(
                () -> "[]",
                (playerId, eventObj, aggregate) -> {
                    try {
                        ArrayNode reviews = (ArrayNode) mapper.readTree(aggregate);
                        ObjectNode reviewJson = mapper.createObjectNode();
                        
                        if (eventObj instanceof NewRatingEvent) {
                            NewRatingEvent event = (NewRatingEvent) eventObj;
                            // NewRatingEvent n'a pas de reviewId, utiliser gameId + playerId
                            String reviewId = event.getGameId().toString() + "-" + event.getPlayerId().toString();
                            reviewJson.put("reviewId", reviewId);
                            reviewJson.put("gameId", event.getGameId().toString());
                            reviewJson.put("rating", event.getRating());
                            reviewJson.put("title", event.getComment() != null ? event.getComment().toString() : "");
                            reviewJson.put("text", "");
                            reviewJson.put("isSpoiler", false);
                            reviewJson.put("timestamp", event.getTimestamp());
                        } else if (eventObj instanceof ReviewPublishedEvent) {
                            ReviewPublishedEvent event = (ReviewPublishedEvent) eventObj;
                            reviewJson.put("reviewId", event.getReviewId().toString());
                            reviewJson.put("gameId", event.getGameId().toString());
                            reviewJson.put("rating", event.getRating());
                            reviewJson.put("title", event.getTitle() != null ? event.getTitle().toString() : "");
                            reviewJson.put("text", event.getText() != null ? event.getText().toString() : "");
                            reviewJson.put("isSpoiler", event.getIsSpoiler());
                            reviewJson.put("timestamp", event.getTimestamp());
                        }
                        
                        // Check if already rated this game
                        String gameId = reviewJson.get("gameId").asText();
                        boolean alreadyRated = false;
                        for (JsonNode existingReview : reviews) {
                            if (existingReview.has("gameId") && 
                                existingReview.get("gameId").asText().equals(gameId)) {
                                alreadyRated = true;
                                break;
                            }
                        }
                        
                        if (!alreadyRated) {
                            reviews.add(reviewJson);
                        }
                        
                        return mapper.writeValueAsString(reviews);
                    } catch (Exception e) {
                        System.err.println("Error processing rating/review event: " + e.getMessage());
                        return aggregate;
                    }
                },
                Materialized.<String, String, KeyValueStore<org.apache.kafka.common.utils.Bytes, byte[]>>as(REVIEWS_STORE)
                    .withKeySerde(Serdes.String())
                    .withValueSerde(Serdes.String())
            );

        // ==================== PURCHASES STORE ====================
        // Consume GamePurchaseEvent and aggregate purchases per player
        KStream<String, Object> purchaseStream = builder.stream(
            "game-purchase-events",
            Consumed.with(Serdes.String(), avroSerde)
        );

        purchaseStream
            .groupByKey()
            .aggregate(
                () -> "[]",
                (playerId, eventObj, aggregate) -> {
                    GamePurchaseEvent event = (GamePurchaseEvent) eventObj;
                    try {
                        ArrayNode purchases = (ArrayNode) mapper.readTree(aggregate);
                        ObjectNode purchaseJson = mapper.createObjectNode();
                        purchaseJson.put("purchaseId", event.getPurchaseId() != null ? event.getPurchaseId().toString() : "");
                        purchaseJson.put("gameId", event.getGameId() != null ? event.getGameId().toString() : "");
                        purchaseJson.put("gameName", event.getGameName() != null ? event.getGameName().toString() : "");
                        purchaseJson.put("playerId", event.getPlayerId() != null ? event.getPlayerId().toString() : playerId);
                        purchaseJson.put("playerUsername", event.getPlayerUsername() != null ? event.getPlayerUsername().toString() : "");
                        purchaseJson.put("pricePaid", event.getPricePaid());
                        purchaseJson.put("platform", event.getPlatform() != null ? event.getPlatform().toString() : "");
                        purchaseJson.put("publisherId", event.getPublisherId() != null ? event.getPublisherId().toString() : "");
                        purchaseJson.put("region", event.getRegion() != null ? event.getRegion().toString() : "OTHER");
                        purchaseJson.put("timestamp", event.getTimestamp());
                        purchaseJson.put("purchaseDate", Instant.ofEpochMilli(event.getTimestamp()).toString());
                        purchases.add(purchaseJson);
                        return mapper.writeValueAsString(purchases);
                    } catch (Exception e) {
                        System.err.println("Error processing GamePurchaseEvent: " + e.getMessage());
                        return aggregate;
                    }
                },
                Materialized.<String, String, KeyValueStore<org.apache.kafka.common.utils.Bytes, byte[]>>as(PURCHASES_STORE)
                    .withKeySerde(Serdes.String())
                    .withValueSerde(Serdes.String())
            );

        streamsInstance = new KafkaStreams(builder.build(), props);
        streamsInstance.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                streamsInstance.close();
            } catch (Exception ignored) {
            }
        }));

        System.out.println("PlayerStreamsProjection started with stores: " + 
            PLAYERS_STORE + ", " + SESSIONS_STORE + ", " + CRASHES_STORE + ", " + REVIEWS_STORE + ", " + PURCHASES_STORE);
        
        return streamsInstance;
    }

    public static KafkaStreams getStreams() {
        return streamsInstance;
    }

    /**
     * Get all players from the store
     */
    public static List<Map<String, Object>> getAllPlayers() {
        if (streamsInstance == null) {
            System.err.println("WARNING: streamsInstance is null!");
            return Collections.emptyList();
        }
        
        try {
            ReadOnlyKeyValueStore<String, String> store = streamsInstance.store(
                org.apache.kafka.streams.StoreQueryParameters.fromNameAndType(
                    PLAYERS_STORE,
                    QueryableStoreTypes.keyValueStore()
                )
            );
            
            List<Map<String, Object>> players = new ArrayList<>();
            try (var iterator = store.all()) {
                int count = 0;
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    count++;
                    System.out.println("DEBUG: Found player with key: " + entry.key);
                    String json = entry.value;
                    if (json != null && !json.equals("{}")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> playerMap = mapper.readValue(json, Map.class);
                        players.add(playerMap);
                    }
                }
                System.out.println("DEBUG: Total players in store: " + count + ", returned: " + players.size());
            }
            return players;
        } catch (Exception e) {
            System.err.println("Error retrieving players: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     * Get a specific player by ID
     */
    public static Map<String, Object> getPlayer(String playerId) {
        if (streamsInstance == null || playerId == null) {
            return null;
        }
        
        try {
            ReadOnlyKeyValueStore<String, String> store = streamsInstance.store(
                org.apache.kafka.streams.StoreQueryParameters.fromNameAndType(
                    PLAYERS_STORE,
                    QueryableStoreTypes.keyValueStore()
                )
            );
            
            String json = store.get(playerId);
            if (json != null && !json.equals("{}")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> playerMap = mapper.readValue(json, Map.class);
                return playerMap;
            }
            return null;
        } catch (Exception e) {
            System.err.println("Error retrieving player " + playerId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Get sessions for a specific player
     */
    public static List<Map<String, Object>> getSessions(String playerId) {
        if (streamsInstance == null) {
            return Collections.emptyList();
        }
        
        try {
            ReadOnlyKeyValueStore<String, String> store = streamsInstance.store(
                org.apache.kafka.streams.StoreQueryParameters.fromNameAndType(
                    SESSIONS_STORE,
                    QueryableStoreTypes.keyValueStore()
                )
            );
            
            String json = store.get(playerId);
            if (json != null && !json.equals("[]")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> sessions = mapper.readValue(json, List.class);
                return sessions;
            }
            return Collections.emptyList();
        } catch (Exception e) {
            System.err.println("Error retrieving sessions: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get crashes for a specific player
     */
    public static List<Map<String, Object>> getCrashes(String playerId) {
        if (streamsInstance == null) {
            return Collections.emptyList();
        }
        
        try {
            ReadOnlyKeyValueStore<String, String> store = streamsInstance.store(
                org.apache.kafka.streams.StoreQueryParameters.fromNameAndType(
                    CRASHES_STORE,
                    QueryableStoreTypes.keyValueStore()
                )
            );
            
            String json = store.get(playerId);
            if (json != null && !json.equals("[]")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> crashes = mapper.readValue(json, List.class);
                return crashes;
            }
            return Collections.emptyList();
        } catch (Exception e) {
            System.err.println("Error retrieving crashes: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get reviews for a specific player
     */
    public static List<Map<String, Object>> getReviews(String playerId) {
        if (streamsInstance == null) {
            return Collections.emptyList();
        }
        
        try {
            ReadOnlyKeyValueStore<String, String> store = streamsInstance.store(
                org.apache.kafka.streams.StoreQueryParameters.fromNameAndType(
                    REVIEWS_STORE,
                    QueryableStoreTypes.keyValueStore()
                )
            );
            
            String json = store.get(playerId);
            if (json != null && !json.equals("[]")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> reviews = mapper.readValue(json, List.class);
                return reviews;
            }
            return Collections.emptyList();
        } catch (Exception e) {
            System.err.println("Error retrieving reviews: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get purchases for a specific player
     */
    public static List<Map<String, Object>> getPurchases(String playerId) {
        if (streamsInstance == null) {
            return Collections.emptyList();
        }
        
        try {
            ReadOnlyKeyValueStore<String, String> store = streamsInstance.store(
                org.apache.kafka.streams.StoreQueryParameters.fromNameAndType(
                    PURCHASES_STORE,
                    QueryableStoreTypes.keyValueStore()
                )
            );
            
            String json = store.get(playerId);
            if (json != null && !json.equals("[]")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> purchases = mapper.readValue(json, List.class);
                return purchases;
            }
            return Collections.emptyList();
        } catch (Exception e) {
            System.err.println("Error retrieving purchases for player " + playerId + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get all purchases from all players
     */
    public static List<Map<String, Object>> getAllPurchases() {
        if (streamsInstance == null) {
            System.err.println("WARNING: streamsInstance is null for getAllPurchases!");
            return Collections.emptyList();
        }
        
        try {
            ReadOnlyKeyValueStore<String, String> store = streamsInstance.store(
                org.apache.kafka.streams.StoreQueryParameters.fromNameAndType(
                    PURCHASES_STORE,
                    QueryableStoreTypes.keyValueStore()
                )
            );
            
            List<Map<String, Object>> allPurchases = new ArrayList<>();
            try (var iterator = store.all()) {
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    String json = entry.value;
                    if (json != null && !json.equals("[]")) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> purchases = mapper.readValue(json, List.class);
                        allPurchases.addAll(purchases);
                    }
                }
            }
            // Sort by timestamp descending (most recent first)
            allPurchases.sort((a, b) -> {
                Long tsA = a.get("timestamp") instanceof Number ? ((Number) a.get("timestamp")).longValue() : 0L;
                Long tsB = b.get("timestamp") instanceof Number ? ((Number) b.get("timestamp")).longValue() : 0L;
                return tsB.compareTo(tsA);
            });
            return allPurchases;
        } catch (Exception e) {
            System.err.println("Error retrieving all purchases: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     * Get the player's library (owned games) from the purchases store
     */
    public static List<Map<String, Object>> getLibrary(String playerId) {
        return getPurchases(playerId);
    }
}
