package org.steamproject.infra.kafka.streams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import org.steamproject.events.PlatformCatalogUpdateEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Kafka Streams topology pour gérer la projection Platform -> Catalog
 * Remplace PlatformProjection.java avec un state store Kafka Streams
 * Store: platform-catalog-store (platformId -> array JSON des jeux du catalogue)
 */
public class PlatformCatalogStreams {
    public static final String STORE_NAME = "platform-catalog-store";
    private static volatile KafkaStreams streamsInstance;

    public static void main(String[] args) {
        startStreams();
    }

    public static KafkaStreams startStreams() {
        if (streamsInstance != null) return streamsInstance;
        
        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        String schema = System.getenv().getOrDefault("SCHEMA_REGISTRY_URL", "http://localhost:8081");

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "platform-catalog-streams");
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

        ObjectMapper om = new ObjectMapper();

        // Stream des événements platform-catalog
        KStream<String, Object> platformEvents = builder.stream("platform-catalog.events", 
            Consumed.with(Serdes.String(), avroSerde));

        // Agréger les jeux par platformId
        platformEvents
            .filter((key, value) -> value != null && value instanceof PlatformCatalogUpdateEvent)
            .map((key, eventObj) -> {
                PlatformCatalogUpdateEvent evt = (PlatformCatalogUpdateEvent) eventObj;
                String platformId = evt.getPlatformId().toString();
                String gameName = evt.getGameName() != null ? evt.getGameName().toString() : "";
                String gameInfo = evt.getGameId() + "|" + gameName + "|";
                return new org.apache.kafka.streams.KeyValue<>(platformId, gameInfo);
            })
            .groupByKey()
            .aggregate(
                // Initializer: tableau JSON vide
                () -> "[]",
                // Aggregator: ajouter le jeu au catalogue
                (platformId, gameInfo, aggregate) -> {
                    try {
                        ArrayNode arr = (ArrayNode) om.readTree(aggregate);
                        // Vérifier si déjà présent
                        boolean exists = false;
                        for (int i = 0; i < arr.size(); i++) {
                            if (arr.get(i).asText().equals(gameInfo)) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            arr.add(gameInfo);
                        }
                        return om.writeValueAsString(arr);
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
        
        System.out.println("PlatformCatalogStreams started with store: " + STORE_NAME);
        return streamsInstance;
    }

    public static KafkaStreams getStreams() {
        return streamsInstance;
    }
}
