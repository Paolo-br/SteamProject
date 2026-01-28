package org.steamproject.infra.kafka.consumer;

import java.util.Collections;
import java.util.Properties;


public class PublisherConsumer {
    private final org.apache.kafka.clients.consumer.KafkaConsumer<String, Object> consumer;
    private final String topic;

    public PublisherConsumer(String bootstrap, String schemaRegistryUrl, String topic, String groupId) {
        this.topic = topic;
        java.util.Properties props = new java.util.Properties();
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringDeserializer.class.getName());
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, io.confluent.kafka.serializers.KafkaAvroDeserializer.class.getName());
        props.put("schema.registry.url", schemaRegistryUrl);
        props.put("specific.avro.reader", "true");
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        this.consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(props);
        java.util.List<String> topics = new java.util.ArrayList<>();
        for (String t : topic.split(",")) {
            String tt = t.trim();
            if (!tt.isEmpty()) topics.add(tt);
        }
        if (topics.isEmpty()) topics.add(topic);
        this.consumer.subscribe(topics);
    }

    public void start() {
        System.out.println("PublisherConsumer started, listening to " + topic);
        try {
            while (true) {
                var recs = consumer.poll(java.time.Duration.ofSeconds(1));
                recs.forEach(r -> {
                    try {
                        Object val = r.value();
                        if (val instanceof org.steamproject.events.GameReleasedEvent) {
                                    org.steamproject.events.GameReleasedEvent evt = (org.steamproject.events.GameReleasedEvent) val;
                                    handleGameReleased(evt);
                                } else if (val instanceof org.steamproject.events.GamePublishedEvent) {
                                    org.steamproject.events.GamePublishedEvent evt = (org.steamproject.events.GamePublishedEvent) val;
                                    handleGamePublished(evt);
                                } else if (val instanceof org.steamproject.events.GameUpdatedEvent) {
                                    org.steamproject.events.GameUpdatedEvent evt = (org.steamproject.events.GameUpdatedEvent) val;
                                    handleGameUpdated(evt);
                                } else if (val instanceof org.steamproject.events.PatchPublishedEvent) {
                                    org.steamproject.events.PatchPublishedEvent evt = (org.steamproject.events.PatchPublishedEvent) val;
                                    handlePatchPublished(evt);
                                } else if (val instanceof org.steamproject.events.DlcPublishedEvent) {
                                    org.steamproject.events.DlcPublishedEvent evt = (org.steamproject.events.DlcPublishedEvent) val;
                                    handleDlcPublished(evt);
                                } else if (val instanceof org.steamproject.events.GameVersionDeprecatedEvent) {
                                    org.steamproject.events.GameVersionDeprecatedEvent evt = (org.steamproject.events.GameVersionDeprecatedEvent) val;
                                    handleGameVersionDeprecated(evt);
                                } else if (val instanceof org.steamproject.events.EditorRespondedToIncidentEvent) {
                                    org.steamproject.events.EditorRespondedToIncidentEvent evt = (org.steamproject.events.EditorRespondedToIncidentEvent) val;
                                    handleEditorResponse(evt);
                                } else {
                                    System.out.println("PublisherConsumer ignored event type: " + (val != null ? val.getClass() : null));
                                }
                    } catch (Exception ex) { ex.printStackTrace(); }
                });
            }
        } finally {
            consumer.close();
        }
    }

    public void handleGameReleased(org.steamproject.events.GameReleasedEvent evt) {
        try {
            String eventId = evt.getEventId() == null ? "" : evt.getEventId().toString();
            if (!PublisherProjection.getInstance().markEventIfNew(eventId)) {
                System.out.println("PublisherConsumer skipped duplicate eventId=" + eventId);
                return;
            }

            String pubId = evt.getPublisherId().toString();
            String gameId = evt.getGameId().toString();
            String gameName = evt.getGameName() == null ? null : evt.getGameName().toString();
            Integer releaseYear = evt.getReleaseYear();
            String genre = evt.getGenre() == null ? null : evt.getGenre().toString();
            String platform = evt.getPlatform() == null ? null : evt.getPlatform().toString();
            String initialVersion = evt.getInitialVersion() == null ? null : evt.getInitialVersion().toString();
            Double initialPrice = evt.getInitialPrice();

            // maintain simple publisher list entry (gameId|gameName|releaseYear) for compatibility
            String summary = gameId + "|" + (gameName == null ? "" : gameName) + "|" + (releaseYear == null ? "" : releaseYear);
            PublisherProjection.getInstance().addPublishedGame(pubId, summary);

            // infer distribution platform id from hardware/platform code
            String hwConsole = platform;
            org.steamproject.model.DistributionPlatform dp = org.steamproject.model.DistributionPlatform.inferFromHardwareCode(hwConsole);
            String distributionPlatform = dp == null ? "steam" : dp.getId();
            GameProjection.getInstance().upsertGame(gameId, distributionPlatform, hwConsole, gameName, releaseYear, genre, pubId, initialVersion, initialPrice);

            try {
                String platformId = distributionPlatform;
                String platTopic = System.getProperty("kafka.topic.platform", "platform-catalog-events");
                String bootstrap = System.getProperty("kafka.bootstrap", "localhost:9092");
                String sr = System.getProperty("schema.registry", "http://localhost:8081");
                org.steamproject.infra.kafka.producer.PlatformProducer pprod = new org.steamproject.infra.kafka.producer.PlatformProducer(bootstrap, sr, platTopic);
                org.steamproject.events.PlatformCatalogUpdateEvent pc = org.steamproject.events.PlatformCatalogUpdateEvent.newBuilder()
                        .setPlatformId(platformId)
                        .setGameId(gameId)
                        .setGameName(gameName)
                        .setAction(org.steamproject.events.CatalogAction.ADD)
                        .setTimestamp(System.currentTimeMillis())
                        .build();
                try { pprod.sendCatalogUpdate(gameId, pc).get(); } catch (Exception e) { /* best-effort */ }
                pprod.close();
            } catch (Throwable t) { /* ignore platform publish failures */ }
            System.out.println("PublisherConsumer processed game released publisher=" + pubId + " game=" + gameId + " platform=" + platform + " genre=" + genre);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    public void handleGamePublished(org.steamproject.events.GamePublishedEvent evt) {
        try {
            String pubId = evt.getPublisherId().toString();
            String gameId = evt.getGameId().toString();
            String gameName = evt.getGameName() == null ? null : evt.getGameName().toString();
            String platform = evt.getPlatform() == null ? null : evt.getPlatform().toString();
            String genre = evt.getGenre() == null ? null : evt.getGenre().toString();
            String initialVersion = evt.getInitialVersion() == null ? null : evt.getInitialVersion().toString();
            Double initialPrice = evt.getInitialPrice();
            Integer releaseYear = null;

            PublisherProjection.getInstance().addPublishedGame(pubId, gameId + "|" + (gameName == null ? "" : gameName) + "|" + (releaseYear == null ? "" : releaseYear));
            String hwConsole = platform;
            org.steamproject.model.DistributionPlatform dp = org.steamproject.model.DistributionPlatform.inferFromHardwareCode(hwConsole);
            String distributionPlatform = dp == null ? "steam" : dp.getId();
            GameProjection.getInstance().upsertGame(gameId, distributionPlatform, hwConsole, gameName, releaseYear, genre, pubId, initialVersion, initialPrice);
            System.out.println("PublisherConsumer processed game published publisher=" + pubId + " game=" + gameId);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    public void handleGameUpdated(org.steamproject.events.GameUpdatedEvent evt) {
        try {
            String gameId = evt.getGameId().toString();
            var updates = evt.getUpdatedFields();
            if (updates != null) {
                updates.forEach((k, v) -> {
                    try {
                        storeUpsertField(gameId, k, v == null ? null : v.toString());
                    } catch (Exception ignored) {}
                });
            }
            System.out.println("PublisherConsumer applied game-updated for " + gameId);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void storeUpsertField(String gameId, String key, String value) {
        var gd = GameProjection.getInstance().getGame(gameId);
        if (gd == null) return;
        java.util.Map<String,Object> m = new java.util.HashMap<>(gd);
        if (value == null) m.remove(key); else m.put(key, value);
        String distributionPlatform = (String) m.get("distributionPlatform");
        String console = (String) m.get("console");
        GameProjection.getInstance().upsertGame(gameId, distributionPlatform, console, (String) m.get("gameName"), (Integer) m.get("releaseYear"), (String) m.get("genre"), (String) m.get("publisherId"), (String) m.get("initialVersion"), (Double) m.get("price"));
    }

    public void handlePatchPublished(org.steamproject.events.PatchPublishedEvent evt) {
        try {
            String gameId = evt.getGameId().toString();
            // patchId not present in schema; derive one
            String patchId = gameId + "-patch-" + Long.toString(evt.getTimestamp());
            String oldVersion = evt.getOldVersion() == null ? "" : evt.getOldVersion().toString();
            String newVersion = evt.getNewVersion() == null ? "" : evt.getNewVersion().toString();
            String desc = evt.getChangeLog() == null ? null : evt.getChangeLog().toString();
            Long ts = evt.getTimestamp();
            GameProjection.getInstance().addPatch(gameId, patchId, oldVersion, newVersion, desc, ts == null ? null : ts);
            System.out.println("PublisherConsumer added patch " + patchId + " for game=" + gameId + " newVersion=" + newVersion);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    public void handleDlcPublished(org.steamproject.events.DlcPublishedEvent evt) {
        try {
            String gameId = evt.getGameId().toString();
            String dlcId = evt.getDlcId() == null ? java.util.UUID.randomUUID().toString() : evt.getDlcId().toString();
            String dlcName = evt.getDlcName() == null ? null : evt.getDlcName().toString();
            Double price = evt.getPrice();
            Long ts = evt.getReleaseTimestamp();
            GameProjection.getInstance().addDlc(gameId, dlcId, dlcName, price, ts == null ? null : ts);
            System.out.println("PublisherConsumer added DLC " + dlcId + " for game=" + gameId);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    public void handleGameVersionDeprecated(org.steamproject.events.GameVersionDeprecatedEvent evt) {
        try {
            String gameId = evt.getGameId().toString();
            String dep = evt.getDeprecatedVersion() == null ? null : evt.getDeprecatedVersion().toString();
            Long at = evt.getDeprecatedAt();
            if (dep != null) GameProjection.getInstance().deprecateVersion(gameId, dep, at == null ? null : at);
            System.out.println("PublisherConsumer deprecated version " + dep + " for game=" + gameId);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    public void handleEditorResponse(org.steamproject.events.EditorRespondedToIncidentEvent evt) {
        try {
            String gameId = evt.getGameId().toString();
            String incidentId = evt.getIncidentId() == null ? null : evt.getIncidentId().toString();
            String resp = evt.getResponseMessage() == null ? null : evt.getResponseMessage().toString();
            Long ts = evt.getResponseTimestamp();
            GameProjection.getInstance().addEditorResponse(gameId, incidentId, resp, ts == null ? null : ts);
            System.out.println("PublisherConsumer recorded editor response for incident=" + incidentId + " game=" + gameId);
        } catch (Exception ex) { ex.printStackTrace(); }
    }
}
