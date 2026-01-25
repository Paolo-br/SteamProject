package tools.test;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.steamproject.events.GamePurchaseEvent;

import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

public class TestPurchaseForPlayer {
    public static void main(String[] args) throws Exception {
        String bootstrap = System.getProperty("kafka.bootstrap", "localhost:9092");
        String sr = System.getProperty("schema.registry", "http://localhost:8081");
        String purchaseTopic = System.getProperty("kafka.topic", "game-purchase-events");

        String playerId = System.getProperty("test.player.id");
        if (playerId == null || playerId.isEmpty()) {
            playerId = System.getenv("TEST_PLAYER_ID");
        }
        if (playerId == null || playerId.isEmpty()) {
            playerId = "bf5b5eba-01de-4088-a100-f29a8a600361";
        }

        String gameId = System.getProperty("test.game.id", "bf3f36d1-19f7-43c7-8840-8768c5644bda");
        String gameName = System.getProperty("test.game.name", "TestGame");

        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrap);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", KafkaAvroSerializer.class.getName());
        props.put("schema.registry.url", sr);

        KafkaProducer<String, Object> prod = new KafkaProducer<>(props);

        GamePurchaseEvent purchase = GamePurchaseEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setPurchaseId(UUID.randomUUID().toString())
                .setGameId(gameId)
                .setGameName(gameName)
                .setPlayerId(playerId)
                .setPlayerUsername(System.getProperty("test.player.username", "test_player"))
                .setPricePaid(9.99)
                .setPlatform(System.getProperty("test.platform", "PC"))
                .setPublisherId(System.getProperty("test.publisher.id", "pub-test-1"))
                .setRegion(org.steamproject.events.SalesRegion.OTHER)
                .setTimestamp(Instant.now().toEpochMilli())
                .build();

        prod.send(new ProducerRecord<>(purchaseTopic, playerId, purchase)).get();
        System.out.println("Sent GamePurchaseEvent for player=" + playerId + " game=" + gameId);
        prod.close();
    }
}
