package tools.test;

import org.steamproject.events.GamePurchaseEvent;
import org.steamproject.service.PlayerService;
import org.steamproject.service.GameDataService;
import org.steamproject.model.Player;
import org.steamproject.model.Game;

import java.time.Instant;
import java.util.UUID;

/**
 * Simple main that sends one GamePurchaseEvent for testing.
 * Used by Gradle task `runTestEventProducer`.
 */
public class TestEventProducer {
    public static void main(String[] args) throws Exception {
        String bootstrap = System.getProperty("kafka.bootstrap", "localhost:9092");
        String sr = System.getProperty("schema.registry", "http://localhost:8081");
        String topic = System.getProperty("kafka.topic", "game-purchase-events");

        PlayerService ps = new PlayerService();
        GameDataService gs = new GameDataService();

        Player player = ps.getAllPlayers().get(0);
        Game game = gs.getAll().get(0);

        String purchaseId = UUID.randomUUID().toString();

        GamePurchaseEvent event = GamePurchaseEvent.newBuilder()
            .setEventId(UUID.randomUUID().toString())
                .setPurchaseId(purchaseId)
                .setGameId(game.getId())
                .setGameName(game.getName())
                .setPlayerId(player.getId())
                .setPlayerUsername(player.getUsername())
                .setPricePaid(9.99)
                .setPlatform(game.getPlatform() == null ? "" : game.getPlatform())
            .setPublisherId(game.getPublisher() == null ? "" : game.getPublisher())
                .setRegion(org.steamproject.events.SalesRegion.OTHER)
                .setTimestamp(Instant.now().toEpochMilli())
                .build();

        org.steamproject.infra.kafka.producer.PlayerProducer producer = new org.steamproject.infra.kafka.producer.PlayerProducer(bootstrap, sr, topic);
        producer.sendGamePurchase(player.getId(), event).get();
        System.out.println("Sent GamePurchaseEvent for player=" + player.getId());
        producer.close();
    }
}
