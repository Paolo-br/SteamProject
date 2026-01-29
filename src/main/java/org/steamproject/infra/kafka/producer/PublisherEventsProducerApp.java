package org.steamproject.infra.kafka.producer;

import java.util.Random;
import java.util.UUID;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

public class PublisherEventsProducerApp {
    public static void main(String[] args) throws Exception {
        String bootstrap = System.getProperty("kafka.bootstrap", "localhost:9092");
        String sr = System.getProperty("schema.registry", "http://localhost:8081");
        String topic = System.getProperty("kafka.topic.pub", "game-released-events");
        String type = System.getProperty("event.type", "patch"); // patch|dlc|published|updated|deprecate|respond

        org.steamproject.ingestion.GameIngestion gi = new org.steamproject.ingestion.GameIngestion("/data/vgsales.csv");
        java.util.List<org.steamproject.model.Game> games = java.util.Collections.emptyList();
        try { games = gi.readAll(); } catch (Exception e) { /* fall back to random ids below */ }

        PublisherProducer p = new PublisherProducer(bootstrap, sr);
        Random rnd = new Random();

        String requestedGameId = System.getProperty("game.id");
        org.steamproject.model.Game selected = null;
        if (requestedGameId != null && !requestedGameId.isEmpty() && games != null) {
            for (org.steamproject.model.Game g : games) if (requestedGameId.equals(g.getId())) { selected = g; break; }
        }
        if (selected == null && games != null && !games.isEmpty()) {
            selected = games.get(rnd.nextInt(games.size()));
        }

        if (selected == null) {
            System.err.println("No ingested game found or game.id not provided. Aborting — do not publish sample games.");
            return;
        }

        String sampleGameId = selected.getId();
        HttpClient httpClient = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();
        String samplePub = System.getProperty("publisher.id", null);
        String platform = selected.getPlatform() != null ? selected.getPlatform() : System.getProperty("platform", "PC");
        String genre = selected.getGenre() != null ? selected.getGenre() : "";

        if ((samplePub == null || samplePub.isEmpty()) && selected != null) {
            try {
                org.steamproject.ingestion.PublisherIngestion pin = new org.steamproject.ingestion.PublisherIngestion();
                for (org.steamproject.model.Publisher pub : pin.readAll()) {
                    if (pub.getName() != null && pub.getName().equalsIgnoreCase(selected.getPublisher())) { samplePub = pub.getId(); break; }
                }
            } catch (Exception ignored) {}
        }
        if (samplePub == null || samplePub.isEmpty()) samplePub = System.getProperty("publisher.id", "publisher-sample");

        switch (type) {
            case "published":
                p.publishGamePublished(sampleGameId, selected.getName(), samplePub, platform, genre == null ? "" : genre, "1.0.0", 0.0).get();
                System.out.println("Sent GamePublishedEvent for " + sampleGameId);
                break;
            case "updated":
                java.util.Map<String,String> updates = new java.util.HashMap<>();
                updates.put("genre", "RPG");
                updates.put("description", "Updated description from CLI");
                p.publishGameUpdated(sampleGameId, samplePub, platform, updates).get();
                System.out.println("Sent GameUpdatedEvent for " + sampleGameId);
                break;
            case "patch":
                String newV = "1.1." + rnd.nextInt(100);
                String gameName = selected.getName();
                String oldV = "1.0.0";
                try {
                    HttpRequest catalogReq = HttpRequest.newBuilder().uri(URI.create("http://localhost:8080/api/catalog")).GET().build();
                    HttpResponse<String> catalogResp = httpClient.send(catalogReq, HttpResponse.BodyHandlers.ofString());
                    HttpRequest purchasesReq = HttpRequest.newBuilder().uri(URI.create("http://localhost:8080/api/purchases")).GET().build();
                    HttpResponse<String> purchasesResp = httpClient.send(purchasesReq, HttpResponse.BodyHandlers.ofString());

                    if (catalogResp.statusCode() == 200 && purchasesResp.statusCode() == 200) {
                        JsonNode catalogArr = mapper.readTree(catalogResp.body());
                        JsonNode purchasesArr = mapper.readTree(purchasesResp.body());

                        java.util.Set<String> purchasedGameIds = new java.util.HashSet<>();
                        if (purchasesArr.isArray()) {
                            for (JsonNode pn : purchasesArr) {
                                if (pn.hasNonNull("gameId")) purchasedGameIds.add(pn.get("gameId").asText());
                            }
                        }

                        java.util.List<JsonNode> candidates = new java.util.ArrayList<>();
                        if (catalogArr.isArray()) {
                            for (JsonNode g : catalogArr) {
                                if (!g.hasNonNull("gameId")) continue;
                                String gid = g.get("gameId").asText();
                                boolean owned = purchasedGameIds.contains(gid);
                                boolean hasInc = (g.hasNonNull("incidentCount") && g.get("incidentCount").asInt() > 0)
                                        || (g.hasNonNull("incidentResponses") && g.get("incidentResponses").isArray() && g.get("incidentResponses").size() > 0);
                                if (owned && hasInc) candidates.add(g);
                            }
                        }

                        boolean usedRequested = false;
                        if (requestedGameId != null && !requestedGameId.isEmpty()) {
                            for (JsonNode g : candidates) {
                                if (g.hasNonNull("gameId") && requestedGameId.equals(g.get("gameId").asText())) {
                                    sampleGameId = requestedGameId;
                                    if (g.hasNonNull("platform")) platform = g.get("platform").asText(platform);
                                    if (g.hasNonNull("publisherId")) samplePub = g.get("publisherId").asText(samplePub);
                                    usedRequested = true;
                                    break;
                                }
                            }
                        }

                        if (!usedRequested) {
                            if (candidates.isEmpty()) {
                                System.err.println("No published+owned game with incidents found — cannot publish patch.");
                                break;
                            }
                            JsonNode chosen = candidates.get(rnd.nextInt(candidates.size()));
                            sampleGameId = chosen.get("gameId").asText(sampleGameId);
                            if (chosen.hasNonNull("platform")) platform = chosen.get("platform").asText(platform);
                            if (chosen.hasNonNull("publisherId")) samplePub = chosen.get("publisherId").asText(samplePub);
                        }
                    } else {
                        System.err.println("Warning: could not fetch catalog/purchases — cannot reliably choose a published+owned game.");
                        break;
                    }
                } catch (Exception ex) {
                    System.err.println("Warning: could not verify published/owned games via projection — aborting patch publish.");
                    break;
                }

                p.publishPatch(sampleGameId, gameName, platform, oldV, newV, "Minor fixes").get();
                System.out.println("Sent PatchPublishedEvent newVersion=" + newV + " for " + sampleGameId);
                break;
            case "dlc":
                try {
                    HttpRequest req = HttpRequest.newBuilder().uri(URI.create("http://localhost:8080/api/catalog")).GET().build();
                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        JsonNode arr = mapper.readTree(resp.body());
                        boolean exists = false;
                        if (arr.isArray()) {
                            for (JsonNode g : arr) {
                                if (g.hasNonNull("gameId") && sampleGameId.equals(g.get("gameId").asText())) { exists = true; break; }
                            }
                            if (!exists && arr.size() > 0) {
                                JsonNode g = arr.get(new Random().nextInt(arr.size()));
                                sampleGameId = g.get("gameId").asText(sampleGameId);
                                if (g.hasNonNull("platform")) platform = g.get("platform").asText(platform);
                                if (g.hasNonNull("publisherId")) samplePub = g.get("publisherId").asText(samplePub);
                                exists = true;
                            }
                        }
                        if (!exists) {
                            System.err.println("No published game found in projection — publish a game first.");
                            break;
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("Warning: could not verify published games via /api/catalog — proceeding to publish DLC");
                }
                String dlcId = UUID.randomUUID().toString();
                p.publishDlc(dlcId, sampleGameId, samplePub, platform, "Extra Pack", 9.99).get();
                System.out.println("Sent DlcPublishedEvent dlc=" + dlcId + " for " + sampleGameId);
                break;
            case "deprecate":
                String deprecated = "1.0.0";
                if (selected != null && selected.getYear() != null) deprecated = "1.0.0";
                p.publishVersionDeprecated(sampleGameId, samplePub, platform, deprecated).get();
                System.out.println("Sent GameVersionDeprecatedEvent for " + sampleGameId);
                break;
            case "respond":
                p.publishEditorResponse(UUID.randomUUID().toString(), sampleGameId, samplePub, platform, "We are investigating").get();
                System.out.println("Sent EditorRespondedToIncidentEvent for " + sampleGameId);
                break;
            default:
                System.out.println("Unknown event.type=" + type);
        }
        p.close();
    }
}
