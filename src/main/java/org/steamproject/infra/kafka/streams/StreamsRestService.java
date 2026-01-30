package org.steamproject.infra.kafka.streams;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.steamproject.infra.kafka.streams.handlers.PatchesStreamsHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Service REST unifié pour interroger tous les state stores Kafka Streams.
 * 
 * Endpoints disponibles:
 * - GET /api/library/{playerId} - Bibliothèque d'un joueur (UserLibraryStreams)
 * - GET /api/publishers/{publisherId}/games - Jeux d'un éditeur (PublisherGamesStreams)
 * - GET /api/platforms/{platformId}/catalog - Catalogue d'une plateforme (PlatformCatalogStreams)
 * - GET /api/publishers-list - Liste de tous les éditeurs
 * - GET /api/catalog - Catalogue complet
 * - GET /api/patches - Tous les patches (GamePatchesStreams)
 * - GET /api/patches/{gameId} - Patches d'un jeu
 * - GET /api/patches/{gameId}/latest - Dernier patch
 * - GET /api/games/{gameId}/version - Version actuelle d'un jeu
 */
public class StreamsRestService {
    private static KafkaStreams libraryStreams;
    private static KafkaStreams publisherStreams;
    private static KafkaStreams platformStreams;
    private static KafkaStreams patchesStreams;
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        // Démarrer les streams
        System.out.println("Starting Kafka Streams...");
        
        libraryStreams = UserLibraryStreams.startStreams();
        publisherStreams = PublisherGamesStreams.startStreams();
        platformStreams = PlatformCatalogStreams.startStreams();
        patchesStreams = GamePatchesStreams.startStreams();
        
        // Attendre que les stores soient prêts
        waitForStoreReady(libraryStreams, UserLibraryStreams.STORE_NAME);
        waitForStoreReady(publisherStreams, PublisherGamesStreams.STORE_NAME);
        waitForStoreReady(platformStreams, PlatformCatalogStreams.STORE_NAME);
        waitForStoreReady(patchesStreams, GamePatchesStreams.STORE_NAME);
        
        // Créer le serveur HTTP
        int port = Integer.getInteger("http.port", 8082);  // Port différent de PurchaseRestService
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // Endpoints
        server.createContext("/api/library", new PlayerLibraryHandler());  // UserLibraryStreams
        server.createContext("/api/publishers", new PublisherHandler());   // PublisherGamesStreams
        server.createContext("/api/platforms", new PlatformHandler());     // PlatformCatalogStreams
        server.createContext("/api/publishers-list", new PublishersListHandler());
        server.createContext("/api/catalog", new CatalogHandler());
        server.createContext("/api/patches", new PatchesStreamsHandler()); // GamePatchesStreams
        server.createContext("/api/games", new PatchesStreamsHandler());   // GamePatchesStreams (alias)
        
        server.setExecutor(null);
        server.start();
        
        System.out.println("=".repeat(60));
        System.out.println("✅ Streams REST Service listening on http://localhost:" + port);
        System.out.println("=".repeat(60));
        System.out.println("Endpoints disponibles:");
        System.out.println("  - GET /api/library/{playerId}           (UserLibraryStreams)");
        System.out.println("  - GET /api/publishers/{publisherId}/games (PublisherGamesStreams)");
        System.out.println("  - GET /api/platforms/{platformId}/catalog (PlatformCatalogStreams)");
        System.out.println("  - GET /api/publishers-list");
        System.out.println("  - GET /api/catalog");
        System.out.println("  - GET /api/patches                      (GamePatchesStreams)");
        System.out.println("  - GET /api/patches/{gameId}");
        System.out.println("  - GET /api/patches/{gameId}/latest");
        System.out.println("  - GET /api/games/{gameId}/version");
        System.out.println("=".repeat(60));
    }

    private static void waitForStoreReady(KafkaStreams streams, String storeName) {
        int attempts = 0;
        while (attempts < 30) {
            try {
                streams.store(StoreQueryParameters.fromNameAndType(storeName, QueryableStoreTypes.keyValueStore()));
                System.out.println("✅ Store ready: " + storeName);
                return;
            } catch (Exception ex) {
                attempts++;
                try { Thread.sleep(1000); } catch (InterruptedException e) {}
            }
        }
        System.err.println("⚠️  Store not ready after 30s: " + storeName);
    }

    // ==================== LIBRARY HANDLER (UserLibraryStreams) ====================
    
    static class PlayerLibraryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            
            // GET /api/library/{playerId}
            if (parts.length >= 4) {
                String playerId = parts[3];
                try {
                    ReadOnlyKeyValueStore<String, String> store = libraryStreams.store(
                        StoreQueryParameters.fromNameAndType(UserLibraryStreams.STORE_NAME, QueryableStoreTypes.keyValueStore())
                    );
                    String response = store.get(playerId);
                    if (response == null) response = "[]";
                    sendJsonResponse(exchange, 200, response);
                } catch (Exception e) {
                    sendJsonResponse(exchange, 200, "[]");
                }
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        }
    }

    // ==================== PUBLISHER HANDLER (PublisherGamesStreams) ====================
    
    static class PublisherHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");

            // GET /api/publishers/{publisherId}/games
            if (parts.length >= 5 && "games".equals(parts[4])) {
                String publisherId = parts[3];
                
                try {
                    ReadOnlyKeyValueStore<String, String> store = 
                        publisherStreams.store(StoreQueryParameters.fromNameAndType(
                            PublisherGamesStreams.STORE_NAME, 
                            QueryableStoreTypes.keyValueStore()
                        ));
                    
                    String gamesJson = store.get(publisherId);
                    
                    if (gamesJson == null || "[]".equals(gamesJson)) {
                        sendJsonResponse(exchange, 200, "[]");
                        return;
                    }
                    
                    // Parser le JSON et enrichir
                    List<Map<String, Object>> games = new ArrayList<>();
                    String[] gameEntries = mapper.readValue(gamesJson, String[].class);
                    
                    for (String entry : gameEntries) {
                        String[] parts2 = entry.split("\\|", 3);
                        Map<String, Object> game = new HashMap<>();
                        game.put("gameId", parts2.length > 0 ? parts2[0] : null);
                        game.put("gameName", parts2.length > 1 ? parts2[1] : null);
                        if (parts2.length > 2 && !parts2[2].isEmpty()) {
                            try {
                                game.put("releaseYear", Integer.parseInt(parts2[2]));
                            } catch (NumberFormatException e) {
                                game.put("releaseYear", null);
                            }
                        }
                        games.add(game);
                    }
                    
                    String response = mapper.writeValueAsString(games);
                    sendJsonResponse(exchange, 200, response);
                    
                } catch (Exception e) {
                    e.printStackTrace();
                    sendJsonResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
                }
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        }
    }

    // ==================== PLATFORM HANDLER (PlatformCatalogStreams) ====================
    
    static class PlatformHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");

            // GET /api/platforms/{platformId}/catalog
            if (parts.length >= 5 && "catalog".equals(parts[4])) {
                String platformId = parts[3];
                
                try {
                    ReadOnlyKeyValueStore<String, String> store = 
                        platformStreams.store(StoreQueryParameters.fromNameAndType(
                            PlatformCatalogStreams.STORE_NAME, 
                            QueryableStoreTypes.keyValueStore()
                        ));
                    
                    String catalogJson = store.get(platformId);
                    
                    if (catalogJson == null || "[]".equals(catalogJson)) {
                        sendJsonResponse(exchange, 200, "[]");
                        return;
                    }
                    
                    // Parser et formatter
                    List<Map<String, Object>> catalog = new ArrayList<>();
                    String[] gameEntries = mapper.readValue(catalogJson, String[].class);
                    
                    for (String entry : gameEntries) {
                        String[] parts2 = entry.split("\\|", 3);
                        Map<String, Object> game = new HashMap<>();
                        game.put("gameId", parts2.length > 0 ? parts2[0] : null);
                        game.put("gameName", parts2.length > 1 ? parts2[1] : null);
                        if (parts2.length > 2 && !parts2[2].isEmpty()) {
                            try {
                                game.put("releaseYear", Integer.parseInt(parts2[2]));
                            } catch (NumberFormatException e) {
                                game.put("releaseYear", null);
                            }
                        }
                        catalog.add(game);
                    }
                    
                    String response = mapper.writeValueAsString(catalog);
                    sendJsonResponse(exchange, 200, response);
                    
                } catch (Exception e) {
                    e.printStackTrace();
                    sendJsonResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
                }
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        }
    }

    // ==================== PUBLISHERS LIST HANDLER ====================
    
    static class PublishersListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                ReadOnlyKeyValueStore<String, String> store = 
                    publisherStreams.store(StoreQueryParameters.fromNameAndType(
                        PublisherGamesStreams.STORE_NAME, 
                        QueryableStoreTypes.keyValueStore()
                    ));
                
                Map<String, Integer> publishers = new HashMap<>();
                
                try (KeyValueIterator<String, String> iterator = store.all()) {
                    while (iterator.hasNext()) {
                        var kv = iterator.next();
                        String publisherId = kv.key;
                        String gamesJson = kv.value;
                        
                        try {
                            String[] games = mapper.readValue(gamesJson, String[].class);
                            publishers.put(publisherId, games.length);
                        } catch (Exception e) {
                            publishers.put(publisherId, 0);
                        }
                    }
                }
                
                String response = mapper.writeValueAsString(publishers);
                sendJsonResponse(exchange, 200, response);
                
            } catch (Exception e) {
                e.printStackTrace();
                sendJsonResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    // ==================== CATALOG HANDLER ====================
    
    static class CatalogHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                ReadOnlyKeyValueStore<String, String> platformStore = 
                    platformStreams.store(StoreQueryParameters.fromNameAndType(
                        PlatformCatalogStreams.STORE_NAME, 
                        QueryableStoreTypes.keyValueStore()
                    ));
                
                ReadOnlyKeyValueStore<String, String> publisherStore = 
                    publisherStreams.store(StoreQueryParameters.fromNameAndType(
                        PublisherGamesStreams.STORE_NAME, 
                        QueryableStoreTypes.keyValueStore()
                    ));
                
                List<Map<String, Object>> catalog = new ArrayList<>();
                Set<String> seenGameIds = new HashSet<>();
                
                // 1. Ajouter les jeux des platforms
                try (KeyValueIterator<String, String> iterator = platformStore.all()) {
                    while (iterator.hasNext()) {
                        var kv = iterator.next();
                        String platformId = kv.key;
                        String gamesJson = kv.value;
                        
                        try {
                            String[] games = mapper.readValue(gamesJson, String[].class);
                            for (String entry : games) {
                                String[] parts = entry.split("\\|", 3);
                                String gameId = parts.length > 0 ? parts[0] : null;
                                
                                if (gameId != null && !seenGameIds.contains(gameId)) {
                                    Map<String, Object> game = new HashMap<>();
                                    game.put("gameId", gameId);
                                    game.put("gameName", parts.length > 1 ? parts[1] : null);
                                    if (parts.length > 2 && !parts[2].isEmpty()) {
                                        try {
                                            game.put("releaseYear", Integer.parseInt(parts[2]));
                                        } catch (NumberFormatException e) {}
                                    }
                                    game.put("platform", platformId);
                                    catalog.add(game);
                                    seenGameIds.add(gameId);
                                }
                            }
                        } catch (Exception e) {}
                    }
                }
                
                // 2. Ajouter les jeux des publishers non encore présents
                try (KeyValueIterator<String, String> iterator = publisherStore.all()) {
                    while (iterator.hasNext()) {
                        var kv = iterator.next();
                        String publisherId = kv.key;
                        String gamesJson = kv.value;
                        
                        try {
                            String[] games = mapper.readValue(gamesJson, String[].class);
                            for (String entry : games) {
                                String[] parts = entry.split("\\|", 3);
                                String gameId = parts.length > 0 ? parts[0] : null;
                                
                                if (gameId != null && !seenGameIds.contains(gameId)) {
                                    Map<String, Object> game = new HashMap<>();
                                    game.put("gameId", gameId);
                                    game.put("gameName", parts.length > 1 ? parts[1] : null);
                                    if (parts.length > 2 && !parts[2].isEmpty()) {
                                        try {
                                            game.put("releaseYear", Integer.parseInt(parts[2]));
                                        } catch (NumberFormatException e) {}
                                    }
                                    game.put("publisherId", publisherId);
                                    catalog.add(game);
                                    seenGameIds.add(gameId);
                                }
                            }
                        } catch (Exception e) {}
                    }
                }
                
                String response = mapper.writeValueAsString(catalog);
                sendJsonResponse(exchange, 200, response);
                
            } catch (Exception e) {
                e.printStackTrace();
                sendJsonResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    // ==================== HELPER ====================
    
    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
