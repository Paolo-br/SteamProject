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
import org.steamproject.events.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Kafka Streams topology pour gérer la projection Publisher -> Games
 * Remplace PublisherProjection.java avec un state store Kafka Streams
 * Store: publisher-games-store (publisherId -> array JSON des jeux publiés)
 */
public class PublisherGamesStreams {
    public static final String STORE_NAME = "publisher-games-store";
    private static volatile KafkaStreams streamsInstance;

    public static void main(String[] args) {
        startStreams();
    }

    public static KafkaStreams startStreams() {
        if (streamsInstance != null) return streamsInstance;
        
        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        String schema = System.getenv().getOrDefault("SCHEMA_REGISTRY_URL", "http://localhost:8081");

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "publisher-games-streams");
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

        // Topics publisher: game-released, game-published, game-updated, patch-published, dlc-published, etc.
        String[] publisherTopics = {
            "game-released.events",
            "game-published.events",
            "game-updated.events",
            "patch-published.events",
            "dlc-published.events"
        };

        // Merge tous les streams de publishers
        KStream<String, Object> allPublisherEvents = null;
        
        for (String topic : publisherTopics) {
            KStream<String, Object> stream = builder.stream(topic, Consumed.with(Serdes.String(), avroSerde));
            if (allPublisherEvents == null) {
                allPublisherEvents = stream;
            } else {
                allPublisherEvents = allPublisherEvents.merge(stream);
            }
        }

        // Extraire publisherId et agréger les jeux
        allPublisherEvents
            .filter((key, value) -> value != null)
            .map((key, eventObj) -> {
                try {
                    String publisherId = null;
                    String gameInfo = null;
                    
                    // GameReleasedEvent
                    if (eventObj instanceof GameReleasedEvent) {
                        GameReleasedEvent evt = (GameReleasedEvent) eventObj;
                        publisherId = evt.getPublisherId().toString();
                        gameInfo = evt.getGameId() + "|" + evt.getGameName() + "|" + evt.getReleaseYear();
                    }
                    // GamePublishedEvent
                    else if (eventObj instanceof GamePublishedEvent) {
                        GamePublishedEvent evt = (GamePublishedEvent) eventObj;
                        publisherId = evt.getPublisherId().toString();
                        gameInfo = evt.getGameId() + "|" + evt.getGameName() + "|" + 
                                  java.time.Instant.ofEpochMilli(evt.getReleaseTimestamp()).atZone(java.time.ZoneId.systemDefault()).getYear();
                    }
                    // DlcPublishedEvent
                    else if (eventObj instanceof DlcPublishedEvent) {
                        DlcPublishedEvent evt = (DlcPublishedEvent) eventObj;
                        publisherId = evt.getPublisherId().toString();
                        gameInfo = evt.getGameId() + "|dlc:" + evt.getDlcName() + "|";
                    }
                    
                    if (publisherId != null && gameInfo != null) {
                        return new org.apache.kafka.streams.KeyValue<>(publisherId, gameInfo);
                    }
                } catch (Exception e) {
                    // Ignorer les événements mal formés
                }
                return new org.apache.kafka.streams.KeyValue<>(null, null);
            })
            .filter((publisherId, gameInfo) -> publisherId != null && gameInfo != null)
            .groupByKey()
            .aggregate(
                // Initializer: tableau JSON vide
                () -> "[]",
                // Aggregator: ajouter le jeu au tableau
                (publisherId, gameInfo, aggregate) -> {
                    try {
                        ArrayNode arr = (ArrayNode) om.readTree(aggregate);
                        // Vérifier si déjà présent (éviter doublons)
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
        
        System.out.println("PublisherGamesStreams started with store: " + STORE_NAME);
        return streamsInstance;
    }

    public static KafkaStreams getStreams() {
        return streamsInstance;
    }
}
