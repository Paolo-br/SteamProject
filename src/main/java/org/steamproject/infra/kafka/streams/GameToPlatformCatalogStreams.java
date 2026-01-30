package org.steamproject.infra.kafka.streams;

import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.steamproject.events.*;

import java.util.*;

/**
 * Kafka Streams topology pour router automatiquement les jeux vers les plateformes de distribution.
 * 
 * Quand un jeu est publié (GamePublishedEvent) ou released (GameReleasedEvent), 
 * ce stream détermine la plateforme de distribution correspondante à la console/plateforme
 * du jeu et émet un PlatformCatalogUpdateEvent.
 * 
 * Mapping Console -> Plateforme de distribution:
 * - PS, PS2, PS3, PS4, PS5, PSP, PSV -> psn (PlayStation Store)
 * - XB, X360, XOne, XS -> xbox (Xbox Store)  
 * - Wii, WiiU, DS, 3DS, GBA, GB, N64, GC, NES, SNES, NS -> nintendo (Nintendo eShop)
 * - PC et autres -> steam (Steam)
 */
public class GameToPlatformCatalogStreams {
    public static final String PLATFORM_CATALOG_TOPIC = "platform-catalog.events";
    private static volatile KafkaStreams streamsInstance;
    private static volatile KafkaProducer<String, Object> catalogProducer;

    public static void main(String[] args) {
        startStreams();
    }

    /**
     * Détermine l'identifiant de la plateforme de distribution à partir du code console/hardware.
     * 
     * @param hardwareCode Code de la console (PS4, PC, Wii, XOne, etc.)
     * @return Identifiant de la plateforme de distribution (steam, psn, xbox, nintendo)
     */
    public static String inferDistributionPlatformId(String hardwareCode) {
        if (hardwareCode == null || hardwareCode.isBlank()) {
            return "steam";
        }
        
        String code = hardwareCode.toUpperCase().trim();
        
        // Consoles Sony -> PlayStation Store
        if (code.startsWith("PS") || code.equals("PSP") || code.equals("PSV")) {
            return "psn";
        }
        
        // Consoles Microsoft -> Xbox Store
        if (code.startsWith("X") || code.equals("XB") || code.equals("X360") || 
            code.equals("XONE") || code.equals("XS")) {
            return "xbox";
        }
        
        // Consoles Nintendo -> Nintendo eShop
        if (code.equals("WII") || code.equals("WIIU") || code.equals("NS") || 
            code.equals("N64") || code.equals("GC") || code.equals("GB") || 
            code.equals("GBA") || code.equals("DS") || code.equals("3DS") || 
            code.equals("NES") || code.equals("SNES")) {
            return "nintendo";
        }
        
        // PC et autres -> Steam par défaut
        return "steam";
    }

    public static KafkaStreams startStreams() {
        if (streamsInstance != null) return streamsInstance;
        
        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        String schema = System.getenv().getOrDefault("SCHEMA_REGISTRY_URL", "http://localhost:8081");

        // Créer le producteur pour les événements de catalogue de plateforme
        Properties producerProps = new Properties();
        producerProps.put("bootstrap.servers", bootstrap);
        producerProps.put("key.serializer", StringSerializer.class.getName());
        producerProps.put("value.serializer", KafkaAvroSerializer.class.getName());
        producerProps.put("schema.registry.url", schema);
        catalogProducer = new KafkaProducer<>(producerProps);

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "game-to-platform-catalog-streams");
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

        // Stream des événements game-published
        KStream<String, Object> gamePublishedStream = builder.stream("game-published-events", 
            Consumed.with(Serdes.String(), avroSerde));
        
        // Stream des événements game-released
        KStream<String, Object> gameReleasedStream = builder.stream("game-released-events",
            Consumed.with(Serdes.String(), avroSerde));

        // Traiter les GamePublishedEvent
        gamePublishedStream
            .filter((key, value) -> value != null && value instanceof GamePublishedEvent)
            .foreach((key, eventObj) -> {
                GamePublishedEvent evt = (GamePublishedEvent) eventObj;
                processGameEvent(
                    evt.getGameId().toString(),
                    evt.getGameName().toString(),
                    evt.getPlatform() != null ? evt.getPlatform().toString() : null,
                    evt.getReleaseTimestamp()
                );
            });

        // Traiter les GameReleasedEvent
        gameReleasedStream
            .filter((key, value) -> value != null && value instanceof GameReleasedEvent)
            .foreach((key, eventObj) -> {
                GameReleasedEvent evt = (GameReleasedEvent) eventObj;
                processGameEvent(
                    evt.getGameId().toString(),
                    evt.getGameName().toString(),
                    evt.getPlatform() != null ? evt.getPlatform().toString() : null,
                    System.currentTimeMillis()
                );
            });

        streamsInstance = new KafkaStreams(builder.build(), props);
        streamsInstance.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { 
                if (catalogProducer != null) {
                    catalogProducer.flush();
                    catalogProducer.close();
                }
                streamsInstance.close(); 
            } catch (Exception ignored) {}
        }));
        
        System.out.println("GameToPlatformCatalogStreams started - routing games to distribution platforms");
        return streamsInstance;
    }

    /**
     * Traite un événement de jeu et publie un PlatformCatalogUpdateEvent.
     */
    private static void processGameEvent(String gameId, String gameName, String platform, long timestamp) {
        String distributionPlatformId = inferDistributionPlatformId(platform);
        
        PlatformCatalogUpdateEvent catalogEvent = PlatformCatalogUpdateEvent.newBuilder()
            .setPlatformId(distributionPlatformId)
            .setGameId(gameId)
            .setGameName(gameName)
            .setAction(CatalogAction.ADD)
            .setTimestamp(timestamp)
            .build();

        ProducerRecord<String, Object> record = new ProducerRecord<>(
            PLATFORM_CATALOG_TOPIC,
            gameId,
            catalogEvent
        );

        catalogProducer.send(record, (metadata, exception) -> {
            if (exception != null) {
                System.err.println("Error sending PlatformCatalogUpdateEvent for " + gameId + ": " + exception.getMessage());
            } else {
                System.out.println("Routed game '" + gameName + "' (platform: " + platform + 
                    ") -> Distribution Platform: " + distributionPlatformId);
            }
        });
    }

    public static KafkaStreams getStreams() {
        return streamsInstance;
    }
}
