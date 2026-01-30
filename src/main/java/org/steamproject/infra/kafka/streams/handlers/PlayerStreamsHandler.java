package org.steamproject.infra.kafka.streams.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.steamproject.infra.kafka.consumer.GameProjection;
import org.steamproject.infra.kafka.consumer.PlayerLibraryProjection;
import org.steamproject.infra.kafka.streams.PlayerStreamsProjection;
import org.steamproject.model.GameOwnership;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler HTTP pour les endpoints joueurs utilisant Kafka Streams.
 * Endpoints gérés:
 * - GET /api/players - Liste tous les joueurs (Kafka Streams)
 * - GET /api/players/{playerId}/library - Bibliothèque d'un joueur (Consumer classique)
 * - GET /api/players/{playerId}/sessions - Sessions d'un joueur (Kafka Streams)
 * - GET /api/players/{playerId}/reviews - Reviews d'un joueur (Kafka Streams)
 * - GET /api/players/{playerId}/crashes - Crashes d'un joueur (Kafka Streams)
 */
public class PlayerStreamsHandler implements HttpHandler {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        // GET /api/players - List all players (using Kafka Streams)
        if ("/api/players".equals(path) || "/api/players/".equals(path)) {
            var list = PlayerStreamsProjection.getAllPlayers();
            String response = mapper.writeValueAsString(list);
            sendJsonResponse(exchange, 200, response);
            return;
        }

        // GET /api/players/{playerId}/library (still using classic consumer)
        if (path.startsWith("/api/players/") && path.endsWith("/library")) {
            String[] parts = path.split("/");
            if (parts.length >= 5) {
                String playerId = parts[3];
                var library = PlayerLibraryProjection.getInstance().getLibrary(playerId);
                // Enrich ownership entries with distribution platform for UI library column
                List<Map<String, Object>> enriched = new ArrayList<>();
                for (Object o : library) {
                    try {
                        var go = (GameOwnership) o;
                        Map<String, Object> item = new HashMap<>();
                        item.put("gameId", go.gameId());
                        item.put("gameName", go.gameName());
                        item.put("purchaseDate", go.purchaseDate());
                        item.put("playtime", go.playtime());
                        item.put("pricePaid", go.pricePaid());
                        // lookup projection to obtain distributionPlatform
                        var gd = GameProjection.getInstance().getGame(go.gameId());
                        if (gd != null) {
                            Object dp = gd.getOrDefault("distributionPlatform", gd.getOrDefault("platform", null));
                            if (dp != null) item.put("platform", dp);
                        }
                        enriched.add(item);
                    } catch (Throwable t) { /* best-effort */ }
                }
                String response = mapper.writeValueAsString(enriched);
                sendJsonResponse(exchange, 200, response);
                return;
            }
        }

        // GET /api/players/{playerId}/sessions (using Kafka Streams)
        if (path.startsWith("/api/players/") && path.endsWith("/sessions")) {
            String[] parts = path.split("/");
            if (parts.length >= 5) {
                String playerId = parts[3];
                var sessions = PlayerStreamsProjection.getSessions(playerId);
                String response = mapper.writeValueAsString(sessions);
                sendJsonResponse(exchange, 200, response);
                return;
            }
        }

        // GET /api/players/{playerId}/reviews (using Kafka Streams)
        if (path.startsWith("/api/players/") && path.endsWith("/reviews")) {
            String[] parts = path.split("/");
            if (parts.length >= 5) {
                String playerId = parts[3];
                var reviews = PlayerStreamsProjection.getReviews(playerId);
                String response = mapper.writeValueAsString(reviews);
                sendJsonResponse(exchange, 200, response);
                return;
            }
        }

        // GET /api/players/{playerId}/crashes (using Kafka Streams)
        if (path.startsWith("/api/players/") && path.endsWith("/crashes")) {
            String[] parts = path.split("/");
            if (parts.length >= 5) {
                String playerId = parts[3];
                var crashes = PlayerStreamsProjection.getCrashes(playerId);
                String response = mapper.writeValueAsString(crashes);
                sendJsonResponse(exchange, 200, response);
                return;
            }
        }

        exchange.sendResponseHeaders(404, -1);
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
