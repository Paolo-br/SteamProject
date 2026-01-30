package org.steamproject.infra.kafka.streams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.common.serialization.Serde;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kafka Streams pour calculer les statistiques avancées des éditeurs :
 * - Note de réactivité : temps moyen entre un incident et la publication d'un patch correctif
 * - Note de qualité : combinaison du nombre d'incidents et de la note moyenne des jeux
 * 
 * Stores:
 * - publisher-stats-store : statistiques complètes par publisherId (JSON)
 */
public class PublisherStatsStreams {
    public static final String STORE_NAME = "publisher-stats-store";
    private static volatile KafkaStreams streamsInstance;
    private static final ObjectMapper om = new ObjectMapper();
    
    // Cache en mémoire pour les incidents non résolus (gameId -> timestamp du dernier incident)
    private static final Map<String, Long> pendingIncidents = new ConcurrentHashMap<>();
    // Cache pour associer gameId -> publisherId
    private static final Map<String, String> gameToPublisher = new ConcurrentHashMap<>();
    // Cache pour les notes par jeu
    private static final Map<String, List<Integer>> gameRatings = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        startStreams();
        // Keep running
        try { Thread.currentThread().join(); } catch (InterruptedException e) { /* shutdown */ }
    }

    public static KafkaStreams startStreams() {
        if (streamsInstance != null) return streamsInstance;
        
        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        String schema = System.getenv().getOrDefault("SCHEMA_REGISTRY_URL", "http://localhost:8081");

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "publisher-stats-streams");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schema);
        // Pour que le store soit queryable rapidement
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1000);

        StreamsBuilder builder = new StreamsBuilder();

        // Avro Serde
        KafkaAvroSerializer avroSerializer = new KafkaAvroSerializer();
        KafkaAvroDeserializer avroDeserializer = new KafkaAvroDeserializer();
        Map<String, Object> serdeConfig = new HashMap<>();
        serdeConfig.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schema);
        serdeConfig.put("specific.avro.reader", true);
        avroSerializer.configure(serdeConfig, false);
        avroDeserializer.configure(serdeConfig, false);
        @SuppressWarnings({"unchecked", "rawtypes"})
        final Serde<Object> avroSerde = Serdes.serdeFrom(
            (org.apache.kafka.common.serialization.Serializer) avroSerializer,
            (org.apache.kafka.common.serialization.Deserializer) avroDeserializer
        );

        // ========== STREAM 1: Jeux publiés (pour mapper gameId -> publisherId) ==========
        KStream<String, Object> gameReleasedStream = builder.stream(
            "game-released-events", 
            Consumed.with(Serdes.String(), avroSerde)
        );
        
        gameReleasedStream
            .filter((k, v) -> v instanceof GameReleasedEvent)
            .foreach((key, value) -> {
                GameReleasedEvent evt = (GameReleasedEvent) value;
                String gameId = evt.getGameId().toString();
                String publisherId = evt.getPublisherId().toString();
                gameToPublisher.put(gameId, publisherId);
            });

        // ========== STREAM 2: Incidents/Crashs ==========
        KStream<String, Object> crashStream = builder.stream(
            "crash-report-events",
            Consumed.with(Serdes.String(), avroSerde)
        );
        
        // Extrait les incidents par éditeur avec timestamp
        KStream<String, String> incidentsByPublisher = crashStream
            .filter((k, v) -> v instanceof CrashReportEvent)
            .map((key, value) -> {
                CrashReportEvent evt = (CrashReportEvent) value;
                String publisherId = evt.getEditeurId().toString();
                String gameId = evt.getGameId().toString();
                long timestamp = evt.getTimestamp();
                
                // Stocker l'incident en attente de résolution
                pendingIncidents.put(gameId, timestamp);
                gameToPublisher.put(gameId, publisherId);
                
                // Format: "incident|gameId|timestamp|severity"
                String data = "incident|" + gameId + "|" + timestamp + "|" + evt.getSeverity().name();
                return new org.apache.kafka.streams.KeyValue<>(publisherId, data);
            });

        // ========== STREAM 3: Patches publiés ==========
        KStream<String, Object> patchStream = builder.stream(
            "patch-published-events",
            Consumed.with(Serdes.String(), avroSerde)
        );
        
        // Calcule le temps de réaction pour chaque patch
        KStream<String, String> patchesByPublisher = patchStream
            .filter((k, v) -> v instanceof PatchPublishedEvent)
            .map((key, value) -> {
                PatchPublishedEvent evt = (PatchPublishedEvent) value;
                String gameId = evt.getGameId().toString();
                long patchTimestamp = evt.getTimestamp();
                
                // Récupérer le publisherId depuis le cache
                String publisherId = gameToPublisher.get(gameId);
                if (publisherId == null) publisherId = "unknown";
                
                // Calculer le temps de réaction si un incident était en attente
                Long incidentTimestamp = pendingIncidents.remove(gameId);
                long reactionTime = -1;
                if (incidentTimestamp != null) {
                    reactionTime = patchTimestamp - incidentTimestamp;
                }
                
                // Format: "patch|gameId|timestamp|reactionTimeMs"
                String data = "patch|" + gameId + "|" + patchTimestamp + "|" + reactionTime;
                return new org.apache.kafka.streams.KeyValue<>(publisherId, data);
            });

        // ========== STREAM 4: Notes/Ratings ==========
        KStream<String, Object> ratingStream = builder.stream(
            "new-rating-events",
            Consumed.with(Serdes.String(), avroSerde)
        );
        
        KStream<String, String> ratingsByPublisher = ratingStream
            .filter((k, v) -> v instanceof NewRatingEvent)
            .map((key, value) -> {
                NewRatingEvent evt = (NewRatingEvent) value;
                String gameId = evt.getGameId().toString();
                int rating = evt.getRating();
                
                // Stocker la note
                gameRatings.computeIfAbsent(gameId, k -> new ArrayList<>()).add(rating);
                
                // Récupérer le publisherId
                String publisherId = gameToPublisher.get(gameId);
                if (publisherId == null) publisherId = "unknown";
                
                // Format: "rating|gameId|rating"
                String data = "rating|" + gameId + "|" + rating;
                return new org.apache.kafka.streams.KeyValue<>(publisherId, data);
            });

        // ========== AGRÉGATION : Fusionner tous les événements par éditeur ==========
        KStream<String, String> allPublisherEvents = incidentsByPublisher
            .merge(patchesByPublisher)
            .merge(ratingsByPublisher);

        allPublisherEvents
            .groupByKey()
            .aggregate(
                // Initializer: JSON avec stats vides
                () -> {
                    try {
                        ObjectNode node = om.createObjectNode();
                        node.put("publisherId", "");
                        node.put("totalIncidents", 0);
                        node.put("totalPatches", 0);
                        node.put("totalRatings", 0);
                        node.put("sumRatings", 0);
                        node.put("sumReactionTimeMs", 0L);
                        node.put("countReactionTimes", 0);
                        node.put("averageRating", 0.0);
                        node.put("averageReactionTimeMs", 0L);
                        node.put("reactivityScore", 0);
                        node.put("qualityScore", 0.0);
                        return om.writeValueAsString(node);
                    } catch (Exception e) {
                        return "{}";
                    }
                },
                // Aggregator: mettre à jour les stats
                (publisherId, eventData, aggregate) -> {
                    try {
                        ObjectNode stats = (ObjectNode) om.readTree(aggregate);
                        stats.put("publisherId", publisherId);
                        
                        String[] parts = eventData.split("\\|");
                        String eventType = parts[0];
                        
                        switch (eventType) {
                            case "incident":
                                int incidents = stats.get("totalIncidents").asInt() + 1;
                                stats.put("totalIncidents", incidents);
                                break;
                                
                            case "patch":
                                int patches = stats.get("totalPatches").asInt() + 1;
                                stats.put("totalPatches", patches);
                                
                                // Ajouter le temps de réaction si disponible
                                if (parts.length >= 4) {
                                    long reactionTime = Long.parseLong(parts[3]);
                                    if (reactionTime >= 0) {
                                        long sumReaction = stats.get("sumReactionTimeMs").asLong() + reactionTime;
                                        int countReaction = stats.get("countReactionTimes").asInt() + 1;
                                        stats.put("sumReactionTimeMs", sumReaction);
                                        stats.put("countReactionTimes", countReaction);
                                        
                                        // Calculer moyenne de réaction
                                        long avgReaction = countReaction > 0 ? sumReaction / countReaction : 0;
                                        stats.put("averageReactionTimeMs", avgReaction);
                                        
                                        // Score de réactivité (100 = instantané, 0 = > 1 heure)
                                        // Plus le temps est court, meilleur est le score
                                        int reactivityScore = calculateReactivityScore(avgReaction);
                                        stats.put("reactivityScore", reactivityScore);
                                    }
                                }
                                break;
                                
                            case "rating":
                                if (parts.length >= 3) {
                                    int rating = Integer.parseInt(parts[2]);
                                    int totalRatings = stats.get("totalRatings").asInt() + 1;
                                    int sumRatings = stats.get("sumRatings").asInt() + rating;
                                    stats.put("totalRatings", totalRatings);
                                    stats.put("sumRatings", sumRatings);
                                    
                                    // Calculer la moyenne
                                    double avgRating = totalRatings > 0 ? (double) sumRatings / totalRatings : 0;
                                    stats.put("averageRating", Math.round(avgRating * 100.0) / 100.0);
                                }
                                break;
                        }
                        
                        // Recalculer le score de qualité
                        double qualityScore = calculateQualityScore(
                            stats.get("totalIncidents").asInt(),
                            stats.get("averageRating").asDouble(),
                            stats.get("totalRatings").asInt()
                        );
                        stats.put("qualityScore", Math.round(qualityScore * 100.0) / 100.0);
                        
                        return om.writeValueAsString(stats);
                    } catch (Exception e) {
                        return aggregate;
                    }
                },
                Materialized.<String, String, KeyValueStore<org.apache.kafka.common.utils.Bytes, byte[]>>as(STORE_NAME)
                    .withKeySerde(Serdes.String())
                    .withValueSerde(Serdes.String())
            );

        streamsInstance = new KafkaStreams(builder.build(), props);
        streamsInstance.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { streamsInstance.close(); } catch (Exception ignored) {}
        }));
        
        System.out.println("PublisherStatsStreams started with store: " + STORE_NAME);
        return streamsInstance;
    }

    /**
     * Calcule le score de réactivité (0-100) basé sur le temps de réaction moyen.
     * - 0-5 min = 100
     * - 5-15 min = 80-99
     * - 15-30 min = 60-79
     * - 30-60 min = 40-59
     * - > 60 min = 0-39
     */
    private static int calculateReactivityScore(long avgReactionMs) {
        long minutes = avgReactionMs / 60000;
        
        if (minutes <= 5) return 100;
        if (minutes <= 15) return 80 + (int)((15 - minutes) * 2);
        if (minutes <= 30) return 60 + (int)((30 - minutes) * 1.3);
        if (minutes <= 60) return 40 + (int)((60 - minutes) * 0.66);
        
        // Plus d'une heure : score décroissant jusqu'à 0
        return Math.max(0, 40 - (int)((minutes - 60) / 10));
    }

    /**
     * Calcule le score de qualité (0-100) basé sur :
     * - La note moyenne des jeux (poids 60%)
     * - L'inverse du ratio d'incidents (poids 40%)
     */
    private static double calculateQualityScore(int totalIncidents, double avgRating, int totalRatings) {
        // Score basé sur les notes (0-5 -> 0-60 points)
        double ratingScore = totalRatings > 0 ? (avgRating / 5.0) * 60 : 30; // 30 par défaut si pas de notes
        
        // Score basé sur les incidents (moins = mieux)
        // 0 incidents = 40 points, 10+ incidents = 0 points
        double incidentScore = Math.max(0, 40 - (totalIncidents * 4));
        
        return ratingScore + incidentScore;
    }

    public static KafkaStreams getStreams() {
        return streamsInstance;
    }

    /**
     * Récupère les statistiques d'un éditeur depuis le state store.
     */
    public static Map<String, Object> getPublisherStats(String publisherId) {
        if (streamsInstance == null) return null;
        try {
            ReadOnlyKeyValueStore<String, String> store = streamsInstance.store(
                org.apache.kafka.streams.StoreQueryParameters.fromNameAndType(STORE_NAME, QueryableStoreTypes.keyValueStore())
            );
            String json = store.get(publisherId);
            if (json != null) {
                return om.readValue(json, Map.class);
            }
        } catch (Exception e) {
            System.err.println("Error getting publisher stats: " + e.getMessage());
        }
        return null;
    }

    /**
     * Récupère les statistiques de tous les éditeurs.
     */
    public static Map<String, Map<String, Object>> getAllPublisherStats() {
        Map<String, Map<String, Object>> result = new HashMap<>();
        if (streamsInstance == null) return result;
        
        try {
            ReadOnlyKeyValueStore<String, String> store = streamsInstance.store(
                org.apache.kafka.streams.StoreQueryParameters.fromNameAndType(STORE_NAME, QueryableStoreTypes.keyValueStore())
            );
            
            var iter = store.all();
            while (iter.hasNext()) {
                var entry = iter.next();
                try {
                    Map<String, Object> stats = om.readValue(entry.value, Map.class);
                    result.put(entry.key, stats);
                } catch (Exception e) {
                    // skip malformed entries
                }
            }
            iter.close();
        } catch (Exception e) {
            System.err.println("Error getting all publisher stats: " + e.getMessage());
        }
        return result;
    }

    /**
     * Retourne l'éditeur avec le meilleur score de qualité.
     */
    public static Map.Entry<String, Double> getTopQualityPublisher() {
        var allStats = getAllPublisherStats();
        return allStats.entrySet().stream()
            .filter(e -> e.getValue().get("qualityScore") != null)
            .map(e -> Map.entry(e.getKey(), ((Number) e.getValue().get("qualityScore")).doubleValue()))
            .max(Comparator.comparingDouble(Map.Entry::getValue))
            .orElse(null);
    }

    /**
     * Retourne l'éditeur avec le meilleur score de réactivité.
     */
    public static Map.Entry<String, Integer> getTopReactivityPublisher() {
        var allStats = getAllPublisherStats();
        return allStats.entrySet().stream()
            .filter(e -> e.getValue().get("reactivityScore") != null)
            .filter(e -> ((Number) e.getValue().get("reactivityScore")).intValue() > 0)
            .map(e -> Map.entry(e.getKey(), ((Number) e.getValue().get("reactivityScore")).intValue()))
            .max(Comparator.comparingInt(Map.Entry::getValue))
            .orElse(null);
    }
}
