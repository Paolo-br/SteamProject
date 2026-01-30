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
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.apache.kafka.streams.StoreQueryParameters;
import org.steamproject.events.PatchPublishedEvent;

import java.time.Instant;
import java.util.*;

/**
 * Kafka Streams topology pour gérer la projection Game -> Patches
 * Consomme les événements PatchPublishedEvent et maintient un state store
 * Store: game-patches-store (gameId -> array JSON des patches)
 * 
 * Chaque patch contient:
 * - patchId: identifiant unique du patch
 * - oldVersion: version précédente
 * - newVersion: nouvelle version
 * - changeLog: description des changements
 * - timestamp: date du patch
 */
public class GamePatchesStreams {
    public static final String STORE_NAME = "game-patches-store";
    private static volatile KafkaStreams streamsInstance;
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        startStreams();
        // Keep running
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
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "game-patches-streams");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schema);

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

        // Stream des événements patch-published
        KStream<String, Object> patchEvents = builder.stream("patch-published-events", 
            Consumed.with(Serdes.String(), avroSerde));

        // Agréger les patches par gameId
        patchEvents
            .filter((key, value) -> value != null && value instanceof PatchPublishedEvent)
            .map((key, eventObj) -> {
                PatchPublishedEvent evt = (PatchPublishedEvent) eventObj;
                String gameId = evt.getGameId().toString();
                
                // Créer un JSON pour ce patch
                try {
                    ObjectNode patchJson = mapper.createObjectNode();
                    String patchId = gameId + "-patch-" + evt.getTimestamp();
                    patchJson.put("patchId", patchId);
                    patchJson.put("gameId", gameId);
                    patchJson.put("oldVersion", evt.getOldVersion() != null ? evt.getOldVersion().toString() : "");
                    patchJson.put("newVersion", evt.getNewVersion() != null ? evt.getNewVersion().toString() : "");
                    patchJson.put("changeLog", evt.getChangeLog() != null ? evt.getChangeLog().toString() : "");
                    patchJson.put("timestamp", evt.getTimestamp());
                    patchJson.put("releaseDate", Instant.ofEpochMilli(evt.getTimestamp()).toString());
                    
                    return new org.apache.kafka.streams.KeyValue<>(gameId, mapper.writeValueAsString(patchJson));
                } catch (Exception e) {
                    return new org.apache.kafka.streams.KeyValue<>(gameId, "{}");
                }
            })
            .groupByKey()
            .aggregate(
                // Initializer: tableau JSON vide
                () -> "[]",
                // Aggregator: ajouter le patch à la liste
                (gameId, patchJsonStr, aggregate) -> {
                    try {
                        ArrayNode arr = (ArrayNode) mapper.readTree(aggregate);
                        ObjectNode patchJson = (ObjectNode) mapper.readTree(patchJsonStr);
                        
                        // Vérifier si ce patch existe déjà (par patchId)
                        String newPatchId = patchJson.has("patchId") ? patchJson.get("patchId").asText() : null;
                        boolean exists = false;
                        if (newPatchId != null) {
                            for (int i = 0; i < arr.size(); i++) {
                                if (arr.get(i).has("patchId") && 
                                    arr.get(i).get("patchId").asText().equals(newPatchId)) {
                                    exists = true;
                                    break;
                                }
                            }
                        }
                        
                        if (!exists) {
                            arr.add(patchJson);
                        }
                        return mapper.writeValueAsString(arr);
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
        
        System.out.println("GamePatchesStreams started with store: " + STORE_NAME);
        return streamsInstance;
    }

    public static KafkaStreams getStreams() {
        return streamsInstance;
    }

    // ==================== INTERACTIVE QUERIES ====================

    /**
     * Récupère tous les patches pour un jeu donné
     */
    public static List<Map<String, Object>> getPatches(String gameId) {
        if (streamsInstance == null) return Collections.emptyList();
        
        try {
            ReadOnlyKeyValueStore<String, String> store = streamsInstance.store(
                StoreQueryParameters.fromNameAndType(STORE_NAME, QueryableStoreTypes.keyValueStore())
            );
            
            String patchesJson = store.get(gameId);
            if (patchesJson == null || "[]".equals(patchesJson)) {
                return Collections.emptyList();
            }
            
            List<Map<String, Object>> result = new ArrayList<>();
            ArrayNode arr = (ArrayNode) mapper.readTree(patchesJson);
            for (int i = 0; i < arr.size(); i++) {
                result.add(mapper.convertValue(arr.get(i), Map.class));
            }
            return result;
        } catch (Exception e) {
            System.err.println("Error getting patches for game " + gameId + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Récupère tous les patches de tous les jeux
     */
    public static Map<String, List<Map<String, Object>>> getAllPatches() {
        if (streamsInstance == null) return Collections.emptyMap();
        
        try {
            ReadOnlyKeyValueStore<String, String> store = streamsInstance.store(
                StoreQueryParameters.fromNameAndType(STORE_NAME, QueryableStoreTypes.keyValueStore())
            );
            
            Map<String, List<Map<String, Object>>> result = new HashMap<>();
            try (var iterator = store.all()) {
                while (iterator.hasNext()) {
                    var kv = iterator.next();
                    String gameId = kv.key;
                    String patchesJson = kv.value;
                    
                    List<Map<String, Object>> patches = new ArrayList<>();
                    if (patchesJson != null && !"[]".equals(patchesJson)) {
                        ArrayNode arr = (ArrayNode) mapper.readTree(patchesJson);
                        for (int i = 0; i < arr.size(); i++) {
                            patches.add(mapper.convertValue(arr.get(i), Map.class));
                        }
                    }
                    result.put(gameId, patches);
                }
            }
            return result;
        } catch (Exception e) {
            System.err.println("Error getting all patches: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Récupère le dernier patch (version la plus récente) pour un jeu
     */
    public static Map<String, Object> getLatestPatch(String gameId) {
        List<Map<String, Object>> patches = getPatches(gameId);
        if (patches.isEmpty()) return null;
        
        // Trier par timestamp décroissant et retourner le premier
        return patches.stream()
            .max((p1, p2) -> {
                Long t1 = p1.get("timestamp") instanceof Number ? ((Number) p1.get("timestamp")).longValue() : 0L;
                Long t2 = p2.get("timestamp") instanceof Number ? ((Number) p2.get("timestamp")).longValue() : 0L;
                return t1.compareTo(t2);
            })
            .orElse(null);
    }

    /**
     * Récupère la version actuelle d'un jeu (dernière newVersion)
     */
    public static String getCurrentVersion(String gameId) {
        Map<String, Object> latest = getLatestPatch(gameId);
        if (latest == null) return null;
        return latest.get("newVersion") != null ? latest.get("newVersion").toString() : null;
    }
}
