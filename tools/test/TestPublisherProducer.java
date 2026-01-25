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

        GameReleasedEvent evt = GameReleasedEvent.newBuilder()
                .setTimestamp(Instant.now().toEpochMilli())
                .setInitialVersion("1.0.0")
                .setReleaseYear(2026)
                .setInitialPrice(19.99)
                .setGenre("Action")
                .setEventId(UUID.randomUUID().toString())
                .setPlatform("PC")
                .setPlatforms(java.util.Arrays.asList("PC"))
                .setPublisherName("TestPub")
                .setPublisherId(pubId)
                .setGameName("TestGame")
                .setGameId(gameId)
                .build();

        PublisherProducer p = new PublisherProducer(bootstrap, sr, topic);
        p.sendGameReleased(gameId, evt).get();
        System.out.println("Sent GameReleasedEvent gameId=" + gameId);
        p.close();
    }
}
