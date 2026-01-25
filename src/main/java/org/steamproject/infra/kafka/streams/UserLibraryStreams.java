package org.steamproject.infra.kafka.streams;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.state.KeyValueStore;

import org.steamproject.events.GamePurchaseEvent;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Minimal Kafka Streams topology that builds a materialized KTable `user-library-store`
 * mapping playerId -> JSON array of owned games. Exposes an internal store for interactive queries.
 */
public class UserLibraryStreams {
    public static final String STORE_NAME = "user-library-store";
    private static volatile KafkaStreams streamsInstance;

    public static void main(String[] args) {
        startStreams();
    }

    public static KafkaStreams startStreams() {
        if (streamsInstance != null) return streamsInstance;
        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        String schema = System.getenv().getOrDefault("SCHEMA_REGISTRY_URL", "http://localhost:8081");

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "user-library-streams");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schema);

        StreamsBuilder builder = new StreamsBuilder();

        // Create Avro Serde using Confluent serializer/deserializer
        KafkaAvroSerializer avroSerializer = new KafkaAvroSerializer();
        KafkaAvroDeserializer avroDeserializer = new KafkaAvroDeserializer();
        Map<String, Object> serdeConfig = new HashMap<>();
        serdeConfig.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schema);
        // Request SpecificRecord instances when deserializing Avro
        serdeConfig.put("specific.avro.reader", true);
        avroSerializer.configure(serdeConfig, false);
        avroDeserializer.configure(serdeConfig, false);
        @SuppressWarnings({"unchecked", "rawtypes"})
        final Serde<Object> avroSerde = Serdes.serdeFrom((org.apache.kafka.common.serialization.Serializer) avroSerializer,
            (org.apache.kafka.common.serialization.Deserializer) avroDeserializer);

        ObjectMapper om = new ObjectMapper();

        KStream<String, Object> purchases = builder.stream("purchase.events", Consumed.with(Serdes.String(), avroSerde));

        purchases.groupByKey()
                .aggregate(
                        // initializer: empty JSON array
                        () -> "[]",
                        // aggregator: append new purchase as JSON object to existing array string
                        (playerId, purchaseObj, aggregate) -> {
                            GamePurchaseEvent purchase = (GamePurchaseEvent) purchaseObj;
                            try {
                                ArrayNode arr = (ArrayNode) om.readTree(aggregate);
                                ObjectNode obj = om.createObjectNode();
                                obj.put("purchaseId", purchase.getPurchaseId().toString());
                                obj.put("gameId", purchase.getGameId().toString());
                                obj.put("gameName", purchase.getGameName().toString());
                                obj.put("purchaseDate", Instant.ofEpochMilli(purchase.getTimestamp()).toString());
                                obj.put("pricePaid", purchase.getPricePaid());
                                arr.add(obj);
                                return om.writeValueAsString(arr);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
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
        System.out.println("UserLibraryStreams started and materialized store: " + STORE_NAME);
        return streamsInstance;
    }

    public static KafkaStreams getStreams() {
        return streamsInstance;
    }
}
