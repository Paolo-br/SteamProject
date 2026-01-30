package org.steamproject.infra.kafka.streams.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.steamproject.events.GamePurchaseEvent;
import org.steamproject.events.SalesRegion;
import org.steamproject.infra.kafka.consumer.GameProjection;
import org.steamproject.infra.kafka.producer.GamePurchaseProducer;
import org.steamproject.infra.kafka.streams.PlayerStreamsProjection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handler HTTP pour la création d'achats utilisant Kafka Streams pour la validation.
 * Endpoint: POST /api/purchase
 * 
 * Utilise Kafka Streams pour valider que le joueur existe.
 * Utilise encore GameProjection pour valider le jeu (non migré).
 */
public class PurchaseStreamsHandler implements HttpHandler {
    private final String bootstrap;
    private final String schemaRegistry;
    private final String topic;
    private final ObjectMapper mapper = new ObjectMapper();

    public PurchaseStreamsHandler(String bootstrap, String schemaRegistry, String topic) {
        this.bootstrap = bootstrap;
        this.schemaRegistry = schemaRegistry;
        this.topic = topic;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String playerId = null;
        String gameId = null;
        double price = 0.0;

        // Try to read from query parameters first
        String query = exchange.getRequestURI().getQuery();
        if (query != null && !query.isEmpty()) {
            Map<String, String> params = new HashMap<>();
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length == 2) {
                    params.put(pair[0], URLDecoder.decode(pair[1], "UTF-8"));
                }
            }
            playerId = params.getOrDefault("playerId", params.get("id"));
            gameId = params.get("gameId");
            if (params.containsKey("price")) {
                try { price = Double.parseDouble(params.get("price")); } catch (Exception e) {}
            }
        }

        // Fallback to reading from JSON body if query params not provided
        if (playerId == null || gameId == null) {
            try (InputStream is = exchange.getRequestBody()) {
                var node = mapper.readTree(is);
                if (playerId == null) playerId = node.has("playerId") ? node.get("playerId").asText(null) : 
                                                  node.has("id") ? node.get("id").asText(null) : null;
                if (gameId == null) gameId = node.has("gameId") ? node.get("gameId").asText(null) : null;
                if (price == 0.0 && node.has("price") && !node.get("price").isNull()) {
                    price = node.get("price").asDouble(0.0);
                }
            }
        }

        if (playerId == null || gameId == null) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }

        try {
            // validate player exists in Kafka Streams projection
            var player = PlayerStreamsProjection.getPlayer(playerId);
            if (player == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            // validate game released in projection (still using classic consumer)
            var game = GameProjection.getInstance().getGame(gameId);
            if (game == null) {
                exchange.sendResponseHeaders(409, -1); // conflict: game not released
                return;
            }

            String gameName = game.getOrDefault("gameName", "") == null ? "" : game.getOrDefault("gameName", "").toString();
            String publisherId = game.getOrDefault("publisherId", null) == null ? null : game.getOrDefault("publisherId", null).toString();

            // Use the game's listed price as authoritative when available
            Object gp = game.getOrDefault("price", null);
            if (gp != null) {
                try {
                    if (gp instanceof Number) {
                        price = ((Number) gp).doubleValue();
                    } else {
                        price = Double.parseDouble(gp.toString());
                    }
                } catch (Exception e) {
                    // keep provided price on parse failure
                }
            }

            // Build GamePurchaseEvent
            GamePurchaseEvent evt = GamePurchaseEvent.newBuilder()
                    .setEventId(UUID.randomUUID().toString())
                    .setPurchaseId(UUID.randomUUID().toString())
                    .setGameId(gameId)
                    .setGameName(gameName)
                    .setPlayerId(playerId)
                    .setPlayerUsername(player.getOrDefault("username", "").toString())
                    .setPricePaid(price)
                    .setPlatform(game.getOrDefault("platform", "").toString())
                    .setPublisherId(publisherId)
                    .setRegion(SalesRegion.OTHER)
                    .setTimestamp(Instant.now().toEpochMilli())
                    .build();

            // send to Kafka
            GamePurchaseProducer prod = new GamePurchaseProducer(bootstrap, schemaRegistry, topic);
            try {
                prod.send(playerId, evt).get();
            } catch (Exception e) {
                prod.close();
                exchange.sendResponseHeaders(500, -1);
                return;
            }
            prod.close();

            // respond with created
            ObjectNode resp = mapper.createObjectNode();
            resp.put("status", "sent");
            resp.put("playerId", playerId);
            resp.put("gameId", gameId);
            sendJsonResponse(exchange, 201, mapper.writeValueAsString(resp));
            
        } catch (Exception ex) {
            ex.printStackTrace();
            exchange.sendResponseHeaders(500, -1);
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
