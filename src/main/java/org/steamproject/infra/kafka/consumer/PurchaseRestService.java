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
import org.steamproject.infra.kafka.streams.ReviewVotesStreams;
import org.steamproject.infra.kafka.streams.handlers.PlayerStreamsHandler;
import org.steamproject.infra.kafka.streams.handlers.PurchaseStreamsHandler;
import org.steamproject.infra.kafka.streams.handlers.ReviewVoteHandler;

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

        // ==================== KAFKA STREAMS FOR PUBLISHER STATS ====================
        System.out.println("Starting PublisherStatsStreams...");
        Thread publisherStatsThread = new Thread(() -> {
            try {
                org.steamproject.infra.kafka.streams.PublisherStatsStreams.startStreams();
            } catch (Throwable t) {
                System.err.println("Error starting PublisherStatsStreams: " + t.getMessage());
                t.printStackTrace();
            }
        }, "publisher-stats-streams-thread");
        publisherStatsThread.setDaemon(true);
        publisherStatsThread.start();

        // ==================== KAFKA STREAMS FOR REVIEW VOTES ====================
        System.out.println("Starting ReviewVotesStreams...");
        Thread reviewVotesThread = new Thread(() -> {
            try {
                ReviewVotesStreams.startStreams();
            } catch (Throwable t) {
                System.err.println("Error starting ReviewVotesStreams: " + t.getMessage());
                t.printStackTrace();
            }
        }, "review-votes-streams-thread");
        reviewVotesThread.setDaemon(true);
        reviewVotesThread.start();

        // ==================== KAFKA STREAMS FOR GAME TO PLATFORM ROUTING ====================
        System.out.println("Starting GameToPlatformCatalogStreams...");
        Thread gameToPlatformThread = new Thread(() -> {
            try {
                org.steamproject.infra.kafka.streams.GameToPlatformCatalogStreams.startStreams();
            } catch (Throwable t) {
                System.err.println("Error starting GameToPlatformCatalogStreams: " + t.getMessage());
                t.printStackTrace();
            }
        }, "game-to-platform-streams-thread");
        gameToPlatformThread.setDaemon(true);
        gameToPlatformThread.start();

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

        String platTopic = System.getProperty("kafka.topic.platform", "platform-catalog.events");
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
        server.createContext("/api/publisher-stats", new PublisherStatsHandler());  // Publisher Stats Kafka Streams
        server.createContext("/api/reviews", new ReviewVoteHandler(bootstrap, sr));  // Review Votes Kafka Streams

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

    // ==================== HANDLERS MIGRÉS VERS KAFKA STREAMS ====================

    static class PurchasesHandler implements HttpHandler {
        private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Return flattened list of all purchases (games + DLCs) across players using Kafka Streams
            var gamePurchases = PlayerStreamsProjection.getAllPurchases();
            var dlcPurchases = PlayerStreamsProjection.getAllDlcPurchases();
            
            // Combine both lists, ensure DLC entries include a display name (`gameName`) and flag
            java.util.List<java.util.Map<String, Object>> allPurchases = new java.util.ArrayList<>(gamePurchases);
            for (java.util.Map<String, Object> dlcEntry : dlcPurchases) {
                try {
                    // If DLC purchase provides dlcName, expose it as gameName for UI consistency
                    Object dlcName = dlcEntry.get("dlcName");
                    if (dlcName != null && (dlcEntry.get("gameName") == null || dlcEntry.get("gameName").toString().isEmpty())) {
                        dlcEntry.put("gameName", dlcName);
                    }
                    // Ensure DLC flag is set for downstream clients
                    dlcEntry.put("isDlc", true);
                    allPurchases.add(dlcEntry);
                } catch (Throwable t) { /* best-effort */ allPurchases.add(dlcEntry); }
            }
            
            // Sort by timestamp descending
            allPurchases.sort((a, b) -> {
                Long tsA = a.get("timestamp") instanceof Number ? ((Number) a.get("timestamp")).longValue() : 0L;
                Long tsB = b.get("timestamp") instanceof Number ? ((Number) b.get("timestamp")).longValue() : 0L;
                return tsB.compareTo(tsA);
            });
            
            String response = mapper.writeValueAsString(allPurchases);
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
                            // enrich with ratings from Kafka Streams (average + list)
                            try {
                                java.util.List<java.util.Map<String,Object>> ratingsList = PlayerStreamsProjection.getReviewsForGame(gid);
                                Double avgRating = PlayerStreamsProjection.getAverageRatingForGame(gid);
                                if (avgRating != null) m.put("averageRating", avgRating);
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
                            // enrich with ratings from Kafka Streams (average + list)
                            try {
                                String gameIdForRating = p.length > 0 ? p[0] : null;
                                java.util.List<java.util.Map<String,Object>> ratingsList = PlayerStreamsProjection.getReviewsForGame(gameIdForRating);
                                Double avgRating = PlayerStreamsProjection.getAverageRatingForGame(gameIdForRating);
                                if (avgRating != null) m.put("averageRating", avgRating);
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
                            // enrich with ratings from Kafka Streams (average + list)
                            try {
                                java.util.List<java.util.Map<String,Object>> ratingsList = PlayerStreamsProjection.getReviewsForGame(gid);
                                Double avgRating = PlayerStreamsProjection.getAverageRatingForGame(gid);
                                if (avgRating != null) m.put("averageRating", avgRating);
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

    /**
     * Handler pour les statistiques avancées des éditeurs (réactivité, qualité).
     *
     * Routes :
     * - GET /api/publisher-stats : Toutes les statistiques de tous les éditeurs
     * - GET /api/publisher-stats/{publisherId} : Statistiques d'un éditeur spécifique
     * - GET /api/publisher-stats/top/quality : Éditeur avec le meilleur score qualité
     * - GET /api/publisher-stats/top/reactivity : Éditeur avec le meilleur score réactivité
     */
    static class PublisherStatsHandler implements HttpHandler {
        private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException {
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");

            try {
                // GET /api/publisher-stats
                if ("/api/publisher-stats".equals(path) || "/api/publisher-stats/".equals(path)) {
                    var allStats = org.steamproject.infra.kafka.streams.PublisherStatsStreams.getAllPublisherStats();
                    String response = mapper.writeValueAsString(allStats);
                    sendJsonResponse(exchange, response);
                    return;
                }

                // GET /api/publisher-stats/top/quality
                if (parts.length >= 5 && "top".equals(parts[3]) && "quality".equals(parts[4])) {
                    var top = org.steamproject.infra.kafka.streams.PublisherStatsStreams.getTopQualityPublisher();
                    if (top != null) {
                        java.util.Map<String, Object> result = new java.util.HashMap<>();
                        result.put("publisherId", top.getKey());
                        result.put("qualityScore", top.getValue());
                        // Essayer de récupérer le nom de l'éditeur
                        try {
                            var stats = org.steamproject.infra.kafka.streams.PublisherStatsStreams.getPublisherStats(top.getKey());
                            if (stats != null) result.putAll(stats);
                        } catch (Exception e) { /* ignore */ }
                        sendJsonResponse(exchange, mapper.writeValueAsString(result));
                    } else {
                        sendJsonResponse(exchange, "{}");
                    }
                    return;
                }

                // GET /api/publisher-stats/top/reactivity
                if (parts.length >= 5 && "top".equals(parts[3]) && "reactivity".equals(parts[4])) {
                    var top = org.steamproject.infra.kafka.streams.PublisherStatsStreams.getTopReactivityPublisher();
                    if (top != null) {
                        java.util.Map<String, Object> result = new java.util.HashMap<>();
                        result.put("publisherId", top.getKey());
                        result.put("reactivityScore", top.getValue());
                        try {
                            var stats = org.steamproject.infra.kafka.streams.PublisherStatsStreams.getPublisherStats(top.getKey());
                            if (stats != null) result.putAll(stats);
                        } catch (Exception e) { /* ignore */ }
                        sendJsonResponse(exchange, mapper.writeValueAsString(result));
                    } else {
                        sendJsonResponse(exchange, "{}");
                    }
                    return;
                }

                // GET /api/publisher-stats/{publisherId}
                if (parts.length >= 4) {
                    String publisherId = parts[3];
                    var stats = org.steamproject.infra.kafka.streams.PublisherStatsStreams.getPublisherStats(publisherId);
                    if (stats != null) {
                        sendJsonResponse(exchange, mapper.writeValueAsString(stats));
                    } else {
                        sendJsonResponse(exchange, "{}");
                    }
                    return;
                }

                exchange.sendResponseHeaders(404, -1);
            } catch (Exception e) {
                e.printStackTrace();
                exchange.sendResponseHeaders(500, -1);
            }
        }

        private void sendJsonResponse(com.sun.net.httpserver.HttpExchange exchange, String json) throws java.io.IOException {
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (java.io.OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        }
    }
}
