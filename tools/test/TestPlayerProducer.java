package tools.test;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.steamproject.events.PlayerCreatedEvent;
import org.steamproject.events.GamePurchaseEvent;

import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Future;

/**
 * Sends a PlayerCreatedEvent followed by a GamePurchaseEvent for the same player.
 * Used by Gradle task `runTestPlayerProducer`.
 */
public class TestPlayerProducer {
    public static void main(String[] args) throws Exception {
        String bootstrap = System.getProperty("kafka.bootstrap", "localhost:9092");
        String sr = System.getProperty("schema.registry", "http://localhost:8081");
        String playerTopic = System.getProperty("kafka.topic.player", "player-created-events");
        String purchaseTopic = System.getProperty("kafka.topic", "game-purchase-events");

        // Use a stable playerId for this run so UI can be targeted
        String playerId = System.getProperty("test.player.id", UUID.randomUUID().toString());
        String username = System.getProperty("test.player.username", "test_player");
        String email = System.getProperty("test.player.email", "test@example.com");

        // Target a previously released game id if provided, else random
        String gameId = System.getProperty("test.game.id", "bf3f36d1-19f7-43c7-8840-8768c5644bda");
        String gameName = System.getProperty("test.game.name", "TestGame");

        // Producer for PlayerCreatedEvent
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrap);
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", KafkaAvroSerializer.class.getName());
        props.put("schema.registry.url", sr);
        KafkaProducer<String, Object> prod = new KafkaProducer<>(props);

        PlayerCreatedEvent p = PlayerCreatedEvent.newBuilder()
                .setId(playerId)
                .setUsername(username)
                .setEmail(email)
                .setRegistrationDate(Instant.now().toString())
                .setDistributionPlatformId(null)
                .setFirstName(null)
                .setLastName(null)
                .setDateOfBirth(null)
                .setGdprConsent(false)
                .setGdprConsentDate(null)
                .setTimestamp(Instant.now().toEpochMilli())
                .build();

        prod.send(new ProducerRecord<>(playerTopic, playerId, p)).get();
        System.out.println("Sent PlayerCreatedEvent id=" + playerId);

        // Now send purchase event for that player and target game
        GamePurchaseEvent purchase = GamePurchaseEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setPurchaseId(UUID.randomUUID().toString())
                .setGameId(gameId)
                .setGameName(gameName)
                .setPlayerId(playerId)
                .setPlayerUsername(username)
                .setPricePaid(9.99)
                .setPlatform("PC")
                .setPublisherId("pub-test-1")
                .setRegion(org.steamproject.events.SalesRegion.OTHER)
                .setTimestamp(Instant.now().toEpochMilli())
                .build();

        KafkaProducer<String, Object> purchaseProd = new KafkaProducer<>(props);
        purchaseProd.send(new ProducerRecord<>(purchaseTopic, playerId, purchase)).get();
        System.out.println("Sent GamePurchaseEvent player=" + playerId + " game=" + gameId);

        prod.close();
        purchaseProd.close();
    }
}
