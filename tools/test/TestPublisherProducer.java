package tools.test;

import org.steamproject.events.GameReleasedEvent;
import java.time.Instant;
import java.util.UUID;

public class TestPublisherProducer {
    public static void main(String[] args) throws Exception {
        String bootstrap = System.getProperty("kafka.bootstrap", "localhost:9092");
        String sr = System.getProperty("schema.registry", "http://localhost:8081");
        String topic = System.getProperty("kafka.topic.pub", "game-released-events");

        String gameId = UUID.randomUUID().toString();
        String pubId = "pub-test-1";

        PublisherProducer p = new PublisherProducer(bootstrap, sr);
        p.publishGame(gameId, "TestGame", pubId, "TestPub", 2026, "PC", java.util.Arrays.asList("PC"), "1.0.0", 19.99, "Action").get();
        System.out.println("Sent GameReleasedEvent gameId=" + gameId);
        p.close();
    }
}
