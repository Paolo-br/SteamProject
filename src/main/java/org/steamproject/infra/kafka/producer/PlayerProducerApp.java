package org.steamproject.infra.kafka.producer;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.steamproject.events.PlayerCreatedEvent;
import org.steamproject.events.GamePurchaseEvent;

import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

/**
 * CLI utilitaire combinant l'envoi d'événements PlayerCreated et GamePurchase.
 * Mode d'utilisation via propriétés système :
 * -Dmode=create       -> envoie un PlayerCreatedEvent (génération automatique si pas d'autres -D)
 * -Dmode=purchase     -> envoie un GamePurchaseEvent (utilise GamePurchaseProducer)
 */
public class PlayerProducerApp {
    public static void main(String[] args) throws Exception {
        String mode = System.getProperty("mode", "create").toLowerCase();
        String bootstrap = System.getProperty("kafka.bootstrap", "localhost:9092");
        String sr = System.getProperty("schema.registry", "http://localhost:8081");

        if ("create".equals(mode)) {
            // create player
            String playerId = System.getProperty("test.player.id", null);
            String username = System.getProperty("test.player.username", null);
            String email = System.getProperty("test.player.email", null);

            if (playerId == null || username == null || email == null) {
                var gen = new org.steamproject.ingestion.PlayerGenerator();
                var list = gen.generate(1);
                var p = list.get(0);
                if (playerId == null) playerId = p.getId();
                if (username == null) username = p.getUsername();
                if (email == null) email = p.getEmail();
            }

            Properties props = new Properties();
            props.put("bootstrap.servers", bootstrap);
            props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            props.put("value.serializer", KafkaAvroSerializer.class.getName());
            props.put("schema.registry.url", sr);

            try (KafkaProducer<String, Object> prod = new KafkaProducer<>(props)) {
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

                prod.send(new ProducerRecord<>(System.getProperty("kafka.topic.player", "player-created-events"), playerId, p)).get();
                System.out.println("Sent PlayerCreatedEvent id=" + playerId);
            }

        } else if ("purchase".equals(mode)) {
            // purchase
            String playerId = System.getProperty("test.player.id", UUID.randomUUID().toString());
            String gameId = System.getProperty("test.game.id", UUID.randomUUID().toString());
            String gameName = System.getProperty("test.game.name", "RealGame");
            double price = Double.parseDouble(System.getProperty("test.price", "9.99"));
            String platform = System.getProperty("test.platform", "PC");

            GamePurchaseEvent purchase = GamePurchaseEvent.newBuilder()
                    .setEventId(UUID.randomUUID().toString())
                    .setPurchaseId(UUID.randomUUID().toString())
                    .setGameId(gameId)
                    .setGameName(gameName)
                    .setPlayerId(playerId)
                    .setPlayerUsername(System.getProperty("test.player.username", "real_player"))
                    .setPricePaid(price)
                    .setPlatform(platform)
                    .setPublisherId(System.getProperty("test.publisher.id", "pub-real-1"))
                    .setRegion(org.steamproject.events.SalesRegion.OTHER)
                    .setTimestamp(Instant.now().toEpochMilli())
                    .build();

            org.steamproject.infra.kafka.producer.GamePurchaseProducer prod = new org.steamproject.infra.kafka.producer.GamePurchaseProducer(bootstrap, sr, System.getProperty("kafka.topic", "game-purchase-events"));
            prod.send(playerId, purchase).get();
            prod.close();
            System.out.println("Sent GamePurchaseEvent player=" + playerId + " game=" + gameId);
        } else {
            System.err.println("Unknown mode: " + mode + " (expected create|purchase)");
        }
    }
}
