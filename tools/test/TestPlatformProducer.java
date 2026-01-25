package tools.test;

import org.steamproject.events.PlatformCatalogUpdateEvent;
import org.steamproject.events.CatalogAction;
import java.time.Instant;

public class TestPlatformProducer {
    public static void main(String[] args) throws Exception {
        String bootstrap = System.getProperty("kafka.bootstrap", "localhost:9092");
        String sr = System.getProperty("schema.registry", "http://localhost:8081");
        String topic = System.getProperty("kafka.topic.platform", "platform-catalog-events");

        String gameId = java.util.UUID.randomUUID().toString();
        String platformId = "plat-test-1";

        PlatformCatalogUpdateEvent evt = PlatformCatalogUpdateEvent.newBuilder()
                .setPlatformId(platformId)
                .setGameId(gameId)
                .setGameName("PlatformTestGame")
                .setAction(CatalogAction.ADD)
                .setTimestamp(Instant.now().toEpochMilli())
                .build();

        PlatformProducer p = new PlatformProducer(bootstrap, sr, topic);
        p.sendCatalogUpdate(gameId, evt).get();
        System.out.println("Sent PlatformCatalogUpdateEvent gameId=" + gameId + " platform=" + platformId);
        p.close();
    }
}
