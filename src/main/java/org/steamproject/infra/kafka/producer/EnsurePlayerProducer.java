package org.steamproject.infra.kafka.producer;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.steamproject.events.PlayerCreatedEvent;

import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

/**
 * Petit utilitaire permettant d'émettre un {@code PlayerCreatedEvent} de
 * test/assurance pour s'assurer que le joueur existe dans le flux d'événements.
 */
public class EnsurePlayerProducer {

    public static void main(String[] args) throws Exception {
        String bootstrap = System.getProperty("kafka.bootstrap", "localhost:9092");
        String sr = System.getProperty("schema.registry", "http://localhost:8081");
        String playerTopic = System.getProperty("kafka.topic.player", "player-created-events");

        String playerId = System.getProperty("test.player.id", System.getenv("TEST_PLAYER_ID"));
        String username = System.getProperty("test.player.username", null);
        String email = System.getProperty("test.player.email", null);

        if (playerId == null || playerId.isEmpty() || username == null || email == null) {
            try {
                var gen = new org.steamproject.ingestion.PlayerGenerator();
                var list = gen.generate(1);
                if (list != null && !list.isEmpty()) {
                    var p = list.get(0);
                    if (playerId == null || playerId.isEmpty()) playerId = p.getId();
                    if (username == null) username = p.getUsername();
                    if (email == null) email = p.getEmail();
                }
            } catch (Throwable t) {
                if (playerId == null || playerId.isEmpty()) playerId = "bf5b5eba-01de-4088-a100-f29a8a600361";
                if (username == null) username = "test_player";
                if (email == null) email = "test@example.com";
            }
        }

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
                .setGdprConsent(true)
                .setGdprConsentDate(Instant.now().toString())
                .setTimestamp(Instant.now().toEpochMilli())
                .build();

        prod.send(new ProducerRecord<>(playerTopic, playerId, p)).get();
        System.out.println("Sent PlayerCreatedEvent id=" + playerId);
        prod.close();
    }
}
