package org.steamproject.infra.kafka.streams.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.steamproject.events.ReviewVotedEvent;
import org.steamproject.events.VoteAction;
import org.steamproject.infra.kafka.producer.PlayerProducer;
import org.steamproject.infra.kafka.streams.PlayerStreamsProjection;
import org.steamproject.infra.kafka.streams.ReviewVotesStreams;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Handler HTTP pour les votes sur les évaluations (utilité des reviews).
 * 
 * Endpoints :
 * - POST /api/reviews/{reviewId}/vote : Soumettre un vote (utile/pas utile)
 * - DELETE /api/reviews/{reviewId}/vote?playerId=xxx : Supprimer un vote
 * - GET /api/reviews/{reviewId}/votes : Récupérer les votes agrégés pour une évaluation
 * - GET /api/reviews/votes : Récupérer tous les votes agrégés
 * - GET /api/reviews/{reviewId}/vote?playerId=xxx : Récupérer le vote d'un joueur spécifique
 * 
 * Body POST :
 * {
 *   "playerId": "player-123",
 *   "isHelpful": true
 * }
 */
public class ReviewVoteHandler implements HttpHandler {
    private final String bootstrapServers;
    private final String schemaRegistryUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    public ReviewVoteHandler(String bootstrapServers, String schemaRegistryUrl) {
        this.bootstrapServers = bootstrapServers;
        this.schemaRegistryUrl = schemaRegistryUrl;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        
        try {
            // Parse path: /api/reviews/{reviewId}/vote or /api/reviews/{reviewId}/votes or /api/reviews/votes
            String[] parts = path.split("/");
            
            // GET /api/reviews/votes - tous les votes
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/reviews/votes")) {
                handleGetAllVotes(exchange);
                return;
            }
            
            // Extraire reviewId
            String reviewId = null;
            if (parts.length >= 4) {
                reviewId = parts[3]; // /api/reviews/{reviewId}/...
            }
            
            if (reviewId == null || reviewId.isEmpty() || "votes".equals(reviewId)) {
                sendJsonResponse(exchange, 400, "{\"error\":\"Missing reviewId\"}");
                return;
            }
            
            // GET /api/reviews/{reviewId}/votes - votes agrégés pour une évaluation
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/votes")) {
                handleGetVotes(exchange, reviewId);
                return;
            }
            
            // GET /api/reviews/{reviewId}/vote?playerId=xxx - vote d'un joueur spécifique
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/vote")) {
                handleGetPlayerVote(exchange, reviewId);
                return;
            }
            
            // POST /api/reviews/{reviewId}/vote - soumettre un vote
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/vote")) {
                handlePostVote(exchange, reviewId);
                return;
            }
            
            // DELETE /api/reviews/{reviewId}/vote?playerId=xxx - supprimer un vote
            if ("DELETE".equalsIgnoreCase(method) && path.endsWith("/vote")) {
                handleDeleteVote(exchange, reviewId);
                return;
            }
            
            exchange.sendResponseHeaders(405, -1);
            
        } catch (Exception e) {
            e.printStackTrace();
            sendJsonResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * GET /api/reviews/votes - Récupère tous les votes agrégés
     */
    private void handleGetAllVotes(HttpExchange exchange) throws IOException {
        List<Map<String, Object>> allVotes = ReviewVotesStreams.getAllVotes();
        sendJsonResponse(exchange, 200, mapper.writeValueAsString(allVotes));
    }
    
    /**
     * GET /api/reviews/{reviewId}/votes - Récupère les votes agrégés pour une évaluation
     */
    private void handleGetVotes(HttpExchange exchange, String reviewId) throws IOException {
        Map<String, Object> votes = ReviewVotesStreams.getVotesForReview(reviewId);
        
        if (votes.isEmpty()) {
            // Retourne des valeurs par défaut si pas encore de votes
            ObjectNode defaultVotes = mapper.createObjectNode();
            defaultVotes.put("reviewId", reviewId);
            defaultVotes.put("helpfulVotes", 0);
            defaultVotes.put("notHelpfulVotes", 0);
            defaultVotes.set("voters", mapper.createArrayNode());
            sendJsonResponse(exchange, 200, mapper.writeValueAsString(defaultVotes));
        } else {
            sendJsonResponse(exchange, 200, mapper.writeValueAsString(votes));
        }
    }
    
    /**
     * GET /api/reviews/{reviewId}/vote?playerId=xxx - Récupère le vote d'un joueur
     */
    private void handleGetPlayerVote(HttpExchange exchange, String reviewId) throws IOException {
        Map<String, String> params = parseQueryParams(exchange);
        String playerId = params.get("playerId");
        
        if (playerId == null || playerId.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"error\":\"Missing playerId\"}");
            return;
        }
        
        Boolean vote = ReviewVotesStreams.getPlayerVote(reviewId, playerId);
        
        ObjectNode response = mapper.createObjectNode();
        response.put("reviewId", reviewId);
        response.put("playerId", playerId);
        response.put("hasVoted", vote != null);
        if (vote != null) {
            response.put("isHelpful", vote);
        }
        
        sendJsonResponse(exchange, 200, mapper.writeValueAsString(response));
    }
    
    /**
     * POST /api/reviews/{reviewId}/vote - Soumet un vote
     */
    private void handlePostVote(HttpExchange exchange, String reviewId) throws IOException {
        String playerId = null;
        Boolean isHelpful = null;
        
        // Parse query params first
        Map<String, String> params = parseQueryParams(exchange);
        if (params.containsKey("playerId")) {
            playerId = params.get("playerId");
        }
        if (params.containsKey("isHelpful")) {
            isHelpful = Boolean.parseBoolean(params.get("isHelpful"));
        }
        
        // Fallback to JSON body
        if (playerId == null || isHelpful == null) {
            try (InputStream is = exchange.getRequestBody()) {
                var node = mapper.readTree(is);
                if (playerId == null && node.has("playerId")) {
                    playerId = node.get("playerId").asText();
                }
                if (isHelpful == null && node.has("isHelpful")) {
                    isHelpful = node.get("isHelpful").asBoolean();
                }
            }
        }
        
        if (playerId == null || playerId.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"error\":\"Missing playerId\"}");
            return;
        }
        
        if (isHelpful == null) {
            sendJsonResponse(exchange, 400, "{\"error\":\"Missing isHelpful\"}");
            return;
        }
        
        // Valide que le joueur existe
        var player = PlayerStreamsProjection.getPlayer(playerId);
        if (player == null) {
            sendJsonResponse(exchange, 404, "{\"error\":\"Player not found\"}");
            return;
        }
        
        // Détermine l'action (CAST ou CHANGE)
        Boolean previousVote = ReviewVotesStreams.getPlayerVote(reviewId, playerId);
        VoteAction action = (previousVote == null) ? VoteAction.CAST : VoteAction.CHANGE;
        
        // Crée l'événement
        String eventId = UUID.randomUUID().toString();
        ReviewVotedEvent event = ReviewVotedEvent.newBuilder()
            .setEventId(eventId)
            .setReviewId(reviewId)
            .setVoterPlayerId(playerId)
            .setIsHelpful(isHelpful)
            .setVoteAction(action)
            .setTimestamp(Instant.now().toEpochMilli())
            .build();
        
        // Envoie à Kafka
        PlayerProducer producer = new PlayerProducer(bootstrapServers, schemaRegistryUrl, "unused");
        try {
            producer.sendReviewVote(reviewId, event).get();
        } catch (Exception e) {
            producer.close();
            sendJsonResponse(exchange, 500, "{\"error\":\"Failed to send vote: " + e.getMessage() + "\"}");
            return;
        }
        producer.close();
        
        // Réponse
        ObjectNode response = mapper.createObjectNode();
        response.put("status", "accepted");
        response.put("eventId", eventId);
        response.put("reviewId", reviewId);
        response.put("playerId", playerId);
        response.put("isHelpful", isHelpful);
        response.put("action", action.toString());
        
        sendJsonResponse(exchange, 202, mapper.writeValueAsString(response));
    }
    
    /**
     * DELETE /api/reviews/{reviewId}/vote?playerId=xxx - Supprime un vote
     */
    private void handleDeleteVote(HttpExchange exchange, String reviewId) throws IOException {
        Map<String, String> params = parseQueryParams(exchange);
        String playerId = params.get("playerId");
        
        if (playerId == null || playerId.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"error\":\"Missing playerId\"}");
            return;
        }
        
        // Vérifie que le joueur a voté
        Boolean previousVote = ReviewVotesStreams.getPlayerVote(reviewId, playerId);
        if (previousVote == null) {
            sendJsonResponse(exchange, 404, "{\"error\":\"No vote found for this player\"}");
            return;
        }
        
        // Crée l'événement de suppression
        String eventId = UUID.randomUUID().toString();
        ReviewVotedEvent event = ReviewVotedEvent.newBuilder()
            .setEventId(eventId)
            .setReviewId(reviewId)
            .setVoterPlayerId(playerId)
            .setIsHelpful(previousVote) // garde la valeur précédente
            .setVoteAction(VoteAction.REMOVE)
            .setTimestamp(Instant.now().toEpochMilli())
            .build();
        
        // Envoie à Kafka
        PlayerProducer producer = new PlayerProducer(bootstrapServers, schemaRegistryUrl, "unused");
        try {
            producer.sendReviewVote(reviewId, event).get();
        } catch (Exception e) {
            producer.close();
            sendJsonResponse(exchange, 500, "{\"error\":\"Failed to remove vote: " + e.getMessage() + "\"}");
            return;
        }
        producer.close();
        
        ObjectNode response = mapper.createObjectNode();
        response.put("status", "accepted");
        response.put("eventId", eventId);
        response.put("action", "REMOVE");
        
        sendJsonResponse(exchange, 202, mapper.writeValueAsString(response));
    }
    
    private Map<String, String> parseQueryParams(HttpExchange exchange) {
        Map<String, String> params = new HashMap<>();
        String query = exchange.getRequestURI().getQuery();
        if (query != null && !query.isEmpty()) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length == 2) {
                    try {
                        params.put(pair[0], URLDecoder.decode(pair[1], "UTF-8"));
                    } catch (Exception e) {
                        params.put(pair[0], pair[1]);
                    }
                }
            }
        }
        return params;
    }
    
    private void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
