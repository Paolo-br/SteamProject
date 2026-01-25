package org.steamproject.infra.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class PurchaseRestService {
    public static void main(String[] args) throws Exception {
        // Start a PlayerConsumer in this same JVM so the in-memory projection
        // is populated and the REST endpoint can read it.
        String bootstrap = System.getProperty("kafka.bootstrap", "localhost:9092");
        String sr = System.getProperty("schema.registry", "http://localhost:8081");
        String topic = System.getProperty("kafka.topic", "game-purchase-events");
        String group = System.getProperty("kafka.group", "player-consumer-group-rest");
        Thread playerThread = new Thread(() -> {
            try {
                PlayerConsumer pc = new PlayerConsumer(bootstrap, sr, topic, group);
                pc.start();
            } catch (Throwable t) { t.printStackTrace(); }
        }, "player-consumer-thread");
        playerThread.setDaemon(true);
        playerThread.start();

        // Start PublisherConsumer in same JVM so PublisherProjection is populated
        String pubTopic = System.getProperty("kafka.topic.pub", "game-released-events");
        String pubGroup = System.getProperty("kafka.group.pub", "publisher-consumer-group-rest");
        Thread publisherThread = new Thread(() -> {
            try {
                PublisherConsumer pcons = new PublisherConsumer(bootstrap, sr, pubTopic, pubGroup);
                pcons.start();
            } catch (Throwable t) { t.printStackTrace(); }
        }, "publisher-consumer-thread");
        publisherThread.setDaemon(true);
        publisherThread.start();

        // Start PlatformConsumer in same JVM so PlatformProjection is populated
        String platTopic = System.getProperty("kafka.topic.platform", "platform-catalog-events");
        String platGroup = System.getProperty("kafka.group.platform", "platform-consumer-group-rest");
        Thread platformThread = new Thread(() -> {
            try {
                PlatformConsumer plcons = new PlatformConsumer(bootstrap, sr, platTopic, platGroup);
                plcons.start();
            } catch (Throwable t) { t.printStackTrace(); }
        }, "platform-consumer-thread");
        platformThread.setDaemon(true);
        platformThread.start();

        // Start PlayerCreatedConsumer in same JVM so PlayerProjection is populated
        String playerCreatedTopic = System.getProperty("kafka.topic.player", "player-created-events");
        String playerCreatedGroup = System.getProperty("kafka.group.player", "player-created-consumer-group-rest");
        Thread playerCreatedThread = new Thread(() -> {
            try {
                PlayerCreatedConsumer pc = new PlayerCreatedConsumer(bootstrap, sr, playerCreatedTopic, playerCreatedGroup);
                pc.start();
            } catch (Throwable t) { t.printStackTrace(); }
        }, "player-created-consumer-thread");
        playerCreatedThread.setDaemon(true);
        playerCreatedThread.start();

        int port = Integer.getInteger("http.port", 8080);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/players", new PlayersHandler());
        server.createContext("/api/publishers", new PublisherHandler());
        server.createContext("/api/platforms", new PlatformHandler());
        server.createContext("/api/publishers-list", new PublishersListHandler());
        server.createContext("/api/catalog", new CatalogHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Purchase REST service listening on http://localhost:" + port + "/api/players/{playerId}/library");
    }

    static class PlayersHandler implements HttpHandler {
        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            // GET /api/players or /api/players/ -> list known players
            if ("/api/players".equals(path) || "/api/players/".equals(path)) {
                var list = org.steamproject.infra.kafka.consumer.PlayerProjection.getInstance().list();
                String response = mapper.writeValueAsString(list);
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
                return;
            }

            // Expect: /api/players/{playerId}/library
            if (path.startsWith("/api/players/") && path.endsWith("/library")) {
                String[] parts = path.split("/");
                if (parts.length >= 5) {
                    String playerId = parts[3];
                    var library = PlayerLibraryProjection.getInstance().getLibrary(playerId);
                    String response = mapper.writeValueAsString(library);
                    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
                    return;
                }
            }

            exchange.sendResponseHeaders(404, -1);
        }
    }

    static class PublisherHandler implements HttpHandler {
        private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException {
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            // Expect: /api/publishers/{publisherId}/games
            if (parts.length >= 5 && "games".equals(parts[4])) {
                String publisherId = parts[3];
                java.util.List<String> entries = org.steamproject.infra.kafka.consumer.PublisherProjection.getInstance().getPublishedGames(publisherId);
                // entries are stored as "gameId|gameName|releaseYear"
                java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
                for (String e : entries) {
                    String[] p = e.split("\\|", 3);
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("gameId", p.length > 0 ? p[0] : null);
                    m.put("gameName", p.length > 1 ? p[1] : null);
                    m.put("releaseYear", p.length > 2 ? Integer.parseInt(p[2]) : null);
                    out.add(m);
                }
                String response = mapper.writeValueAsString(out);
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (java.io.OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        }
    }

    static class PlatformHandler implements HttpHandler {
        private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException {
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            // Expect: /api/platforms/{platformId}/catalog
            if (parts.length >= 5 && "catalog".equals(parts[4])) {
                String platformId = parts[3];
                java.util.List<String> entries = org.steamproject.infra.kafka.consumer.PlatformProjection.getInstance().getCatalog(platformId);
                java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
                for (String e : entries) {
                    String[] p = e.split("\\|", 2);
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("gameId", p.length > 0 ? p[0] : null);
                    m.put("gameName", p.length > 1 ? p[1] : null);
                    out.add(m);
                }
                String response = mapper.writeValueAsString(out);
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (java.io.OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        }
    }

    /**
     * Returns a JSON map of publisherId -> published game count.
     */
    static class PublishersListHandler implements HttpHandler {
        private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException {
            var snapshot = org.steamproject.infra.kafka.consumer.PublisherProjection.getInstance().snapshot();
            java.util.Map<String, Integer> out = new java.util.HashMap<>();

            // Start from ingestion-known publishers so all publishers exist from the start
            try {
                var ingestion = new org.steamproject.ingestion.PublisherIngestion();
                java.util.List<org.steamproject.model.Publisher> ing = ingestion.readAll();
                for (var p : ing) {
                    if (p.getId() != null) out.put(p.getId(), 0);
                }
            } catch (Exception e) {
                // ignore ingestion failures and continue with projection snapshot
            }

            // Merge counts from projection (overwrite zeros with actual counts)
            snapshot.forEach((k, v) -> out.put(k, v.size()));
            String response = mapper.writeValueAsString(out);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (java.io.OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        }
    }

    /**
     * Returns a JSON array of catalog entries across all platforms.
     * Each entry: { gameId, gameName, platform, publisherId?, releaseYear? }
     */
    static class CatalogHandler implements HttpHandler {
        private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException {
            // Aggregate platform catalogs and publisher summaries
            var platSnap = org.steamproject.infra.kafka.consumer.PlatformProjection.getInstance().snapshot();
            var pubSnap = org.steamproject.infra.kafka.consumer.PublisherProjection.getInstance().snapshot();

            java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
            platSnap.forEach((platformId, entries) -> {
                for (String e : entries) {
                    String[] p = e.split("\\|", 4);
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("gameId", p.length > 0 ? p[0] : null);
                    m.put("gameName", p.length > 1 ? p[1] : null);
                    m.put("releaseYear", p.length > 2 ? (p[2].isEmpty() ? null : Integer.parseInt(p[2])) : null);
                    m.put("platform", platformId);
                    // try to find publisherId from publisher snapshot (cheap linear search)
                    String publisherId = null;
                    for (var entry : pubSnap.entrySet()) {
                        for (String g : entry.getValue()) {
                            if (g.startsWith(p.length>0?p[0]:"")) { publisherId = entry.getKey(); break; }
                        }
                        if (publisherId != null) break;
                    }
                    m.put("publisherId", publisherId);
                    out.add(m);
                }
            });

            // Also include games present in PublisherProjection in case platform catalogs
            // were not emitted. This ensures a released game becomes visible in the
            // aggregate catalog even if no PlatformCatalogUpdateEvent was sent.
            for (var entry : pubSnap.entrySet()) {
                String pubId = entry.getKey();
                for (String g : entry.getValue()) {
                    // g format: gameId|gameName|releaseYear
                    String[] p = g.split("\\|", 3);
                    String gid = p.length>0? p[0] : null;
                    // Skip if already included by platform snapshot
                    boolean exists = out.stream().anyMatch(map -> gid != null && gid.equals(map.get("gameId")));
                    if (exists) continue;
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("gameId", gid);
                    m.put("gameName", p.length>1? p[1] : null);
                    m.put("releaseYear", p.length>2 && !p[2].isEmpty() ? Integer.parseInt(p[2]) : null);
                    m.put("platform", null);
                    m.put("publisherId", pubId);
                    out.add(m);
                }
            }

            String response = mapper.writeValueAsString(out);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (java.io.OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        }
    }
}
