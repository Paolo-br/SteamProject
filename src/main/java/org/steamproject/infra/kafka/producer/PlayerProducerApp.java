package org.steamproject.infra.kafka.producer;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.steamproject.events.PlayerCreatedEvent;
import org.steamproject.events.GamePurchaseEvent;
import org.steamproject.events.DlcPurchaseEvent;
import org.steamproject.events.GameSessionEvent;
import org.steamproject.events.CrashReportEvent;
import org.steamproject.events.NewRatingEvent;
import org.steamproject.events.ReviewPublishedEvent;
import org.steamproject.events.ReviewVotedEvent;

import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ThreadLocalRandom;

/**
 * CLI utilitaire combinant l'envoi d'événements PlayerCreated et GamePurchase.
 * Mode d'utilisation via propriétés système :
 * -Dmode=create       -> envoie un PlayerCreatedEvent (génération automatique si pas d'autres -D)
 * -Dmode=purchase     -> envoie un GamePurchaseEvent (utilise GamePurchaseProducer)
 */
public class PlayerProducerApp {
    private static long randomTimestampBetween(String purchaseDateStr) {
        long now = Instant.now().toEpochMilli();
        long start;
        if (purchaseDateStr != null) {
            try {
                start = Instant.parse(purchaseDateStr).toEpochMilli();
            } catch (Exception e) {
                start = now - 30L * 24 * 3600 * 1000; // fallback 30 days
            }
        } else {
            start = now - 30L * 24 * 3600 * 1000;
        }
        if (start >= now) start = now;
        long diff = now - start;
        long offset = diff > 0 ? ThreadLocalRandom.current().nextLong(diff + 1) : 0;
        return start + offset;
    }
    public static void main(String[] args) throws Exception {
        String mode = System.getProperty("mode", "create").toLowerCase();
        String bootstrap = System.getProperty("kafka.bootstrap", "localhost:9092");
        String sr = System.getProperty("schema.registry", "http://localhost:8081");

        if ("create".equals(mode)) {
            String playerId = System.getProperty("test.player.id", null);
            String username = System.getProperty("test.player.username", null);
            String email = System.getProperty("test.player.email", null);
            String firstName = System.getProperty("test.player.firstName", null);
            String lastName = System.getProperty("test.player.lastName", null);
            String dateOfBirth = System.getProperty("test.player.dateOfBirth", null);

            if (playerId == null || username == null || email == null) {
                var gen = new org.steamproject.ingestion.PlayerGenerator();
                var list = gen.generate(1);
                var p = list.get(0);
                if (playerId == null) playerId = p.getId();
                if (username == null) username = p.getUsername();
                if (email == null) email = p.getEmail();
                if (firstName == null) firstName = p.getFirstName();
                if (lastName == null) lastName = p.getLastName();
                if (dateOfBirth == null) dateOfBirth = p.getDateOfBirth();
            }

            Properties props = new Properties();
            props.put("bootstrap.servers", bootstrap);
            props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            props.put("value.serializer", KafkaAvroSerializer.class.getName());
            props.put("schema.registry.url", sr);

            try (KafkaProducer<String, Object> prod = new KafkaProducer<>(props)) {
                PlayerCreatedEvent p = PlayerCreatedEvent.newBuilder()
                    .setId(playerId)
                    .setUsername(username)
                    .setEmail(email)
                    .setRegistrationDate(Instant.now().toString())
                    .setDistributionPlatformId(null)
                    .setFirstName(firstName == null ? null : firstName)
                    .setLastName(lastName == null ? null : lastName)
                    .setDateOfBirth(dateOfBirth == null ? null : dateOfBirth)
                    .setGdprConsent(true)
                    .setGdprConsentDate(Instant.now().toString())
                    .setTimestamp(Instant.now().toEpochMilli())
                    .build();

                prod.send(new ProducerRecord<>(System.getProperty("kafka.topic.player", "player-created-events"), playerId, p)).get();
                System.out.println("Sent PlayerCreatedEvent id=" + playerId);
            }

        } else if ("purchase".equals(mode)) {
            String restBase = System.getProperty("rest.base", "http://localhost:8080");
            ObjectMapper mapper = new ObjectMapper();
            HttpClient client = HttpClient.newHttpClient();

            String playerId = null;
            String playerUsername = System.getProperty("test.player.username", "real_player");
            try {
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(restBase + "/api/players")).GET().build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode arr = mapper.readTree(resp.body());
                    if (arr.isArray() && arr.size() > 0) {
                        int idx = ThreadLocalRandom.current().nextInt(arr.size());
                        JsonNode p = arr.get(idx);
                        playerId = p.get("id").asText();
                        playerUsername = p.get("username").asText(playerUsername);
                    }
                }
            } catch (Exception ignored) {}

            String gameId = null;
            String gameName = "RealGame";
            double price = Double.parseDouble(System.getProperty("test.price", "9.99"));
            String platform = System.getProperty("test.platform", "PC");
            String publisherId = System.getProperty("test.publisher.id", "pub-real-1");
            try {
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(restBase + "/api/catalog")).GET().build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode arr = mapper.readTree(resp.body());
                    if (arr.isArray() && arr.size() > 0) {
                        int idx = ThreadLocalRandom.current().nextInt(arr.size());
                        JsonNode g = arr.get(idx);
                        gameId = g.get("gameId").asText(gameId);
                        gameName = g.get("gameName").asText(gameName);
                        if (g.hasNonNull("price")) price = g.get("price").asDouble(price);
                        if (g.hasNonNull("platform")) platform = g.get("platform").asText(platform);
                        else if (g.hasNonNull("console")) platform = g.get("console").asText(platform);
                        if (g.hasNonNull("publisherId")) publisherId = g.get("publisherId").asText(publisherId);
                    }
                }
            } catch (Exception ignored) {}

            if (playerId == null) playerId = System.getProperty("test.player.id", UUID.randomUUID().toString());
            if (gameId == null) gameId = System.getProperty("test.game.id", UUID.randomUUID().toString());

            try {
                if (playerId != null && gameId != null) {
                    HttpRequest libReq = HttpRequest.newBuilder().uri(URI.create(restBase + "/api/players/" + playerId + "/library")).GET().build();
                    HttpResponse<String> libResp = client.send(libReq, HttpResponse.BodyHandlers.ofString());
                    boolean owned = false;
                    if (libResp.statusCode() == 200) {
                        JsonNode libRoot = mapper.readTree(libResp.body());
                        JsonNode lib = libRoot;
                        if (!libRoot.isArray() && libRoot.has("value") && libRoot.get("value").isArray()) lib = libRoot.get("value");
                        if (lib.isArray()) {
                            for (JsonNode entry : lib) {
                                if (entry.hasNonNull("gameId") && gameId.equals(entry.get("gameId").asText())) { owned = true; break; }
                            }
                        }
                    }

                    if (owned) {
                        try {
                            HttpRequest allReq = HttpRequest.newBuilder().uri(URI.create(restBase + "/api/players")).GET().build();
                            HttpResponse<String> allResp = client.send(allReq, HttpResponse.BodyHandlers.ofString());
                            if (allResp.statusCode() == 200) {
                                JsonNode arr = mapper.readTree(allResp.body());
                                if (!arr.isArray() && arr.has("value") && arr.get("value").isArray()) arr = arr.get("value");
                                java.util.List<JsonNode> candidates = new java.util.ArrayList<>();
                                if (arr.isArray()) { for (JsonNode n : arr) candidates.add(n); }
                                java.util.Collections.shuffle(candidates);
                                for (JsonNode cand : candidates) {
                                    String cid = cand.get("id").asText();
                                    String cun = cand.get("username").asText(playerUsername);
                                    try {
                                        HttpRequest cLibReq = HttpRequest.newBuilder().uri(URI.create(restBase + "/api/players/" + cid + "/library")).GET().build();
                                        HttpResponse<String> cLibResp = client.send(cLibReq, HttpResponse.BodyHandlers.ofString());
                                        boolean cOwned = false;
                                        if (cLibResp.statusCode() == 200) {
                                            JsonNode cLibRoot = mapper.readTree(cLibResp.body());
                                            JsonNode cLib = cLibRoot;
                                            if (!cLibRoot.isArray() && cLibRoot.has("value") && cLibRoot.get("value").isArray()) cLib = cLibRoot.get("value");
                                            if (cLib.isArray()) {
                                                for (JsonNode entry : cLib) {
                                                    if (entry.hasNonNull("gameId") && gameId.equals(entry.get("gameId").asText())) { cOwned = true; break; }
                                                }
                                            }
                                        }
                                        if (!cOwned) { playerId = cid; playerUsername = cun; break; }
                                    } catch (Exception ignore) { /* try next candidate */ }
                                }
                            }
                        } catch (Exception ignore) { /* ignore if we can't fetch players */ }
                    }
                }
            } catch (Exception ignore) {}

            GamePurchaseEvent purchase = GamePurchaseEvent.newBuilder()
                    .setEventId(UUID.randomUUID().toString())
                    .setPurchaseId(UUID.randomUUID().toString())
                    .setGameId(gameId)
                    .setGameName(gameName)
                    .setPlayerId(playerId)
                    .setPlayerUsername(playerUsername)
                    .setPricePaid(price)
                    .setPlatform(platform)
                    .setPublisherId(publisherId)
                    .setRegion(org.steamproject.events.SalesRegion.OTHER)
                    .setTimestamp(Instant.now().toEpochMilli())
                    .build();

            org.steamproject.infra.kafka.producer.GamePurchaseProducer prod = new org.steamproject.infra.kafka.producer.GamePurchaseProducer(bootstrap, sr, System.getProperty("kafka.topic", "game-purchase-events"));
            prod.send(playerId, purchase).get();
            prod.close();
            System.out.println("Sent GamePurchaseEvent player=" + playerId + " game=" + gameId);
        } else if ("dlc_purchase".equals(mode) || "dlcpurchase".equals(mode)) {
            String restBase = System.getProperty("rest.base", "http://localhost:8080");
            ObjectMapper mapper = new ObjectMapper();
            HttpClient client = HttpClient.newHttpClient();

            String playerId = null;
            String playerUsername = System.getProperty("test.player.username", "real_player");
            try {
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(restBase + "/api/players")).GET().build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode arr = mapper.readTree(resp.body());
                    if (arr.isArray() && arr.size() > 0) {
                        int idx = ThreadLocalRandom.current().nextInt(arr.size());
                        JsonNode p = arr.get(idx);
                        playerId = p.get("id").asText();
                        playerUsername = p.get("username").asText(playerUsername);
                    }
                }
            } catch (Exception ignored) {}

            String dlcId = null;
            String dlcName = System.getProperty("test.dlc.name", "Cool DLC");
            String gameId = null;
            double price = Double.parseDouble(System.getProperty("test.price", "4.99"));
            String platform = System.getProperty("test.platform", "PC");
            try {
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(restBase + "/api/catalog")).GET().build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode arr = mapper.readTree(resp.body());
                    java.util.List<JsonNode> gamesWithDlcs = new java.util.ArrayList<>();
                    if (arr.isArray()) {
                        for (JsonNode g : arr) {
                            if (g.has("dlcs") && g.get("dlcs").isArray() && g.get("dlcs").size() > 0) {
                                gamesWithDlcs.add(g);
                            }
                        }
                    }
                    if (!gamesWithDlcs.isEmpty()) {
                        int gi = ThreadLocalRandom.current().nextInt(gamesWithDlcs.size());
                        JsonNode g = gamesWithDlcs.get(gi);
                        gameId = g.get("gameId").asText();
                        JsonNode dlcs = g.get("dlcs");
                        int di = ThreadLocalRandom.current().nextInt(dlcs.size());
                        JsonNode chosen = dlcs.get(di);
                        dlcId = chosen.get("dlcId").asText(java.util.UUID.randomUUID().toString());
                        dlcName = chosen.get("dlcName").asText(dlcName);
                        if (chosen.hasNonNull("price")) price = chosen.get("price").asDouble(price);
                        if (g.hasNonNull("platform")) platform = g.get("platform").asText(platform);
                        else if (g.hasNonNull("console")) platform = g.get("console").asText(platform);
                    }
                }
            } catch (Exception ignored) {}

            if (playerId == null) playerId = System.getProperty("test.player.id", UUID.randomUUID().toString());
            if (gameId == null) gameId = System.getProperty("test.game.id", UUID.randomUUID().toString());
            if (dlcId == null) dlcId = System.getProperty("test.dlc.id", UUID.randomUUID().toString());

            DlcPurchaseEvent dlc = DlcPurchaseEvent.newBuilder()
                    .setPurchaseId(UUID.randomUUID().toString())
                    .setEventId(UUID.randomUUID().toString())
                    .setDlcId(dlcId)
                    .setDlcName(dlcName)
                    .setGameId(gameId)
                    .setPlayerId(playerId)
                    .setPlayerUsername(playerUsername)
                    .setPricePaid(price)
                    .setPlatform(platform)
                    .setTimestamp(Instant.now().toEpochMilli())
                    .build();

            Properties props = new Properties();
            props.put("bootstrap.servers", bootstrap);
            props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            props.put("value.serializer", KafkaAvroSerializer.class.getName());
            props.put("schema.registry.url", sr);

            try (KafkaProducer<String, Object> prod = new KafkaProducer<>(props)) {
                prod.send(new ProducerRecord<>(System.getProperty("kafka.topic.dlc", "dlc-purchase-events"), playerId, dlc)).get();
                System.out.println("Sent DlcPurchaseEvent player=" + playerId + " dlc=" + dlcId);
            }
        } else if ("launch".equals(mode) || "launched".equals(mode)) {
            String restBase = System.getProperty("rest.base", "http://localhost:8080");
            ObjectMapper mapper = new ObjectMapper();
            HttpClient client = HttpClient.newHttpClient();

            String playerId = System.getProperty("test.player.id", null);
            String playerUsername = System.getProperty("test.player.username", "real_player");
            String gameId = null;
            String gameName = System.getProperty("test.game.name", "RealGame");

            if (playerId == null) {
                try {
                    HttpRequest req = HttpRequest.newBuilder().uri(URI.create(restBase + "/api/players")).GET().build();
                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                        if (resp.statusCode() == 200) {
                        JsonNode root = mapper.readTree(resp.body());
                        try { String rs = root.toString(); } catch (Throwable _t) {}
                        JsonNode arr = root;
                        if (!root.isArray() && root.has("value") && root.get("value").isArray()) arr = root.get("value");
                        if (arr.isArray() && arr.size() > 0) {
                            java.util.List<Integer> idxs = new java.util.ArrayList<>();
                            for (int i = 0; i < arr.size(); i++) idxs.add(i);
                            java.util.Collections.shuffle(idxs);
                                for (int i : idxs) {
                                JsonNode p = arr.get(i);
                                String candidateId = p.get("id").asText();
                                try {
                                    HttpRequest libReq = HttpRequest.newBuilder().uri(URI.create(restBase + "/api/players/" + candidateId + "/library")).GET().build();
                                    HttpResponse<String> libResp = client.send(libReq, HttpResponse.BodyHandlers.ofString());
                                    if (libResp.statusCode() == 200) {
                                        try { String ls = libResp.body(); } catch (Throwable _t) {}
                                        JsonNode libRoot = mapper.readTree(libResp.body());
                                        JsonNode lib = libRoot;
                                        if (!libRoot.isArray() && libRoot.has("value") && libRoot.get("value").isArray()) lib = libRoot.get("value");
                                        if (lib.isArray() && lib.size() > 0) {
                                            playerId = candidateId;
                                            playerUsername = p.get("username").asText(playerUsername);
                                            break;
                                        }
                                    }
                                } catch (Exception ignore2) { /* try next player */ }
                            }
                        }
                    }
                } catch (Exception ex) { ex.printStackTrace(); }
            }

            String purchaseDateStr = null;
            if (playerId != null) {
                try {
                    HttpRequest req = HttpRequest.newBuilder().uri(URI.create(restBase + "/api/players/" + playerId + "/library")).GET().build();
                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        JsonNode libRoot = mapper.readTree(resp.body());
                        JsonNode arrLib = libRoot;
                        if (!libRoot.isArray() && libRoot.has("value") && libRoot.get("value").isArray()) arrLib = libRoot.get("value");
                        if (arrLib.isArray() && arrLib.size() > 0) {
                            JsonNode entry = arrLib.get(ThreadLocalRandom.current().nextInt(arrLib.size()));
                            if (entry.hasNonNull("gameId")) gameId = entry.get("gameId").asText();
                            if (entry.hasNonNull("gameName")) gameName = entry.get("gameName").asText(gameName);
                            if (entry.hasNonNull("purchaseDate")) purchaseDateStr = entry.get("purchaseDate").asText();
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (playerId == null || gameId == null) {
                System.err.println("No player or owned game available for launch; aborting");
                return;
            }

            int duration = 60 + ThreadLocalRandom.current().nextInt(541); // always random 60..600
            long timestamp = randomTimestampBetween(purchaseDateStr);

            GameSessionEvent ev = GameSessionEvent.newBuilder()
                    .setSessionId(UUID.randomUUID().toString())
                    .setGameId(gameId)
                    .setGameName(gameName)
                    .setPlayerId(playerId)
                    .setPlayerUsername(playerUsername)
                    .setSessionDuration(duration)
                    .setSessionType(org.steamproject.events.SessionType.FIRST_LAUNCH)
                    .setTimestamp(timestamp)
                    .build();

            Properties props = new Properties();
            props.put("bootstrap.servers", bootstrap);
            props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            props.put("value.serializer", KafkaAvroSerializer.class.getName());
            props.put("schema.registry.url", sr);

            try (KafkaProducer<String, Object> prod = new KafkaProducer<>(props)) {
                prod.send(new ProducerRecord<>(System.getProperty("kafka.topic.session", "game-session-events"), playerId, ev)).get();
                System.out.println("Sent GameSessionEvent (launch) player=" + playerId + " game=" + gameId);
            }
        } else if ("stop".equals(mode) || "stopped".equals(mode)) {
            String restBase = System.getProperty("rest.base", "http://localhost:8080");
            ObjectMapper mapper = new ObjectMapper();
            HttpClient client = HttpClient.newHttpClient();

            String playerId = System.getProperty("test.player.id", null);
            String playerUsername = System.getProperty("test.player.username", "real_player");
            String gameId = null;
            String gameName = System.getProperty("test.game.name", "RealGame");
            String purchaseDateStr = null;
            int duration = 60 + ThreadLocalRandom.current().nextInt(541); // always random 60..600

            if (playerId == null) {
                try {
                    HttpRequest req = HttpRequest.newBuilder().uri(URI.create(restBase + "/api/players")).GET().build();
                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        JsonNode root = mapper.readTree(resp.body());
                        JsonNode arr = root;
                        if (!root.isArray() && root.has("value") && root.get("value").isArray()) arr = root.get("value");
                        try { String rs = arr.toString(); } catch (Throwable _t) {}
                        if (arr.isArray() && arr.size() > 0) {
                            java.util.List<Integer> idxs = new java.util.ArrayList<>();
                            for (int i = 0; i < arr.size(); i++) idxs.add(i);
                            java.util.Collections.shuffle(idxs);
                            for (int i : idxs) {
                                JsonNode p = arr.get(i);
                                String candidateId = p.get("id").asText();
                                try {
                                    HttpRequest libReq = HttpRequest.newBuilder().uri(URI.create(restBase + "/api/players/" + candidateId + "/library")).GET().build();
                                    HttpResponse<String> libResp = client.send(libReq, HttpResponse.BodyHandlers.ofString());
                                    try { String ls = libResp.body(); } catch (Throwable _t) {}
                                    if (libResp.statusCode() == 200) {
                                        JsonNode libRoot = mapper.readTree(libResp.body());
                                        JsonNode lib = libRoot;
                                        if (!libRoot.isArray() && libRoot.has("value") && libRoot.get("value").isArray()) lib = libRoot.get("value");
                                        if (lib.isArray() && lib.size() > 0) {
                                            playerId = candidateId;
                                            playerUsername = p.get("username").asText(playerUsername);
                                            break;
                                        }
                                    }
                                } catch (Exception ignore2) { /* try next player */ }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (playerId != null) {
                try {
                    HttpRequest req = HttpRequest.newBuilder().uri(URI.create(restBase + "/api/players/" + playerId + "/library")).GET().build();
                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        JsonNode libRoot = mapper.readTree(resp.body());
                        JsonNode arrLib = libRoot;
                        if (!libRoot.isArray() && libRoot.has("value") && libRoot.get("value").isArray()) arrLib = libRoot.get("value");
                        if (arrLib.isArray() && arrLib.size() > 0) {
                            JsonNode entry = arrLib.get(ThreadLocalRandom.current().nextInt(arrLib.size()));
                            if (entry.hasNonNull("gameId")) gameId = entry.get("gameId").asText();
                            if (entry.hasNonNull("gameName")) gameName = entry.get("gameName").asText(gameName);
                            if (entry.hasNonNull("purchaseDate")) purchaseDateStr = entry.get("purchaseDate").asText();
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (playerId == null || gameId == null) {
                System.err.println("No player or owned game available for stop; aborting");
                return;
            }

                long timestamp = randomTimestampBetween(purchaseDateStr);
                GameSessionEvent ev = GameSessionEvent.newBuilder()
                    .setSessionId(UUID.randomUUID().toString())
                    .setGameId(gameId)
                    .setGameName(gameName)
                    .setPlayerId(playerId)
                    .setPlayerUsername(playerUsername)
                    .setSessionDuration(duration)
                    .setSessionType(org.steamproject.events.SessionType.CASUAL)
                    .setTimestamp(timestamp)
                    .build();

            Properties props = new Properties();
            props.put("bootstrap.servers", bootstrap);
            props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            props.put("value.serializer", KafkaAvroSerializer.class.getName());
            props.put("schema.registry.url", sr);

            try (KafkaProducer<String, Object> prod = new KafkaProducer<>(props)) {
                prod.send(new ProducerRecord<>(System.getProperty("kafka.topic.session", "game-session-events"), playerId, ev)).get();
                System.out.println("Sent GameSessionEvent (stop) player=" + playerId + " game=" + gameId + " duration=" + duration);
            }
        } else if ("playsession".equals(mode) || "play_session".equals(mode)) {
            String restBase = System.getProperty("rest.base", "http://localhost:8080");
            ObjectMapper mapper = new ObjectMapper();
            HttpClient client = HttpClient.newHttpClient();
            String purchaseDateStr = null;
            String playerId = System.getProperty("test.player.id", null);
            String playerUsername = System.getProperty("test.player.username", "real_player");
            String gameId = null;
            String gameName = System.getProperty("test.game.name", "RealGame");
            int duration = 60 + ThreadLocalRandom.current().nextInt(541); // always random 60..600

            if (playerId == null) {
                try {
                    HttpRequest req = HttpRequest.newBuilder().uri(URI.create(restBase + "/api/players")).GET().build();
                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        String raw = resp.body();
                        try { String __tmp = raw.substring(0, Math.min(400, raw.length())); } catch (Throwable _t) {}
                        JsonNode root = mapper.readTree(raw);
                        JsonNode arr = root;
                        if (!root.isArray() && root.has("value") && root.get("value").isArray()) arr = root.get("value");
                        if (arr.isArray() && arr.size() > 0) {
                            java.util.List<Integer> idxs = new java.util.ArrayList<>();
                            for (int i = 0; i < arr.size(); i++) idxs.add(i);
                            java.util.Collections.shuffle(idxs);
                            for (int i : idxs) {
                                JsonNode p = arr.get(i);
                                String candidateId = p.get("id").asText();
                                try {
                                    HttpRequest libReq = HttpRequest.newBuilder().uri(URI.create(restBase + "/api/players/" + candidateId + "/library")).GET().build();
                                    HttpResponse<String> libResp = client.send(libReq, HttpResponse.BodyHandlers.ofString());
                                    if (libResp.statusCode() == 200) {
                                        String libRaw = libResp.body();
                                        JsonNode libRoot = mapper.readTree(libRaw);
                                        JsonNode lib = libRoot;
                                        if (!libRoot.isArray() && libRoot.has("value") && libRoot.get("value").isArray()) lib = libRoot.get("value");
                                        if (lib.isArray() && lib.size() > 0) {
                                            playerId = candidateId;
                                            playerUsername = p.get("username").asText(playerUsername);
                                            break;
                                        }
                                    }
                                } catch (Exception e) { /* ignore candidate errors */ }
                            }
                        }
                    }
                } catch (Exception ex) { ex.printStackTrace(); }
            }

            if (playerId != null) {
                try {
                    HttpRequest req = HttpRequest.newBuilder().uri(URI.create(restBase + "/api/players/" + playerId + "/library")).GET().build();
                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    String rawLib = resp.body();
                    try { String __tmp2 = rawLib.substring(0, Math.min(400, rawLib.length())); } catch (Throwable _t) {}
                    if (resp.statusCode() == 200) {
                        JsonNode rootLib = mapper.readTree(rawLib);
                        JsonNode arr = rootLib;
                        if (!rootLib.isArray() && rootLib.has("value") && rootLib.get("value").isArray()) arr = rootLib.get("value");
                        if (arr.isArray() && arr.size() > 0) {
                            JsonNode entry = arr.get(ThreadLocalRandom.current().nextInt(arr.size()));
                            if (entry.hasNonNull("gameId")) gameId = entry.get("gameId").asText();
                            if (entry.hasNonNull("gameName")) gameName = entry.get("gameName").asText(gameName);
                            if (entry.hasNonNull("purchaseDate")) purchaseDateStr = entry.get("purchaseDate").asText();
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (playerId == null || gameId == null) {
                System.err.println("No player or owned game available for playsession; aborting");
                return;
            }

                long timestamp = randomTimestampBetween(purchaseDateStr);

                GameSessionEvent ev = GameSessionEvent.newBuilder()
                    .setSessionId(UUID.randomUUID().toString())
                    .setGameId(gameId)
                    .setGameName(gameName)
                    .setPlayerId(playerId)
                    .setPlayerUsername(playerUsername)
                    .setSessionDuration(duration)
                    .setSessionType(org.steamproject.events.SessionType.CASUAL)
                    .setTimestamp(timestamp)
                    .build();

            Properties props = new Properties();
            props.put("bootstrap.servers", bootstrap);
            props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            props.put("value.serializer", KafkaAvroSerializer.class.getName());
            props.put("schema.registry.url", sr);

            try (KafkaProducer<String, Object> prod = new KafkaProducer<>(props)) {
                prod.send(new ProducerRecord<>(System.getProperty("kafka.topic.session", "game-session-events"), playerId, ev)).get();
                System.out.println("Sent GameSessionEvent (playsession) player=" + playerId + " game=" + gameId + " duration=" + duration);
            }
        } else if ("crash".equals(mode) || "crash_reported".equals(mode)) {
            String restBase = System.getProperty("rest.base", "http://localhost:8080");
            ObjectMapper mapper = new ObjectMapper();
            HttpClient client = HttpClient.newHttpClient();

            String crashId = UUID.randomUUID().toString();
            String playerId = System.getProperty("test.player.id", null);
            String playerUsername = System.getProperty("test.player.username", "real_player");

            if (playerId == null) {
                try {
                    HttpRequest req = HttpRequest.newBuilder().uri(URI.create(restBase + "/api/players")).GET().build();
                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        JsonNode arr = mapper.readTree(resp.body());
                        if (arr.isArray() && arr.size() > 0) {
                            int idx = ThreadLocalRandom.current().nextInt(arr.size());
                            JsonNode p = arr.get(idx);
                            playerId = p.get("id").asText();
                            playerUsername = p.get("username").asText(playerUsername);
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (playerId == null) playerId = UUID.randomUUID().toString();

            String gameId = System.getProperty("test.game.id", null);
            String gameName = System.getProperty("test.game.name", "RealGame");
            String platform = System.getProperty("test.platform", "PC");

            if (gameId == null) {
                try {
                    HttpRequest req = HttpRequest.newBuilder().uri(URI.create(restBase + "/api/players/" + playerId + "/library")).GET().build();
                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        JsonNode arr = mapper.readTree(resp.body());
                        if (arr.isArray() && arr.size() > 0) {
                            int idx = ThreadLocalRandom.current().nextInt(arr.size());
                            JsonNode entry = arr.get(idx);
                            if (entry.hasNonNull("gameId")) gameId = entry.get("gameId").asText(gameId);
                            if (entry.hasNonNull("gameName")) gameName = entry.get("gameName").asText(gameName);
                            if (entry.hasNonNull("platform")) platform = entry.get("platform").asText(platform);
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Fallback: pick random from catalog
            if (gameId == null) {
                try {
                    HttpRequest req = HttpRequest.newBuilder().uri(URI.create(restBase + "/api/catalog")).GET().build();
                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        JsonNode arr = mapper.readTree(resp.body());
                        if (arr.isArray() && arr.size() > 0) {
                            int idx = ThreadLocalRandom.current().nextInt(arr.size());
                            JsonNode g = arr.get(idx);
                            gameId = g.hasNonNull("gameId") ? g.get("gameId").asText() : UUID.randomUUID().toString();
                            gameName = g.hasNonNull("gameName") ? g.get("gameName").asText(gameName) : gameName;
                            if (g.hasNonNull("platform")) platform = g.get("platform").asText(platform);
                            else if (g.hasNonNull("console")) platform = g.get("console").asText(platform);
                        }
                    }
                } catch (Exception ignored) {}
            }

            CrashReportEvent ev = CrashReportEvent.newBuilder()
                    .setCrashId(crashId)
                    .setGameId(gameId == null ? UUID.randomUUID().toString() : gameId)
                    .setGameName(gameName)
                    .setPlayerId(playerId)
                    .setEditeurId(System.getProperty("test.publisher.id", "pub-real-1"))
                    .setEditeurName(System.getProperty("test.publisher.name", "Publisher"))
                    .setGameVersion(System.getProperty("test.game.version", "1.0.0"))
                    .setPlatform(platform)
                    .setSeverity(org.steamproject.events.CrashSeverity.MODERATE)
                    .setErrorType(System.getProperty("test.error.type", "UnknownError"))
                    .setErrorMessage(System.getProperty("test.error.message", null))
                    .setTimestamp(Instant.now().toEpochMilli())
                    .build();

            Properties props = new Properties();
            props.put("bootstrap.servers", bootstrap);
            props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            props.put("value.serializer", KafkaAvroSerializer.class.getName());
            props.put("schema.registry.url", sr);

            try (KafkaProducer<String, Object> prod = new KafkaProducer<>(props)) {
                prod.send(new ProducerRecord<>(System.getProperty("kafka.topic.crash", "crash-report-events"), crashId, ev)).get();
                System.out.println("Sent CrashReportEvent crashId=" + crashId + " game=" + ev.getGameId());
            }
        } else if ("rate".equals(mode) || "rating".equals(mode)) {
            String restBase = System.getProperty("rest.base", "http://localhost:8080");
            ObjectMapper mapper = new ObjectMapper();
            HttpClient client = HttpClient.newHttpClient();

            String playerId = System.getProperty("test.player.id", null);
            String playerUsername = System.getProperty("test.player.username", "real_player");

            String gameId = null;
            String gameName = System.getProperty("test.game.name", "RealGame");
            int playtime = 0;

            try {
                // Fetch all players once and attempt up to N times to find one with a game >10h
                HttpRequest allReq = HttpRequest.newBuilder().uri(URI.create(restBase + "/api/players")).GET().build();
                HttpResponse<String> allResp = client.send(allReq, HttpResponse.BodyHandlers.ofString());
                if (allResp.statusCode() != 200) {
                    System.err.println("Failed to fetch players list; aborting");
                    return;
                }
                JsonNode playersArr = mapper.readTree(allResp.body());
                if (!playersArr.isArray() || playersArr.size() == 0) {
                    System.err.println("No players available to rate from; aborting");
                    return;
                }

                java.util.List<JsonNode> players = new java.util.ArrayList<>();
                for (JsonNode p : playersArr) players.add(p);
                int maxAttempts = players.size();

                for (int attempt = 0; attempt < maxAttempts && gameId == null; attempt++) {
                    // choose random player
                    int idx = ThreadLocalRandom.current().nextInt(players.size());
                    JsonNode p = players.get(idx);
                    String candidateId = p.get("id").asText();
                    String candidateUsername = p.get("username").asText(playerUsername);

                    // fetch library for candidate
                    try {
                        HttpRequest libReq = HttpRequest.newBuilder().uri(URI.create(restBase + "/api/players/" + candidateId + "/library")).GET().build();
                        HttpResponse<String> libResp = client.send(libReq, HttpResponse.BodyHandlers.ofString());
                        if (libResp.statusCode() == 200) {
                            JsonNode libArr = mapper.readTree(libResp.body());
                            if (libArr.isArray() && libArr.size() > 0) {
                                java.util.List<JsonNode> candidates = new java.util.ArrayList<>();
                                for (JsonNode e : libArr) {
                                    int pt = e.hasNonNull("playtime") ? e.get("playtime").asInt(0) : 0;
                                    if (pt > 10) candidates.add(e);
                                }
                                if (!candidates.isEmpty()) {
                                    JsonNode chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
                                    gameId = chosen.hasNonNull("gameId") ? chosen.get("gameId").asText() : null;
                                    gameName = chosen.hasNonNull("gameName") ? chosen.get("gameName").asText(gameName) : gameName;
                                    playtime = chosen.hasNonNull("playtime") ? chosen.get("playtime").asInt(0) : 0;
                                    playerId = candidateId;
                                    playerUsername = candidateUsername;
                                    break; // found suitable player+game
                                }
                            }
                        }
                    } catch (Exception ignored) { /* try next candidate */ }
                }
            } catch (Exception ignored) {}

            if (gameId == null) {
                System.err.println("No owned game with >10h playtime found after trying players; aborting");
                return;
            }

            int rating = Integer.parseInt(System.getProperty("test.rating", Integer.toString(3 + ThreadLocalRandom.current().nextInt(3))));
            boolean recommend = Boolean.parseBoolean(System.getProperty("test.recommend", rating >= 4 ? "true" : "false"));
            String comment = System.getProperty("test.comment", null);

            NewRatingEvent ev = NewRatingEvent.newBuilder()
                    .setGameId(gameId)
                    .setGameName(gameName)
                    .setPlayerId(playerId)
                    .setPlayerUsername(playerUsername)
                    .setRating(rating)
                    .setComment(comment)
                    .setPlaytime(playtime)
                    .setIsRecommended(recommend)
                    .setTimestamp(Instant.now().toEpochMilli())
                    .build();

            Properties props = new Properties();
            props.put("bootstrap.servers", bootstrap);
            props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            props.put("value.serializer", KafkaAvroSerializer.class.getName());
            props.put("schema.registry.url", sr);

            try (KafkaProducer<String, Object> prod = new KafkaProducer<>(props)) {
                prod.send(new ProducerRecord<>(System.getProperty("kafka.topic.rating", "new-rating-events"), playerId, ev)).get();
                System.out.println("Sent NewRatingEvent player=" + playerId + " game=" + gameId + " rating=" + rating + " playtime=" + playtime);
            }
        } else if ("review_publish".equals(mode) || "publish_review".equals(mode)) {
            String reviewId = UUID.randomUUID().toString();
            String playerId = System.getProperty("test.player.id", UUID.randomUUID().toString());
            String gameId = System.getProperty("test.game.id", UUID.randomUUID().toString());
            String username = System.getProperty("test.player.username", "real_player");
            int rating = Integer.parseInt(System.getProperty("test.rating", "5"));

            ReviewPublishedEvent ev = ReviewPublishedEvent.newBuilder()
                    .setReviewId(reviewId)
                    .setGameId(gameId)
                    .setPlayerId(playerId)
                    .setPlayerUsername(username)
                    .setRating(rating)
                    .setTitle(System.getProperty("test.review.title", null))
                    .setText(System.getProperty("test.review.text", null))
                    .setIsSpoiler(Boolean.parseBoolean(System.getProperty("test.review.spoiler", "false")))
                    .setTimestamp(Instant.now().toEpochMilli())
                    .build();

            Properties props = new Properties();
            props.put("bootstrap.servers", bootstrap);
            props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            props.put("value.serializer", KafkaAvroSerializer.class.getName());
            props.put("schema.registry.url", sr);

            try (KafkaProducer<String, Object> prod = new KafkaProducer<>(props)) {
                prod.send(new ProducerRecord<>(System.getProperty("kafka.topic.review", "review-published-events"), reviewId, ev)).get();
                System.out.println("Sent ReviewPublishedEvent reviewId=" + reviewId + " player=" + playerId + " game=" + gameId);
            }
        } else if ("review_vote".equals(mode) || "review_vote_publish".equals(mode)) {
            String reviewId = System.getProperty("test.review.id", UUID.randomUUID().toString());
            String voter = System.getProperty("test.player.id", UUID.randomUUID().toString());
            boolean helpful = Boolean.parseBoolean(System.getProperty("test.review.helpful", "true"));

            ReviewVotedEvent ev = ReviewVotedEvent.newBuilder()
                    .setReviewId(reviewId)
                    .setVoterPlayerId(voter)
                    .setIsHelpful(helpful)
                    .setTimestamp(Instant.now().toEpochMilli())
                    .build();

            Properties props = new Properties();
            props.put("bootstrap.servers", bootstrap);
            props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            props.put("value.serializer", KafkaAvroSerializer.class.getName());
            props.put("schema.registry.url", sr);

            try (KafkaProducer<String, Object> prod = new KafkaProducer<>(props)) {
                prod.send(new ProducerRecord<>(System.getProperty("kafka.topic.review.vote", "review-voted-events"), reviewId, ev)).get();
                System.out.println("Sent ReviewVotedEvent reviewId=" + reviewId + " voter=" + voter + " helpful=" + helpful);
            }
        } else {
            System.err.println("Unknown mode: " + mode + " (expected create|purchase|dlc_purchase|launch|stop|playsession|crash|rate|review_publish|review_vote)");
        }
    }
}
