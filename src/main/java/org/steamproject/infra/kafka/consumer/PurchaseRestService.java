package org.steamproject.infra.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

// Import des handlers Kafka Streams
import org.steamproject.infra.kafka.streams.PlayerStreamsProjection;
import org.steamproject.infra.kafka.streams.handlers.PlayerStreamsHandler;
import org.steamproject.infra.kafka.streams.handlers.PurchaseStreamsHandler;

/**
 * Service REST exposant les données des projections Kafka via HTTP.
 *
 * Ce service démarre plusieurs consommateurs Kafka en arrière-plan (PlayerConsumer,
 * PublisherConsumer, PlatformConsumer) et expose leurs projections via des endpoints
 * REST. Il permet de consulter les bibliothèques de joueurs, les catalogues d'éditeurs
 * et de plateformes, ainsi que de créer de nouveaux achats.
 *
 * Endpoints disponibles :
 * - GET  /api/players : Liste tous les joueurs
 * - GET  /api/players/{playerId}/library : Bibliothèque d'un joueur
 * - GET  /api/players/{playerId}/sessions : Sessions de jeu d'un joueur
 * - GET  /api/players/{playerId}/reviews : Avis d'un joueur
 * - POST /api/purchase : Créer un nouvel achat
 * - GET  /api/purchases : Liste de tous les achats
 * - GET  /api/publishers : Liste des éditeurs
 * - GET  /api/publishers/{publisherId}/games : Jeux d'un éditeur
 * - GET  /api/platforms/{platformId}/catalog : Catalogue d'une plateforme
 * - GET  /api/publishers-list : Statistiques des éditeurs
 * - GET  /api/catalog : Catalogue global enrichi
 */
public class PurchaseRestService {
    public static void main(String[] args) throws Exception {

        String bootstrap = System.getProperty("kafka.bootstrap", "localhost:9092");
        String sr = System.getProperty("schema.registry", "http://localhost:8081");
        String purchaseTopic = System.getProperty("kafka.topic", "game-purchase-events");

        // ==================== KAFKA STREAMS FOR PLAYERS ====================
        // Start Kafka Streams for player projections instead of classic consumer
        System.out.println("Starting PlayerStreamsProjection...");
        System.setProperty("KAFKA_BOOTSTRAP_SERVERS", bootstrap);
        System.setProperty("SCHEMA_REGISTRY_URL", sr);
        Thread playerStreamsThread = new Thread(() -> {
            try {
                PlayerStreamsProjection.startStreams();
            } catch (Throwable t) {
                System.err.println("Error starting PlayerStreamsProjection: " + t.getMessage());
                t.printStackTrace();
            }
        }, "player-streams-thread");
        playerStreamsThread.setDaemon(true);
        playerStreamsThread.start();

        // Wait for Kafka Streams to be ready
        Thread.sleep(3000);

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

        int port = Integer.getInteger("http.port", 8080);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // ==================== HANDLERS KAFKA STREAMS ====================
        server.createContext("/api/players", new PlayerStreamsHandler());  // Kafka Streams
        server.createContext("/api/purchase", new PurchaseStreamsHandler(bootstrap, sr, purchaseTopic));  // Kafka Streams

        // ==================== HANDLERS CLASSIC (non migrés) ====================
        server.createContext("/api/purchases", new PurchasesHandler());
        server.createContext("/api/publishers", new PublisherHandler());
        server.createContext("/api/platforms", new PlatformHandler());
        server.createContext("/api/publishers-list", new PublishersListHandler());
        server.createContext("/api/catalog", new CatalogHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Purchase REST service listening on http://localhost:" + port + "/api/players/{playerId}/library");
    }

    // ==================== HANDLERS NON MIGRÉS (utilisant encore les consumers classiques) ====================

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
                        // Ignore les entrées malformées (best-effort)
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

    /**
     * Handler pour les endpoints liés aux éditeurs.
     *
     * Gère plusieurs routes :
     * - GET /api/publishers : Liste tous les éditeurs depuis l'ingestion
     * - GET /api/publishers/{publisherId}/games : Jeux publiés enrichis avec détails,
     *   versions, patches, DLCs, incidents et notations moyennes
     */
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

    /**
     * Handler pour récupérer le catalogue d'une plateforme.
     *
     * Route : GET /api/platforms/{platformId}/catalog
     * Retourne la liste des jeux disponibles sur la plateforme spécifiée.
     */
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
     * Handler pour obtenir des statistiques sur les éditeurs.
     *
     * Retourne une map associant chaque publisherId au nombre de jeux publiés.
     * Combine les données d'ingestion (éditeurs existants) avec les données
     * de la projection (jeux effectivement publiés).
     */
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

    /**
     * Handler pour le catalogue global enrichi de tous les jeux.
     *
     * Combine les données des projections Platform et Publisher pour produire
     * un catalogue complet avec enrichissement depuis GameProjection (genre,
     * prix, versions, patches, DLCs, incidents) et PlayerProjection (notations
     * moyennes et liste des avis).
     *
     * Fusionne les jeux présents dans les catalogues de plateforme avec ceux
     * publiés par les éditeurs pour une vue exhaustive.
     */
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
