package tools.admin;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.steamproject.events.PlayerCreatedEvent;

import java.time.Instant;
import java.util.Properties;

/**
 * Admin utility moved out of main sources to tools/admin.
 * Run interactively when you need to ensure a PlayerCreatedEvent exists.
 */
public class EnsurePlayerProducer {
    public static void main(String[] args) throws Exception {
        String bootstrap = System.getProperty("kafka.bootstrap", "localhost:9092");
        String sr = System.getProperty("schema.registry", "http://localhost:8081");
        String playerTopic = System.getProperty("kafka.topic.player", "player-created-events");

        String playerId = System.getProperty("test.player.id", System.getenv("TEST_PLAYER_ID"));
        if (playerId == null || playerId.isEmpty()) playerId = "bf5b5eba-01de-4088-a100-f29a8a600361";
        String username = System.getProperty("test.player.username", "test_player");
        String email = System.getProperty("test.player.email", "test@example.com");

        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrap);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
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
        prod.close();
    }
}
