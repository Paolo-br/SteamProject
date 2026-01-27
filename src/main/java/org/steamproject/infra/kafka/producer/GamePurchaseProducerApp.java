package org.steamproject.infra.kafka.producer;

import org.steamproject.events.GamePurchaseEvent;
import org.steamproject.events.SalesRegion;

import java.time.Instant;
import java.util.UUID;


public class GamePurchaseProducerApp {
    public static void main(String[] args) throws Exception {
        String bootstrap = System.getProperty("kafka.bootstrap", "localhost:9092");
        String sr = System.getProperty("schema.registry", "http://localhost:8081");
        String topic = System.getProperty("kafka.topic", "game-purchase-events");

        String playerId = System.getProperty("test.player.id", System.getenv("TEST_PLAYER_ID"));
        if (playerId == null || playerId.isEmpty()) playerId = UUID.randomUUID().toString();

        String gameId = System.getProperty("test.game.id", UUID.randomUUID().toString());
        String gameName = System.getProperty("test.game.name", "RealGame");

        GamePurchaseEvent purchase = GamePurchaseEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setPurchaseId(UUID.randomUUID().toString())
                .setGameId(gameId)
                .setGameName(gameName)
                .setPlayerId(playerId)
                .setPlayerUsername(System.getProperty("test.player.username", "real_player"))
                .setPricePaid(Double.parseDouble(System.getProperty("test.price", "9.99")))
                .setPlatform(System.getProperty("test.platform", "PC"))
                .setPublisherId(System.getProperty("test.publisher.id", "pub-real-1"))
                .setRegion(SalesRegion.OTHER)
                .setTimestamp(Instant.now().toEpochMilli())
                .build();

        GamePurchaseProducer prod = new GamePurchaseProducer(bootstrap, sr, topic);
        prod.send(playerId, purchase).get();
        System.out.println("Sent GamePurchaseEvent player=" + playerId + " game=" + gameId);
        prod.close();
    }
}
