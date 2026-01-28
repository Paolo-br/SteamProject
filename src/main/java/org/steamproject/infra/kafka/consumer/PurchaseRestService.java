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

        String bootstrap = System.getProperty("kafka.bootstrap", "localhost:9092");
        String sr = System.getProperty("schema.registry", "http://localhost:8081");
        String purchaseTopic = System.getProperty("kafka.topic", "game-purchase-events");
        String extraPlayerTopics = System.getProperty("kafka.topic.player.events", "dlc-purchase-events,game-session-events,crash-report-events,new-rating-events,review-published-events,review-voted-events,player-created-events");
        String playerTopics = purchaseTopic + "," + extraPlayerTopics;
        String group = System.getProperty("kafka.group", "player-consumer-group-rest");
        Thread playerThread = new Thread(() -> {
            try {
                PlayerConsumer pc = new PlayerConsumer(bootstrap, sr, playerTopics, group);
                pc.start();
            } catch (Throwable t) { t.printStackTrace(); }
        }, "player-consumer-thread");
        playerThread.setDaemon(true);
        playerThread.start();

        String pubTopic = System.getProperty("kafka.topic.pub", "game-released-events,game-published-events,game-updated-events,patch-published-events,dlc-published-events,game-version-deprecated-events,editor-responded-events");
        String pubGroup = System.getProperty("kafka.group.pub", "publisher-consumer-group-rest");
        Thread publisherThread = new Thread(() -> {
            try {
                PublisherConsumer pcons = new PublisherConsumer(bootstrap, sr, pubTopic, pubGroup);
                pcons.start();
            } catch (Throwable t) { t.printStackTrace(); }
        }, "publisher-consumer-thread");
        publisherThread.setDaemon(true);
        publisherThread.start();

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

        // Player creation events are handled by the consolidated PlayerConsumer
        // (PlayerConsumer started above) which also updates PlayerProjection.

        int port = Integer.getInteger("http.port", 8080);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/players", new PlayersHandler());
        server.createContext("/api/purchase", new PurchaseCreateHandler(bootstrap, sr, purchaseTopic));
        server.createContext("/api/purchases", new PurchasesHandler());
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

            if ("/api/players".equals(path) || "/api/players/".equals(path)) {
                var list = org.steamproject.infra.kafka.consumer.PlayerProjection.getInstance().list();
                String response = mapper.writeValueAsString(list);
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
                return;
            }

            if (path.startsWith("/api/players/") && path.endsWith("/library")) {
                String[] parts = path.split("/");
                if (parts.length >= 5) {
                    String playerId = parts[3];
                    var library = PlayerLibraryProjection.getInstance().getLibrary(playerId);
                    // Enrich ownership entries with distribution platform for UI library column
                    java.util.List<java.util.Map<String,Object>> enriched = new java.util.ArrayList<>();
                    for (Object o : library) {
                        try {
                            var go = (org.steamproject.model.GameOwnership) o;
                            java.util.Map<String,Object> item = new java.util.HashMap<>();
                            item.put("gameId", go.gameId());
                            item.put("gameName", go.gameName());
                            item.put("purchaseDate", go.purchaseDate());
                            item.put("playtime", go.playtime());
                            item.put("pricePaid", go.pricePaid());
                            // lookup projection to obtain distributionPlatform
                            var gd = org.steamproject.infra.kafka.consumer.GameProjection.getInstance().getGame(go.gameId());
                            if (gd != null) {
                                Object dp = gd.getOrDefault("distributionPlatform", gd.getOrDefault("platform", null));
                                if (dp != null) item.put("platform", dp);
                            }
                            enriched.add(item);
                        } catch (Throwable t) { /* best-effort */ }
                    }
                    String response = mapper.writeValueAsString(enriched);
                    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
                    return;
                }
            }

            if (path.startsWith("/api/players/") && path.endsWith("/sessions")) {
                String[] parts = path.split("/");
                if (parts.length >= 5) {
                    String playerId = parts[3];
                    var sessions = org.steamproject.infra.kafka.consumer.PlayerProjection.getInstance().snapshotSessions().get(playerId);
                    String response = mapper.writeValueAsString(sessions == null ? java.util.Collections.emptyList() : sessions);
                    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
                    return;
                }
            }

            if (path.startsWith("/api/players/") && path.endsWith("/reviews")) {
                String[] parts = path.split("/");
                if (parts.length >= 5) {
                    String playerId = parts[3];
                    var reviews = org.steamproject.infra.kafka.consumer.PlayerProjection.getInstance().snapshotReviews().get(playerId);
                    String response = mapper.writeValueAsString(reviews == null ? java.util.Collections.emptyList() : reviews);
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

    static class PurchaseCreateHandler implements HttpHandler {
        private final String bootstrap;
        private final String schemaRegistry;
        private final String topic;
        private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        public PurchaseCreateHandler(String bootstrap, String schemaRegistry, String topic) {
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

            try (java.io.InputStream is = exchange.getRequestBody()) {
                var node = mapper.readTree(is);
                String playerId = node.has("playerId") ? node.get("playerId").asText(null) : null;
                String gameId = node.has("gameId") ? node.get("gameId").asText(null) : null;
                double price = node.has("price") && !node.get("price").isNull() ? node.get("price").asDouble(0.0) : 0.0;

                if (playerId == null || gameId == null) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }

                // validate player exists in projection
                var players = org.steamproject.infra.kafka.consumer.PlayerProjection.getInstance().snapshot();
                if (!players.containsKey(playerId)) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }

                // validate game released in projection
                var game = org.steamproject.infra.kafka.consumer.GameProjection.getInstance().getGame(gameId);
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
                org.steamproject.events.GamePurchaseEvent evt = org.steamproject.events.GamePurchaseEvent.newBuilder()
                        .setEventId(java.util.UUID.randomUUID().toString())
                        .setPurchaseId(java.util.UUID.randomUUID().toString())
                        .setGameId(gameId)
                        .setGameName(gameName)
                        .setPlayerId(playerId)
                        .setPlayerUsername(players.get(playerId).getOrDefault("username", "").toString())
                        .setPricePaid(price)
                        .setPlatform(game.getOrDefault("platform", "").toString())
                        .setPublisherId(publisherId)
                        .setRegion(org.steamproject.events.SalesRegion.OTHER)
                        .setTimestamp(java.time.Instant.now().toEpochMilli())
                        .build();

                // send to Kafka
                org.steamproject.infra.kafka.producer.GamePurchaseProducer prod = new org.steamproject.infra.kafka.producer.GamePurchaseProducer(bootstrap, schemaRegistry, topic);
                try {
                    prod.send(playerId, evt).get();
                } catch (Exception e) {
                    prod.close();
                    exchange.sendResponseHeaders(500, -1);
                    return;
                }
                prod.close();

                // respond with created
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                var resp = mapper.createObjectNode();
                resp.put("status", "sent");
                resp.put("playerId", playerId);
                resp.put("gameId", gameId);
                byte[] bytes = mapper.writeValueAsBytes(resp);
                exchange.sendResponseHeaders(201, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
            } catch (Exception ex) {
                ex.printStackTrace();
                exchange.sendResponseHeaders(500, -1);
            }
        }
    }

    static class PurchasesHandler implements HttpHandler {
        private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Return flattened list of purchases across players
            var snapshot = PlayerLibraryProjection.getInstance().snapshot();
            java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
            snapshot.forEach((playerId, list) -> {
                for (Object o : list) {
                    try {
                        // java record org.steamproject.model.GameOwnership
                        var go = (org.steamproject.model.GameOwnership) o;
                        java.util.Map<String, Object> m = new java.util.HashMap<>();
                        m.put("playerId", playerId);
                        m.put("gameId", go.gameId());
                        m.put("gameName", go.gameName());
                        m.put("purchaseDate", go.purchaseDate());
                        m.put("pricePaid", go.pricePaid() == null ? 0.0 : go.pricePaid());
                        out.add(m);
                    } catch (Throwable t) {
                        // best-effort; ignore malformed entries
                    }
                }
            });

            String response = mapper.writeValueAsString(out);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (java.io.OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        }
    }

    static class PublisherHandler implements HttpHandler {
        private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException {
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");

            if ("/api/publishers".equals(path) || "/api/publishers/".equals(path)) {
                try {
                    var ingestion = new org.steamproject.ingestion.PublisherIngestion();
                    java.util.List<org.steamproject.model.Publisher> ing = ingestion.readAll();
                    String response = mapper.writeValueAsString(ing);
                    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                    byte[] bytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (java.io.OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
                } catch (Exception e) {
                    exchange.sendResponseHeaders(500, -1);
                }
                return;
            }

            if (parts.length >= 5 && "games".equals(parts[4])) {
                String publisherId = parts[3];
                java.util.List<String> entries = org.steamproject.infra.kafka.consumer.PublisherProjection.getInstance().getPublishedGames(publisherId);
                java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
                for (String e : entries) {
                    String[] p = e.split("\\|", 3);
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    String gid = p.length > 0 ? p[0] : null;
                    m.put("gameId", gid);
                    m.put("gameName", p.length > 1 ? p[1] : null);
                    m.put("releaseYear", p.length > 2 && !p[2].isEmpty() ? Integer.parseInt(p[2]) : null);
                    // attempt to enrich with game projection details (price, platform, genre, and ratings)
                    try {
                        var gd = org.steamproject.infra.kafka.consumer.GameProjection.getInstance().getGame(gid);
                        if (gd != null) {
                            if (gd.get("genre") != null) m.put("genre", gd.get("genre"));
                            if (gd.get("console") != null) m.put("console", gd.get("console"));
                            if (gd.get("platform") != null) m.put("platform", gd.get("platform"));
                            if (gd.get("price") != null) m.put("price", gd.get("price"));
                            if (gd.get("initialVersion") != null) m.put("initialVersion", gd.get("initialVersion"));
                            if (gd.get("versions") != null) m.put("versions", gd.get("versions"));
                            if (gd.get("patches") != null) m.put("patches", gd.get("patches"));
                            if (gd.get("dlcs") != null) m.put("dlcs", gd.get("dlcs"));
                            if (gd.get("deprecatedVersions") != null) m.put("deprecatedVersions", gd.get("deprecatedVersions"));
                            if (gd.get("incidentResponses") != null) m.put("incidentResponses", gd.get("incidentResponses"));
                            if (gd.get("incidentCount") != null) m.put("incidentCount", gd.get("incidentCount"));
                            // enrich with ratings from PlayerProjection (average + list)
                            try {
                                var reviewsSnap = org.steamproject.infra.kafka.consumer.PlayerProjection.getInstance().snapshotReviews();
                                java.util.List<java.util.Map<String,Object>> ratingsList = new java.util.ArrayList<>();
                                double sum = 0.0; int cnt = 0;
                                for (var revEntry : reviewsSnap.entrySet()) {
                                    for (var rv : revEntry.getValue()) {
                                        try {
                                            Object gidRv = rv.get("gameId");
                                            if (gidRv != null && gidRv.equals(gid)) {
                                                ratingsList.add(rv);
                                                Object r = rv.get("rating");
                                                if (r instanceof Number) { sum += ((Number) r).doubleValue(); cnt++; }
                                                else if (r != null) { try { sum += Double.parseDouble(r.toString()); cnt++; } catch (Exception ignore) {}
                                                }
                                            }
                                        } catch (Throwable t) { /* ignore per-item */ }
                                    }
                                }
                                if (cnt > 0) m.put("averageRating", sum / cnt);
                                if (!ratingsList.isEmpty()) m.put("ratings", ratingsList);
                            } catch (Throwable t) { /* best-effort */ }
                        }
                    } catch (Exception ex) { /* ignore enrichment failures */ }
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

    static class PublishersListHandler implements HttpHandler {
        private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException {
            var snapshot = org.steamproject.infra.kafka.consumer.PublisherProjection.getInstance().snapshot();
            java.util.Map<String, Integer> out = new java.util.HashMap<>();

            try {
                var ingestion = new org.steamproject.ingestion.PublisherIngestion();
                java.util.List<org.steamproject.model.Publisher> ing = ingestion.readAll();
                for (var p : ing) {
                    if (p.getId() != null) out.put(p.getId(), 0);
                }
            } catch (Exception e) {
            }

            snapshot.forEach((k, v) -> out.put(k, v.size()));
            String response = mapper.writeValueAsString(out);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (java.io.OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        }
    }

  
    static class CatalogHandler implements HttpHandler {
        private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException {
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
                    String publisherId = null;
                    for (var entry : pubSnap.entrySet()) {
                        for (String g : entry.getValue()) {
                            if (g.startsWith(p.length>0?p[0]:"")) { publisherId = entry.getKey(); break; }
                        }
                        if (publisherId != null) break;
                    }
                    m.put("publisherId", publisherId);

                    try {
                        var gd = org.steamproject.infra.kafka.consumer.GameProjection.getInstance().getGame(p.length>0? p[0] : null);
                        if (gd != null) {
                            if (gd.get("genre") != null) m.put("genre", gd.get("genre"));
                            if (gd.get("console") != null) m.put("console", gd.get("console"));
                            if (gd.get("platform") != null && m.get("platform") == null) m.put("platform", gd.get("platform"));
                            if (gd.get("price") != null) m.put("price", gd.get("price"));
                            if (gd.get("initialVersion") != null) m.put("initialVersion", gd.get("initialVersion"));
                            // include richer projection lists if available
                            if (gd.get("versions") != null) m.put("versions", gd.get("versions"));
                            if (gd.get("patches") != null) m.put("patches", gd.get("patches"));
                            if (gd.get("dlcs") != null) m.put("dlcs", gd.get("dlcs"));
                            if (gd.get("deprecatedVersions") != null) m.put("deprecatedVersions", gd.get("deprecatedVersions"));
                            if (gd.get("incidentResponses") != null) m.put("incidentResponses", gd.get("incidentResponses"));
                            if (gd.get("incidentCount") != null) m.put("incidentCount", gd.get("incidentCount"));
                            // enrich with ratings from PlayerProjection (average + list)
                            try {
                                var reviewsSnap = org.steamproject.infra.kafka.consumer.PlayerProjection.getInstance().snapshotReviews();
                                java.util.List<java.util.Map<String,Object>> ratingsList = new java.util.ArrayList<>();
                                double sum = 0.0; int cnt = 0;
                                for (var revEntry : reviewsSnap.entrySet()) {
                                    for (var rv : revEntry.getValue()) {
                                        try {
                                            Object gid = rv.get("gameId");
                                            if (gid != null && gid.equals(p.length>0? p[0] : null)) {
                                                ratingsList.add(rv);
                                                Object r = rv.get("rating");
                                                if (r instanceof Number) { sum += ((Number) r).doubleValue(); cnt++; }
                                                else if (r != null) { try { sum += Double.parseDouble(r.toString()); cnt++; } catch (Exception ignore) {}
                                                }
                                            }
                                        } catch (Throwable t) { /* ignore per-item */ }
                                    }
                                }
                                if (cnt > 0) m.put("averageRating", sum / cnt);
                                if (!ratingsList.isEmpty()) m.put("ratings", ratingsList);
                            } catch (Throwable t) { /* best-effort */ }
                        }
                    } catch (Exception ex) { /* ignore enrichment failures */ }
                    out.add(m);
                }
            });


            for (var entry : pubSnap.entrySet()) {
                String pubId = entry.getKey();
                for (String g : entry.getValue()) {
                    String[] p = g.split("\\|", 3);
                    String gid = p.length>0? p[0] : null;
                    boolean exists = out.stream().anyMatch(map -> gid != null && gid.equals(map.get("gameId")));
                    if (exists) continue;
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("gameId", gid);
                    m.put("gameName", p.length>1? p[1] : null);
                    m.put("releaseYear", p.length>2 && !p[2].isEmpty() ? Integer.parseInt(p[2]) : null);
                    m.put("platform", null);
                    m.put("publisherId", pubId);
                    try {
                        var gd = org.steamproject.infra.kafka.consumer.GameProjection.getInstance().getGame(gid);
                        if (gd != null) {
                            if (gd.get("genre") != null) m.put("genre", gd.get("genre"));
                            if (gd.get("console") != null) m.put("console", gd.get("console"));
                            if (gd.get("platform") != null) m.put("platform", gd.get("platform"));
                            if (gd.get("price") != null) m.put("price", gd.get("price"));
                            if (gd.get("initialVersion") != null) m.put("initialVersion", gd.get("initialVersion"));
                            if (gd.get("versions") != null) m.put("versions", gd.get("versions"));
                            if (gd.get("patches") != null) m.put("patches", gd.get("patches"));
                            if (gd.get("dlcs") != null) m.put("dlcs", gd.get("dlcs"));
                            if (gd.get("deprecatedVersions") != null) m.put("deprecatedVersions", gd.get("deprecatedVersions"));
                            if (gd.get("incidentResponses") != null) m.put("incidentResponses", gd.get("incidentResponses"));
                            if (gd.get("incidentCount") != null) m.put("incidentCount", gd.get("incidentCount"));
                            // enrich with ratings from PlayerProjection (average + list)
                            try {
                                var reviewsSnap = org.steamproject.infra.kafka.consumer.PlayerProjection.getInstance().snapshotReviews();
                                java.util.List<java.util.Map<String,Object>> ratingsList = new java.util.ArrayList<>();
                                double sum = 0.0; int cnt = 0;
                                for (var revEntry : reviewsSnap.entrySet()) {
                                    for (var rv : revEntry.getValue()) {
                                        try {
                                            Object gidRv = rv.get("gameId");
                                            if (gidRv != null && gidRv.equals(gid)) {
                                                ratingsList.add(rv);
                                                Object r = rv.get("rating");
                                                if (r instanceof Number) { sum += ((Number) r).doubleValue(); cnt++; }
                                                else if (r != null) { try { sum += Double.parseDouble(r.toString()); cnt++; } catch (Exception ignore) {}
                                                }
                                            }
                                        } catch (Throwable t) { /* ignore per-item */ }
                                    }
                                }
                                if (cnt > 0) m.put("averageRating", sum / cnt);
                                if (!ratingsList.isEmpty()) m.put("ratings", ratingsList);
                            } catch (Throwable t) { /* best-effort */ }
                        }
                    } catch (Exception ex) { /* ignore */ }
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
