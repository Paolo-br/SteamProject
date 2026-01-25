package tools.test;

import org.steamproject.events.GamePurchaseEvent;
import org.steamproject.service.PlayerService;
import org.steamproject.service.GameDataService;
import org.steamproject.model.Player;
import org.steamproject.model.Game;

import java.time.Instant;
import java.util.UUID;

/**
 * Sends the same GamePurchaseEvent twice with the same eventId to validate dedup.
 */
public class TestDedupProducer {
    public static void main(String[] args) throws Exception {
        String bootstrap = System.getProperty("kafka.bootstrap", "localhost:9092");
        String sr = System.getProperty("schema.registry", "http://localhost:8081");
        String topic = System.getProperty("kafka.topic", "game-purchase-events");

        PlayerService ps = new PlayerService();
        GameDataService gs = new GameDataService();

        Player player = ps.getAllPlayers().get(0);
        Game game = gs.getAll().get(0);

        String purchaseId = UUID.randomUUID().toString();
        String fixedEventId = "dedup-test-" + UUID.randomUUID().toString();

        GamePurchaseEvent event = GamePurchaseEvent.newBuilder()
                .setEventId(fixedEventId)
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

        PlayerProducer producer = new PlayerProducer(bootstrap, sr, topic);

        System.out.println("Sending first event with eventId=" + fixedEventId);
        producer.sendGamePurchase(player.getId(), event).get();

        System.out.println("Sending duplicate event with same eventId=" + fixedEventId);
        producer.sendGamePurchase(player.getId(), event).get();

        producer.close();
        System.out.println("Done sending duplicate test events.");
    }
}
