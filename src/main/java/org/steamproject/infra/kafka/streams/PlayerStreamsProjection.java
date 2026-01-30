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
    public static final String DLC_PURCHASES_STORE = "dlc-purchases-store";
    
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
        // Note: crash events are keyed by crashId, so we re-key by playerId
        KStream<String, Object> crashStream = builder.stream(
            "crash-report-events",
            Consumed.with(Serdes.String(), avroSerde)
        );

        // Use foreach to update GameProjection (side effect) separately from the aggregate
        crashStream.foreach((key, eventObj) -> {
            try {
                CrashReportEvent event = (CrashReportEvent) eventObj;
                String crashId = event.getCrashId() != null ? event.getCrashId().toString() : "";
                String gameId = event.getGameId() != null ? event.getGameId().toString() : "";
                String gameName = event.getGameName() != null ? event.getGameName().toString() : "";
                
                if (!gameId.isEmpty()) {
                    String errorMessage = event.getErrorMessage() != null ? event.getErrorMessage().toString() : "";
                    String severity = event.getSeverity() != null ? event.getSeverity().toString() : "UNKNOWN";
                    org.steamproject.infra.kafka.consumer.GameProjection.getInstance().incrementIncidentCount(gameId, gameName);
                    org.steamproject.infra.kafka.consumer.GameProjection.getInstance().addEditorResponse(
                        gameId, crashId, severity + ": " + errorMessage, event.getTimestamp());
                    System.out.println("Crash recorded for game " + gameId + " (" + gameName + "), severity: " + severity);
                }
            } catch (Throwable t) {
                System.err.println("Error updating GameProjection for crash: " + t.getMessage());
            }
        });

        // Store crashes by playerId for player crash history
        crashStream
            .selectKey((crashId, eventObj) -> {
                CrashReportEvent event = (CrashReportEvent) eventObj;
                return event.getPlayerId() != null ? event.getPlayerId().toString() : crashId;
            })
            .groupByKey()
            .aggregate(
                () -> "[]",
                (playerId, eventObj, aggregate) -> {
                    CrashReportEvent event = (CrashReportEvent) eventObj;
                    try {
                        ArrayNode crashes = (ArrayNode) mapper.readTree(aggregate);
                        ObjectNode crashJson = mapper.createObjectNode();
                        crashJson.put("crashId", event.getCrashId() != null ? event.getCrashId().toString() : "");
                        crashJson.put("gameId", event.getGameId() != null ? event.getGameId().toString() : "");
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
                        
                        // Check if player already owns this game (prevent duplicate purchases)
                        String newGameId = event.getGameId() != null ? event.getGameId().toString() : "";
                        for (JsonNode existing : purchases) {
                            if (existing.has("gameId") && existing.get("gameId").asText().equals(newGameId)) {
                                System.out.println("Player " + playerId + " already owns game " + newGameId + ", skipping duplicate purchase");
                                return aggregate; // Already owns this game, skip
                            }
                        }
                        
                        ObjectNode purchaseJson = mapper.createObjectNode();
                        purchaseJson.put("purchaseId", event.getPurchaseId() != null ? event.getPurchaseId().toString() : "");
                        purchaseJson.put("gameId", newGameId);
                        purchaseJson.put("gameName", event.getGameName() != null ? event.getGameName().toString() : "");
                        purchaseJson.put("playerId", event.getPlayerId() != null ? event.getPlayerId().toString() : playerId);
                        purchaseJson.put("playerUsername", event.getPlayerUsername() != null ? event.getPlayerUsername().toString() : "");
                        purchaseJson.put("pricePaid", event.getPricePaid());
                        purchaseJson.put("platform", event.getPlatform() != null ? event.getPlatform().toString() : "");
                        purchaseJson.put("publisherId", event.getPublisherId() != null ? event.getPublisherId().toString() : "");
                        purchaseJson.put("region", event.getRegion() != null ? event.getRegion().toString() : "OTHER");
                        purchaseJson.put("timestamp", event.getTimestamp());
                        purchaseJson.put("purchaseDate", Instant.ofEpochMilli(event.getTimestamp()).toString());
                        purchaseJson.put("playtime", 0); // Initial playtime, will be updated from sessions
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

        // ==================== DLC PURCHASES STORE ====================
        // Consume DlcPurchaseEvent and aggregate DLC purchases per player
        // A player can only buy a DLC if they own the parent game
        KStream<String, Object> dlcPurchaseStream = builder.stream(
            "dlc-purchase-events",
            Consumed.with(Serdes.String(), avroSerde)
        );

        dlcPurchaseStream
            .groupByKey()
            .aggregate(
                () -> "[]",
                (playerId, eventObj, aggregate) -> {
                    DlcPurchaseEvent event = (DlcPurchaseEvent) eventObj;
                    try {
                        ArrayNode dlcPurchases = (ArrayNode) mapper.readTree(aggregate);
                        
                        // Check if player already owns this DLC (prevent duplicate purchases)
                        String newDlcId = event.getDlcId() != null ? event.getDlcId().toString() : "";
                        for (JsonNode existing : dlcPurchases) {
                            if (existing.has("dlcId") && existing.get("dlcId").asText().equals(newDlcId)) {
                                System.out.println("Player " + playerId + " already owns DLC " + newDlcId + ", skipping duplicate purchase");
                                return aggregate; // Already owns this DLC, skip
                            }
                        }
                        
                        // Note: Game ownership check is done at the producer/API level
                        // Here we just store the DLC purchase
                        ObjectNode dlcJson = mapper.createObjectNode();
                        dlcJson.put("purchaseId", event.getPurchaseId() != null ? event.getPurchaseId().toString() : "");
                        dlcJson.put("dlcId", newDlcId);
                        dlcJson.put("dlcName", event.getDlcName() != null ? event.getDlcName().toString() : "");
                        dlcJson.put("gameId", event.getGameId() != null ? event.getGameId().toString() : "");
                        dlcJson.put("playerId", event.getPlayerId() != null ? event.getPlayerId().toString() : playerId);
                        dlcJson.put("playerUsername", event.getPlayerUsername() != null ? event.getPlayerUsername().toString() : "");
                        dlcJson.put("pricePaid", event.getPricePaid());
                        dlcJson.put("platform", event.getPlatform() != null ? event.getPlatform().toString() : "");
                        dlcJson.put("timestamp", event.getTimestamp());
                        dlcJson.put("purchaseDate", Instant.ofEpochMilli(event.getTimestamp()).toString());
                        dlcJson.put("isDlc", true);
                        dlcPurchases.add(dlcJson);
                        return mapper.writeValueAsString(dlcPurchases);
                    } catch (Exception e) {
                        System.err.println("Error processing DlcPurchaseEvent: " + e.getMessage());
                        return aggregate;
                    }
                },
                Materialized.<String, String, KeyValueStore<org.apache.kafka.common.utils.Bytes, byte[]>>as(DLC_PURCHASES_STORE)
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
            PLAYERS_STORE + ", " + SESSIONS_STORE + ", " + CRASHES_STORE + ", " + REVIEWS_STORE + ", " + PURCHASES_STORE + ", " + DLC_PURCHASES_STORE);
        
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
     * Get all reviews from all players (for calculating average ratings per game)
     */
    public static Map<String, List<Map<String, Object>>> getAllReviewsByPlayer() {
        if (streamsInstance == null) {
            return Collections.emptyMap();
        }
        
        try {
            ReadOnlyKeyValueStore<String, String> store = streamsInstance.store(
                org.apache.kafka.streams.StoreQueryParameters.fromNameAndType(
                    REVIEWS_STORE,
                    QueryableStoreTypes.keyValueStore()
                )
            );
            
            Map<String, List<Map<String, Object>>> result = new HashMap<>();
            try (var iterator = store.all()) {
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    String playerId = entry.key;
                    String json = entry.value;
                    if (json != null && !json.equals("[]")) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> reviews = mapper.readValue(json, List.class);
                        result.put(playerId, reviews);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            System.err.println("Error retrieving all reviews: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Get all reviews for a specific game (aggregated from all players)
     */
    public static List<Map<String, Object>> getReviewsForGame(String gameId) {
        if (gameId == null) return Collections.emptyList();
        
        Map<String, List<Map<String, Object>>> allReviews = getAllReviewsByPlayer();
        List<Map<String, Object>> gameReviews = new ArrayList<>();
        
        for (var entry : allReviews.entrySet()) {
            String playerId = entry.getKey();
            for (Map<String, Object> review : entry.getValue()) {
                if (gameId.equals(review.get("gameId"))) {
                    Map<String, Object> enrichedReview = new HashMap<>(review);
                    enrichedReview.put("playerId", playerId);
                    gameReviews.add(enrichedReview);
                }
            }
        }
        return gameReviews;
    }

    /**
     * Calculate average rating for a specific game
     */
    public static Double getAverageRatingForGame(String gameId) {
        List<Map<String, Object>> reviews = getReviewsForGame(gameId);
        if (reviews.isEmpty()) return null;
        
        double sum = 0;
        int count = 0;
        for (Map<String, Object> review : reviews) {
            Object rating = review.get("rating");
            if (rating instanceof Number) {
                sum += ((Number) rating).doubleValue();
                count++;
            }
        }
        return count > 0 ? sum / count : null;
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
     * Get the player's library (owned games) from the purchases store.
     * Enriches library entries with playtime from sessions.
     */
    public static List<Map<String, Object>> getLibrary(String playerId) {
        List<Map<String, Object>> library = getPurchases(playerId);
        List<Map<String, Object>> sessions = getSessions(playerId);
        List<Map<String, Object>> dlcPurchases = getDlcPurchases(playerId);
        
        // Calculate playtime per game from sessions
        Map<String, Long> playtimeByGame = new HashMap<>();
        for (Map<String, Object> session : sessions) {
            String gameId = (String) session.get("gameId");
            if (gameId != null) {
                Object durationObj = session.get("duration");
                long duration = 0;
                if (durationObj instanceof Number) {
                    duration = ((Number) durationObj).longValue();
                }
                playtimeByGame.merge(gameId, duration, Long::sum);
            }
        }
        
        // Enrich library entries with playtime (convert minutes to hours)
        List<Map<String, Object>> enrichedLibrary = new ArrayList<>();
        for (Map<String, Object> entry : library) {
            Map<String, Object> enriched = new HashMap<>(entry);
            String gameId = (String) entry.get("gameId");
            if (gameId != null && playtimeByGame.containsKey(gameId)) {
                long totalMinutes = playtimeByGame.get(gameId);
                int hours = (int) (totalMinutes / 60);
                enriched.put("playtime", hours);
            } else {
                // Toujours inclure le playtime, même à 0, pour l'affichage dans la bibliothèque
                enriched.put("playtime", 0);
            }
            enrichedLibrary.add(enriched);
        }
        
        // Add DLC purchases to the library
        for (Map<String, Object> dlc : dlcPurchases) {
            Map<String, Object> dlcEntry = new HashMap<>(dlc);
            dlcEntry.put("isDlc", true);
            // Use dlcName as the display name and as `gameName` for compatibility with UI mapping
            if (dlcEntry.get("dlcName") != null && !dlcEntry.get("dlcName").toString().isEmpty()) {
                dlcEntry.put("gameName", dlcEntry.get("dlcName"));
                dlcEntry.put("title", dlcEntry.get("dlcName"));
            }
            enrichedLibrary.add(dlcEntry);
        }
        
        return enrichedLibrary;
    }

    /**
     * Get DLC purchases for a specific player
     */
    public static List<Map<String, Object>> getDlcPurchases(String playerId) {
        if (streamsInstance == null) {
            return Collections.emptyList();
        }
        
        try {
            ReadOnlyKeyValueStore<String, String> store = streamsInstance.store(
                org.apache.kafka.streams.StoreQueryParameters.fromNameAndType(
                    DLC_PURCHASES_STORE,
                    QueryableStoreTypes.keyValueStore()
                )
            );
            
            String json = store.get(playerId);
            if (json != null && !json.equals("[]")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> dlcPurchases = mapper.readValue(json, List.class);
                return dlcPurchases;
            }
            return Collections.emptyList();
        } catch (Exception e) {
            System.err.println("Error retrieving DLC purchases for player " + playerId + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get all DLC purchases from all players
     */
    public static List<Map<String, Object>> getAllDlcPurchases() {
        if (streamsInstance == null) {
            return Collections.emptyList();
        }
        
        try {
            ReadOnlyKeyValueStore<String, String> store = streamsInstance.store(
                org.apache.kafka.streams.StoreQueryParameters.fromNameAndType(
                    DLC_PURCHASES_STORE,
                    QueryableStoreTypes.keyValueStore()
                )
            );
            
            List<Map<String, Object>> allDlcPurchases = new ArrayList<>();
            try (var iterator = store.all()) {
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    String json = entry.value;
                    if (json != null && !json.equals("[]")) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> dlcPurchases = mapper.readValue(json, List.class);
                        allDlcPurchases.addAll(dlcPurchases);
                    }
                }
            }
            // Sort by timestamp descending
            allDlcPurchases.sort((a, b) -> {
                Long tsA = a.get("timestamp") instanceof Number ? ((Number) a.get("timestamp")).longValue() : 0L;
                Long tsB = b.get("timestamp") instanceof Number ? ((Number) b.get("timestamp")).longValue() : 0L;
                return tsB.compareTo(tsA);
            });
            return allDlcPurchases;
        } catch (Exception e) {
            System.err.println("Error retrieving all DLC purchases: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Check if a player owns a specific game
     */
    public static boolean playerOwnsGame(String playerId, String gameId) {
        List<Map<String, Object>> library = getPurchases(playerId);
        for (Map<String, Object> entry : library) {
            if (gameId.equals(entry.get("gameId"))) {
                return true;
            }
        }
        return false;
    }
}
