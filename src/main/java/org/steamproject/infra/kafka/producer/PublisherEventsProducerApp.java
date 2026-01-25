package org.steamproject.infra.kafka.producer;

import java.util.Random;
import java.util.UUID;

public class PublisherEventsProducerApp {
    public static void main(String[] args) throws Exception {
        String bootstrap = System.getProperty("kafka.bootstrap", "localhost:9092");
        String sr = System.getProperty("schema.registry", "http://localhost:8081");
        String topic = System.getProperty("kafka.topic.pub", "game-released-events");
        String type = System.getProperty("event.type", "patch"); // patch|dlc|published|updated|deprecate|respond

        // Load games from ingestion so events are based on ingested dataset
        org.steamproject.ingestion.GameIngestion gi = new org.steamproject.ingestion.GameIngestion("/data/vgsales.csv");
        java.util.List<org.steamproject.model.Game> games = java.util.Collections.emptyList();
        try { games = gi.readAll(); } catch (Exception e) { /* fall back to random ids below */ }

        PublisherProducer p = new PublisherProducer(bootstrap, sr);
        Random rnd = new Random();

        // choose a game from ingestion or fallback to generated id
        String requestedGameId = System.getProperty("game.id");
        org.steamproject.model.Game selected = null;
        if (requestedGameId != null && !requestedGameId.isEmpty() && games != null) {
            for (org.steamproject.model.Game g : games) if (requestedGameId.equals(g.getId())) { selected = g; break; }
        }
        if (selected == null && games != null && !games.isEmpty()) {
            // pick random ingested game
            selected = games.get(rnd.nextInt(games.size()));
        }

        String sampleGameId = selected != null ? selected.getId() : System.getProperty("game.id", "sample-game-" + Math.abs(rnd.nextInt()));
        String samplePub = System.getProperty("publisher.id", null);
        String platform = selected != null ? (selected.getPlatform() == null ? System.getProperty("platform", "PC") : selected.getPlatform()) : System.getProperty("platform", "PC");
        String genre = selected != null ? (selected.getGenre() == null ? "" : selected.getGenre()) : "";

        // try to map publisher id from ingestion if not provided
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
                p.publishGamePublished(sampleGameId, "Sample Game", samplePub, platform, "Action", "1.0.0", 29.99).get();
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
                String gameName = selected != null ? selected.getName() : "Sample Game";
                String oldV = selected != null && selected.getYear() != null ? "1.0.0" : "1.0.0";
                p.publishPatch(sampleGameId, gameName, platform, oldV, newV, "Minor fixes").get();
                System.out.println("Sent PatchPublishedEvent newVersion=" + newV + " for " + sampleGameId);
                break;
            case "dlc":
                String dlcId = UUID.randomUUID().toString();
                p.publishDlc(dlcId, sampleGameId, samplePub, platform, "Extra Pack", 9.99).get();
                System.out.println("Sent DlcPublishedEvent dlc=" + dlcId + " for " + sampleGameId);
                break;
            case "deprecate":
                // deprecate a sensible version if available
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
