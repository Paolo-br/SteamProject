package org.steamproject.infra.kafka.consumer;

import java.util.Collections;
import java.util.Properties;

/**
 * Consommateur Kafka pour les événements liés aux éditeurs de jeux.
 * 
 * Ce consommateur traite les événements de cycle de vie des jeux publiés par les éditeurs :
 * sorties de jeux, publications, mises à jour, patches, DLCs, dépréciations de versions
 * et réponses aux incidents. Il maintient à jour les projections GameProjection et
 * PublisherProjection, et peut déclencher des événements downstream vers d'autres topics.
 */
public class PublisherConsumer {
    private final org.apache.kafka.clients.consumer.KafkaConsumer<String, Object> consumer;
    private final String topic;

    /**
     * Construit un nouveau consommateur Kafka pour les événements d'éditeurs.
     * 
     * Configure la désérialisation Avro avec Schema Registry et supporte
     * la souscription à plusieurs topics séparés par des virgules.
     * 
     * @param bootstrap Adresse du serveur Kafka (ex: localhost:9092)
     * @param schemaRegistryUrl URL du Schema Registry Confluent
     * @param topic Nom du topic à consommer, ou liste de topics séparés par des virgules
     * @param groupId Identifiant du groupe de consommateurs
     */
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

    /**
     * Démarre la boucle de consommation bloquante des événements.
     * 
     * Cette méthode bloque le thread courant et dispatche chaque événement
     * vers le handler approprié selon son type (GameReleased, GamePublished,
     * PatchPublished, DlcPublished, etc.). Les événements non reconnus sont
     * ignorés avec un message de log.
     * 
     * La méthode garantit la fermeture propre du consommateur en cas d'arrêt.
     */
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

    /**
     * Traite un événement de sortie de jeu.
     * 
     * Vérifie la déduplication via l'eventId, ajoute le jeu au catalogue de l'éditeur,
     * met à jour la projection du jeu, infère la plateforme de distribution depuis
     * le code hardware, et publie un événement de mise à jour du catalogue de plateforme
     * de manière best-effort.
     * 
     * @param evt Événement de sortie de jeu à traiter
     */
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

            String summary = gameId + "|" + (gameName == null ? "" : gameName) + "|" + (releaseYear == null ? "" : releaseYear);
            PublisherProjection.getInstance().addPublishedGame(pubId, summary);

            String hwConsole = platform;
            org.steamproject.model.DistributionPlatform dp = org.steamproject.model.DistributionPlatform.inferFromHardwareCode(hwConsole);
            String distributionPlatform = dp == null ? "steam" : dp.getId();
            GameProjection.getInstance().upsertGame(gameId, distributionPlatform, hwConsole, gameName, releaseYear, genre, pubId, initialVersion, initialPrice);

            try {
                String platformId = distributionPlatform;
                String platTopic = System.getProperty("kafka.topic.platform", "platform-catalog.events");
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
            } catch (Throwable t) {  }
            System.out.println("PublisherConsumer processed game released publisher=" + pubId + " game=" + gameId + " platform=" + platform + " genre=" + genre);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    /**
     * Traite un événement de publication de jeu.
     * 
     * Similaire à handleGameReleased mais sans année de sortie explicite.
     * Ajoute le jeu au catalogue de l'éditeur et met à jour la projection du jeu.
     * 
     * @param evt Événement de publication de jeu à traiter
     */
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

    /**
     * Traite un événement de mise à jour de jeu.
     * 
     * Applique les modifications de champs spécifiées dans l'événement en mettant
     * à jour la projection du jeu champ par champ.
     * 
     * @param evt Événement de mise à jour de jeu à traiter
     */
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

    /**
     * Met à jour un champ spécifique d'un jeu dans la projection.
     * 
     * Méthode auxiliaire qui récupère les données actuelles du jeu, modifie
     * le champ spécifié, puis réinsère l'ensemble dans la projection.
     * 
     * @param gameId Identifiant du jeu
     * @param key Nom du champ à modifier
     * @param value Nouvelle valeur (null pour supprimer le champ)
     */
    private void storeUpsertField(String gameId, String key, String value) {
        var gd = GameProjection.getInstance().getGame(gameId);
        if (gd == null) return;
        java.util.Map<String,Object> m = new java.util.HashMap<>(gd);
        if (value == null) m.remove(key); else m.put(key, value);
        String distributionPlatform = (String) m.get("distributionPlatform");
        String console = (String) m.get("console");
        GameProjection.getInstance().upsertGame(gameId, distributionPlatform, console, (String) m.get("gameName"), (Integer) m.get("releaseYear"), (String) m.get("genre"), (String) m.get("publisherId"), (String) m.get("initialVersion"), (Double) m.get("price"));
    }

    /**
     * Traite un événement de publication de patch.
     * 
     * Infère le type de patch (ADD/OPTIMIZATION/FIX) depuis les changements ou le changelog,
     * valide et ajuste le numéro de version selon la sémantique SemVer si nécessaire,
     * puis ajoute le patch à la projection du jeu avec création automatique d'une nouvelle version.
     * 
     * @param evt Événement de publication de patch à traiter
     */
    public void handlePatchPublished(org.steamproject.events.PatchPublishedEvent evt) {
        try {
            String gameId = evt.getGameId().toString();
            String patchId = gameId + "-patch-" + Long.toString(evt.getTimestamp());
            String oldVersion = evt.getOldVersion() == null ? "" : evt.getOldVersion().toString();
            String newVersion = evt.getNewVersion() == null ? "" : evt.getNewVersion().toString();
            String desc = evt.getChangeLog() == null ? null : evt.getChangeLog().toString();
            Long ts = evt.getTimestamp();

            // Déterminer le type principal du patch à partir du premier Change
            org.steamproject.model.PatchType inferredType = org.steamproject.model.PatchType.FIX;
            try {
                java.util.List<org.steamproject.events.Change> changes = evt.getChanges();
                if (changes != null && !changes.isEmpty()) {
                    // Le premier changement détermine le type principal du patch
                    org.steamproject.events.Change firstChange = changes.get(0);
                    if (firstChange != null && firstChange.getType() != null) {
                        org.steamproject.events.PatchType avroPatchType = firstChange.getType();
                        switch (avroPatchType) {
                            case ADD:
                                inferredType = org.steamproject.model.PatchType.ADD;
                                break;
                            case OPTIMIZATION:
                                inferredType = org.steamproject.model.PatchType.OPTIMIZATION;
                                break;
                            case FIX:
                            default:
                                inferredType = org.steamproject.model.PatchType.FIX;
                                break;
                        }
                    }
                }
            } catch (Exception ignore) { 
                // Fallback sur analyse de texte si échec
                String txt = desc == null ? "" : desc.toLowerCase();
                if (txt.contains("ajout") || txt.contains("add") || txt.contains("feature") || txt.contains("majeure")) 
                    inferredType = org.steamproject.model.PatchType.ADD;
                else if (txt.contains("optim") || txt.contains("perf")) 
                    inferredType = org.steamproject.model.PatchType.OPTIMIZATION;
            }

            try {
                String expected = org.steamproject.util.Semver.nextVersionForPatchType(oldVersion, inferredType == null ? org.steamproject.model.PatchType.FIX : inferredType);
                boolean shouldAdjust = false;
                if (newVersion == null || newVersion.isBlank()) shouldAdjust = true;
                else {
                    try {
                        int cmp = org.steamproject.util.Semver.compare(newVersion, oldVersion);
                        if (cmp <= 0) shouldAdjust = true;
                        else if (!newVersion.equals(expected)) {
                            shouldAdjust = true;
                        }
                    } catch (Exception ex) { shouldAdjust = true; }
                }
                if (shouldAdjust) {
                    System.out.println("PublisherConsumer: adjusting patch version from '" + newVersion + "' to expected '" + expected + "' for game=" + gameId);
                    newVersion = expected;
                }
            } catch (Exception ex) {
                if (newVersion == null || newVersion.isBlank()) {
                    newVersion = org.steamproject.util.Semver.incrementPatch(oldVersion);
                }
            }

            Long sizeInMB = evt.getSizeInMB();
            String typeStr = inferredType != null ? inferredType.name() : "FIX";
            GameProjection.getInstance().addPatch(gameId, patchId, oldVersion, newVersion, desc, sizeInMB, ts == null ? null : ts, typeStr);
            System.out.println("PublisherConsumer added patch " + patchId + " for game=" + gameId + " newVersion=" + newVersion + " (type=" + inferredType + ", size=" + sizeInMB + " MB)");
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    /**
     * Traite un événement de publication de DLC.
     * 
     * Génère un ID de DLC si absent, puis ajoute le DLC à la projection du jeu parent.
     * 
     * @param evt Événement de publication de DLC à traiter
     */
    public void handleDlcPublished(org.steamproject.events.DlcPublishedEvent evt) {
        try {
            String gameId = evt.getGameId().toString();
            String dlcId = evt.getDlcId() == null ? java.util.UUID.randomUUID().toString() : evt.getDlcId().toString();
            String dlcName = evt.getDlcName() == null ? null : evt.getDlcName().toString();
            Double price = evt.getPrice();
            Long sizeInMB = evt.getSizeInMB();
            Long ts = evt.getReleaseTimestamp();
            GameProjection.getInstance().addDlc(gameId, dlcId, dlcName, price, sizeInMB, ts == null ? null : ts);
            System.out.println("PublisherConsumer added DLC " + dlcId + " for game=" + gameId + " (size=" + sizeInMB + " MB)");
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    /**
     * Traite un événement de dépréciation de version de jeu.
     * 
     * Marque une version spécifique comme dépréciée dans la projection du jeu.
     * 
     * @param evt Événement de dépréciation de version à traiter
     */
    public void handleGameVersionDeprecated(org.steamproject.events.GameVersionDeprecatedEvent evt) {
        try {
            String gameId = evt.getGameId().toString();
            String dep = evt.getDeprecatedVersion() == null ? null : evt.getDeprecatedVersion().toString();
            Long at = evt.getDeprecatedAt();
            if (dep != null) GameProjection.getInstance().deprecateVersion(gameId, dep, at == null ? null : at);
            System.out.println("PublisherConsumer deprecated version " + dep + " for game=" + gameId);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    /**
     * Traite un événement de réponse de l'éditeur à un incident.
     * 
     * Enregistre la réponse officielle de l'éditeur concernant un incident signalé
     * (crash, bug, etc.) dans la projection du jeu.
     * 
     * @param evt Événement de réponse d'éditeur à traiter
     */
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