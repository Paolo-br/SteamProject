package org.steamproject.infra.kafka.producer;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.Random;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.steamproject.ingestion.GameIngestion;
import org.steamproject.ingestion.PublisherIngestion;
import org.steamproject.model.Game;
import org.steamproject.model.Publisher;
import java.nio.charset.StandardCharsets;


public class PublisherProducerApp {
    public static void main(String[] args) throws Exception {
        String bootstrap = System.getProperty("kafka.bootstrap", "localhost:9092");
        String sr = System.getProperty("schema.registry", "http://localhost:8081");
        String topic = System.getProperty("kafka.topic.pub", "game-released-events");

        GameIngestion gi = new GameIngestion("/data/vgsales.csv");
        List<Game> games = null;
        try {
            games = gi.readAll();
        } catch (Exception e) {
            System.err.println("Failed to read ingestion CSV: " + e.getMessage());
            System.exit(2);
        }

        if (games.isEmpty()) {
            System.err.println("No games available from ingestion");
            System.exit(2);
        }

        Set<String> publishedIds = new HashSet<>();
        try {
            HttpClient http = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create("http://localhost:8080/api/catalog")).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(resp.body());
                if (root.isArray()) {
                    for (JsonNode n : root) {
                        JsonNode gid = n.get("gameId");
                        if (gid != null && !gid.isNull()) publishedIds.add(gid.asText());
                    }
                }
            }
        } catch (Exception ignored) {
        }

        String requestedGameId = System.getProperty("game.id");
        Game selected = null;
        if (requestedGameId != null && !requestedGameId.isEmpty()) {
            for (Game g : games) if (requestedGameId.equals(g.getId())) { selected = g; break; }
        }

        List<Game> candidates = new java.util.ArrayList<>();
        for (Game g : games) {
            if (!publishedIds.contains(g.getId())) candidates.add(g);
        }

        if (selected == null) {
            if (!candidates.isEmpty()) {
                Random rnd = new Random();
                selected = candidates.get(rnd.nextInt(candidates.size()));
            } else {
                selected = games.get(new Random().nextInt(games.size()));
            }
        }

        String publisherName = selected.getPublisher() == null ? "" : selected.getPublisher();
        String publisherId = null;
        try {
            PublisherIngestion pin = new PublisherIngestion();
            List<Publisher> pubs = pin.readAll();
            for (Publisher p : pubs) {
                if (p.getName() != null && p.getName().equalsIgnoreCase(publisherName)) { publisherId = p.getId(); break; }
            }
        } catch (Exception ignored) {}

        if (publisherId == null) {
            String slug = (publisherName == null ? "" : publisherName.toLowerCase()).replaceAll("[^\\p{Alnum}]+", "_");
            slug = slug.replaceAll("^_+|_+$", "");
            if (slug.isEmpty()) slug = UUID.nameUUIDFromBytes((publisherName == null ? "" : publisherName).getBytes(StandardCharsets.UTF_8)).toString();
            publisherId = "publisher-" + slug;
        }

        String platform = selected.getPlatform();
        if (platform == null || platform.isBlank()) platform = "PC";
        String genre = selected.getGenre();
        if (genre == null || genre.isBlank()) genre = "Misc";

        Random rnd = new Random();
        double price = 30.0 + rnd.nextDouble() * 30.0;
        price = Math.round(price * 100.0) / 100.0;

   
        String version = "1.0." + rnd.nextInt(100);

        PublisherProducer p = new PublisherProducer(bootstrap, sr);
        p.publishGame(selected.getId(), selected.getName(), publisherId, publisherName, selected.getYear() == null ? 2026 : selected.getYear(), platform, Arrays.asList(platform), version, price, genre).get();
        System.out.println("Published GameReleasedEvent gameId=" + selected.getId() + " publisher=" + publisherId + " name=" + selected.getName() + " platform=" + platform + " genre=" + genre + " price=" + price + " version=" + version);
        p.close();
    }
}
