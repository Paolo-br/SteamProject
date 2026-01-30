package org.steamproject.infra.kafka.streams.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.steamproject.infra.kafka.streams.GamePatchesStreams;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Handler HTTP pour les endpoints patches utilisant Kafka Streams.
 * Endpoints gérés:
 * - GET /api/patches - Liste tous les patches de tous les jeux
 * - GET /api/patches/{gameId} - Patches d'un jeu spécifique
 * - GET /api/patches/{gameId}/latest - Dernier patch d'un jeu
 * - GET /api/games/{gameId}/patches - Patches d'un jeu (alternative)
 * - GET /api/games/{gameId}/version - Version actuelle d'un jeu
 */
public class PatchesStreamsHandler implements HttpHandler {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        // GET /api/patches - Liste tous les patches
        if ("/api/patches".equals(path) || "/api/patches/".equals(path)) {
            var allPatches = GamePatchesStreams.getAllPatches();
            String response = mapper.writeValueAsString(allPatches);
            sendJsonResponse(exchange, 200, response);
            return;
        }

        // GET /api/patches/{gameId}/latest - Dernier patch d'un jeu
        if (path.startsWith("/api/patches/") && path.endsWith("/latest")) {
            String[] parts = path.split("/");
            if (parts.length >= 5) {
                String gameId = parts[3];
                var latest = GamePatchesStreams.getLatestPatch(gameId);
                if (latest == null) {
                    sendJsonResponse(exchange, 404, "{\"error\":\"No patches found for game\"}");
                } else {
                    String response = mapper.writeValueAsString(latest);
                    sendJsonResponse(exchange, 200, response);
                }
                return;
            }
        }

        // GET /api/patches/{gameId} - Patches d'un jeu spécifique
        if (path.startsWith("/api/patches/")) {
            String[] parts = path.split("/");
            if (parts.length >= 4) {
                String gameId = parts[3];
                var patches = GamePatchesStreams.getPatches(gameId);
                String response = mapper.writeValueAsString(patches);
                sendJsonResponse(exchange, 200, response);
                return;
            }
        }

        // GET /api/games/{gameId}/patches - Patches via endpoint games
        if (path.startsWith("/api/games/") && path.endsWith("/patches")) {
            String[] parts = path.split("/");
            if (parts.length >= 5) {
                String gameId = parts[3];
                var patches = GamePatchesStreams.getPatches(gameId);
                String response = mapper.writeValueAsString(patches);
                sendJsonResponse(exchange, 200, response);
                return;
            }
        }

        // GET /api/games/{gameId}/version - Version actuelle d'un jeu
        if (path.startsWith("/api/games/") && path.endsWith("/version")) {
            String[] parts = path.split("/");
            if (parts.length >= 5) {
                String gameId = parts[3];
                String version = GamePatchesStreams.getCurrentVersion(gameId);
                if (version == null) {
                    sendJsonResponse(exchange, 404, "{\"error\":\"No version found for game\"}");
                } else {
                    sendJsonResponse(exchange, 200, "{\"gameId\":\"" + gameId + "\",\"currentVersion\":\"" + version + "\"}");
                }
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
