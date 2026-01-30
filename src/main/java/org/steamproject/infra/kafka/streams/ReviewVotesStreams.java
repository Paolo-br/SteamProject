package org.steamproject.infra.kafka.streams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.steamproject.events.ReviewVotedEvent;
import org.steamproject.events.VoteAction;

import java.util.*;

/**
 * Kafka Streams pour l'agrégation des votes d'utilité sur les évaluations.
 * 
 * Ce processeur consomme les événements ReviewVotedEvent et maintient une vue
 * matérialisée des votes agrégés par évaluation (reviewId).
 * 
 * Fonctionnalités :
 * - Agrégation des votes "utile" et "pas utile" par évaluation
 * - Gestion des re-votes (un joueur peut changer son vote)
 * - Idempotence basée sur l'eventId
 * - Support de la suppression de vote
 * - Exposition des données via Interactive Queries
 * 
 * Structure de l'état agrégé par reviewId (JSON) :
 * {
 *   "reviewId": "...",
 *   "helpfulVotes": 10,
 *   "notHelpfulVotes": 3,
 *   "voters": {
 *     "playerId1": { "isHelpful": true, "eventId": "...", "timestamp": 123 },
 *     "playerId2": { "isHelpful": false, "eventId": "...", "timestamp": 456 }
 *   },
 *   "processedEventIds": ["eventId1", "eventId2", ...]
 * }
 */
public class ReviewVotesStreams {
    
    public static final String REVIEW_VOTES_STORE = "review-votes-store";
    private static final String TOPIC_REVIEW_VOTED = "review-voted-events";
    
    // Limite du nombre d'eventIds stockés pour l'idempotence (fenêtre glissante)
    private static final int MAX_PROCESSED_EVENT_IDS = 1000;
    
    private static volatile KafkaStreams streamsInstance;
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        startStreams();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (streamsInstance != null) streamsInstance.close();
        }));
    }

    public static KafkaStreams startStreams() {
        if (streamsInstance != null) return streamsInstance;
        
        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", 
            System.getProperty("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"));
        String schema = System.getenv().getOrDefault("SCHEMA_REGISTRY_URL", 
            System.getProperty("SCHEMA_REGISTRY_URL", "http://localhost:8081"));

        Properties props = new Properties();
        String autoReset = System.getProperty("kafka.auto.offset.reset", "earliest");
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "review-votes-streams");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schema);
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1000);
        props.put("auto.offset.reset", autoReset);

        StreamsBuilder builder = new StreamsBuilder();

        // Configuration Avro Serde
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

        // =========================================================================
        // Agrégation des votes par reviewId
        // =========================================================================
        KStream<String, Object> votesStream = builder.stream(TOPIC_REVIEW_VOTED,
            Consumed.with(Serdes.String(), avroSerde));
        
        votesStream
            .filter((key, value) -> value instanceof ReviewVotedEvent)
            .groupByKey()
            .aggregate(
                // Initializer: état vide
                () -> {
                    try {
                        ObjectNode initial = mapper.createObjectNode();
                        initial.put("helpfulVotes", 0);
                        initial.put("notHelpfulVotes", 0);
                        initial.set("voters", mapper.createObjectNode());
                        initial.set("processedEventIds", mapper.createArrayNode());
                        return mapper.writeValueAsString(initial);
                    } catch (Exception e) {
                        return "{}";
                    }
                },
                // Aggregator: traite chaque vote
                (reviewId, eventObj, aggregate) -> {
                    ReviewVotedEvent event = (ReviewVotedEvent) eventObj;
                    try {
                        ObjectNode state = (ObjectNode) mapper.readTree(aggregate);
                        
                        String eventId = event.getEventId() != null ? event.getEventId().toString() : "";
                        String voterId = event.getVoterPlayerId() != null ? event.getVoterPlayerId().toString() : "";
                        boolean isHelpful = event.getIsHelpful();
                        VoteAction action = event.getVoteAction();
                        long timestamp = event.getTimestamp();
                        
                        // Vérification d'idempotence
                        ArrayNode processedIds = (ArrayNode) state.get("processedEventIds");
                        if (processedIds == null) {
                            processedIds = mapper.createArrayNode();
                            state.set("processedEventIds", processedIds);
                        }
                        
                        // Vérifie si cet eventId a déjà été traité
                        for (int i = 0; i < processedIds.size(); i++) {
                            if (eventId.equals(processedIds.get(i).asText())) {
                                // Événement déjà traité, on ignore
                                return aggregate;
                            }
                        }
                        
                        // Ajoute l'eventId à la liste des traités
                        processedIds.add(eventId);
                        // Limite la taille de la liste (fenêtre glissante)
                        while (processedIds.size() > MAX_PROCESSED_EVENT_IDS) {
                            processedIds.remove(0);
                        }
                        
                        ObjectNode voters = (ObjectNode) state.get("voters");
                        if (voters == null) {
                            voters = mapper.createObjectNode();
                            state.set("voters", voters);
                        }
                        
                        int helpfulVotes = state.has("helpfulVotes") ? state.get("helpfulVotes").asInt() : 0;
                        int notHelpfulVotes = state.has("notHelpfulVotes") ? state.get("notHelpfulVotes").asInt() : 0;
                        
                        // Récupère le vote précédent du joueur (si existant)
                        boolean hadPreviousVote = voters.has(voterId);
                        Boolean previousVoteHelpful = null;
                        if (hadPreviousVote) {
                            previousVoteHelpful = voters.get(voterId).get("isHelpful").asBoolean();
                        }
                        
                        if (action == null) {
                            action = VoteAction.CAST;
                        }
                        
                        switch (action) {
                            case CAST:
                            case CHANGE:
                                // Si le joueur avait déjà voté, on retire son ancien vote
                                if (hadPreviousVote && previousVoteHelpful != null) {
                                    if (previousVoteHelpful) {
                                        helpfulVotes = Math.max(0, helpfulVotes - 1);
                                    } else {
                                        notHelpfulVotes = Math.max(0, notHelpfulVotes - 1);
                                    }
                                }
                                
                                // Ajoute le nouveau vote
                                if (isHelpful) {
                                    helpfulVotes++;
                                } else {
                                    notHelpfulVotes++;
                                }
                                
                                // Enregistre le vote du joueur
                                ObjectNode voterInfo = mapper.createObjectNode();
                                voterInfo.put("isHelpful", isHelpful);
                                voterInfo.put("eventId", eventId);
                                voterInfo.put("timestamp", timestamp);
                                voters.set(voterId, voterInfo);
                                break;
                                
                            case REMOVE:
                                // Supprime le vote du joueur
                                if (hadPreviousVote && previousVoteHelpful != null) {
                                    if (previousVoteHelpful) {
                                        helpfulVotes = Math.max(0, helpfulVotes - 1);
                                    } else {
                                        notHelpfulVotes = Math.max(0, notHelpfulVotes - 1);
                                    }
                                    voters.remove(voterId);
                                }
                                break;
                        }
                        
                        state.put("helpfulVotes", helpfulVotes);
                        state.put("notHelpfulVotes", notHelpfulVotes);
                        state.put("reviewId", reviewId);
                        state.put("lastUpdated", timestamp);
                        
                        return mapper.writeValueAsString(state);
                    } catch (Exception e) {
                        System.err.println("[ReviewVotesStreams] Error processing vote: " + e.getMessage());
                        return aggregate;
                    }
                },
                Materialized.<String, String, KeyValueStore<org.apache.kafka.common.utils.Bytes, byte[]>>as(REVIEW_VOTES_STORE)
                    .withKeySerde(Serdes.String())
                    .withValueSerde(Serdes.String())
            );

        streamsInstance = new KafkaStreams(builder.build(), props);
        
        streamsInstance.setStateListener((newState, oldState) -> {
            System.out.println("[ReviewVotesStreams] State changed from " + oldState + " to " + newState);
        });
        
        streamsInstance.start();
        
        System.out.println("[ReviewVotesStreams] Started - consuming from " + TOPIC_REVIEW_VOTED);

        return streamsInstance;
    }
    
    /**
     * Récupère les votes agrégés pour une évaluation donnée.
     * 
     * @param reviewId identifiant de l'évaluation
     * @return Map contenant helpfulVotes, notHelpfulVotes, et la liste des votants
     */
    public static Map<String, Object> getVotesForReview(String reviewId) {
        if (streamsInstance == null || 
            streamsInstance.state() != KafkaStreams.State.RUNNING) {
            return Collections.emptyMap();
        }
        
        try {
            ReadOnlyKeyValueStore<String, String> store = streamsInstance.store(
                org.apache.kafka.streams.StoreQueryParameters.fromNameAndType(
                    REVIEW_VOTES_STORE, 
                    QueryableStoreTypes.keyValueStore()
                )
            );
            
            String value = store.get(reviewId);
            if (value == null) {
                return Collections.emptyMap();
            }
            
            ObjectNode node = (ObjectNode) mapper.readTree(value);
            Map<String, Object> result = new HashMap<>();
            result.put("reviewId", reviewId);
            result.put("helpfulVotes", node.has("helpfulVotes") ? node.get("helpfulVotes").asInt() : 0);
            result.put("notHelpfulVotes", node.has("notHelpfulVotes") ? node.get("notHelpfulVotes").asInt() : 0);
            result.put("lastUpdated", node.has("lastUpdated") ? node.get("lastUpdated").asLong() : 0L);
            
            // Extrait la liste des votants (sans les eventIds pour la réponse API)
            List<Map<String, Object>> votersList = new ArrayList<>();
            if (node.has("voters")) {
                ObjectNode voters = (ObjectNode) node.get("voters");
                voters.fieldNames().forEachRemaining(voterId -> {
                    Map<String, Object> voterInfo = new HashMap<>();
                    voterInfo.put("playerId", voterId);
                    voterInfo.put("isHelpful", voters.get(voterId).get("isHelpful").asBoolean());
                    voterInfo.put("timestamp", voters.get(voterId).get("timestamp").asLong());
                    votersList.add(voterInfo);
                });
            }
            result.put("voters", votersList);
            
            return result;
        } catch (Exception e) {
            System.err.println("[ReviewVotesStreams] Error getting votes for review " + reviewId + ": " + e.getMessage());
            return Collections.emptyMap();
        }
    }
    
    /**
     * Récupère tous les votes agrégés.
     * 
     * @return Liste de tous les votes par évaluation
     */
    public static List<Map<String, Object>> getAllVotes() {
        List<Map<String, Object>> allVotes = new ArrayList<>();
        
        if (streamsInstance == null || 
            streamsInstance.state() != KafkaStreams.State.RUNNING) {
            return allVotes;
        }
        
        try {
            ReadOnlyKeyValueStore<String, String> store = streamsInstance.store(
                org.apache.kafka.streams.StoreQueryParameters.fromNameAndType(
                    REVIEW_VOTES_STORE, 
                    QueryableStoreTypes.keyValueStore()
                )
            );
            
            store.all().forEachRemaining(kv -> {
                try {
                    Map<String, Object> votes = getVotesForReview(kv.key);
                    if (!votes.isEmpty()) {
                        allVotes.add(votes);
                    }
                } catch (Exception ignored) {}
            });
            
        } catch (Exception e) {
            System.err.println("[ReviewVotesStreams] Error getting all votes: " + e.getMessage());
        }
        
        return allVotes;
    }
    
    /**
     * Vérifie si un joueur a déjà voté sur une évaluation.
     * 
     * @param reviewId identifiant de l'évaluation
     * @param playerId identifiant du joueur
     * @return null si pas de vote, sinon true pour "utile" et false pour "pas utile"
     */
    public static Boolean getPlayerVote(String reviewId, String playerId) {
        if (streamsInstance == null || 
            streamsInstance.state() != KafkaStreams.State.RUNNING) {
            return null;
        }
        
        try {
            ReadOnlyKeyValueStore<String, String> store = streamsInstance.store(
                org.apache.kafka.streams.StoreQueryParameters.fromNameAndType(
                    REVIEW_VOTES_STORE, 
                    QueryableStoreTypes.keyValueStore()
                )
            );
            
            String value = store.get(reviewId);
            if (value == null) {
                return null;
            }
            
            ObjectNode node = (ObjectNode) mapper.readTree(value);
            if (node.has("voters")) {
                ObjectNode voters = (ObjectNode) node.get("voters");
                if (voters.has(playerId)) {
                    return voters.get(playerId).get("isHelpful").asBoolean();
                }
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    public static void stopStreams() {
        if (streamsInstance != null) {
            streamsInstance.close();
            streamsInstance = null;
        }
    }
    
    public static boolean isRunning() {
        return streamsInstance != null && streamsInstance.state() == KafkaStreams.State.RUNNING;
    }
}
