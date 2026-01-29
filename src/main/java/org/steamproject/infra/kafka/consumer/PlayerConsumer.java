package org.steamproject.infra.kafka.consumer;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.steamproject.events.GamePurchaseEvent;
import org.steamproject.events.PlayerCreatedEvent;
import org.steamproject.events.DlcPurchaseEvent;
import org.steamproject.events.GameSessionEvent;
import org.steamproject.events.CrashReportEvent;
import org.steamproject.events.NewRatingEvent;
import org.steamproject.events.ReviewPublishedEvent;
import org.steamproject.events.ReviewVotedEvent;
import org.steamproject.model.GameOwnership;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Properties;


/**
 * Consommateur pour les événements liés aux joueurs (achats, etc.).
 *
 * Ce consommateur désérialise les événements Avro et délègue le traitement
 * des achats à la méthode.
 */
public class PlayerConsumer {
    private final KafkaConsumer<String, Object> consumer;
    private final String topic;

    public PlayerConsumer(String bootstrap, String schemaRegistryUrl, String topic, String groupId) {
        this.topic = topic;
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
        props.put("schema.registry.url", schemaRegistryUrl);
        props.put("specific.avro.reader", "true");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        this.consumer = new KafkaConsumer<>(props);
        if (topic != null && topic.contains(",")) {
            java.util.List<String> topics = new java.util.ArrayList<>();
            for (String t : topic.split(",")) {
                String s = t.strip(); if (!s.isEmpty()) topics.add(s);
            }
            this.consumer.subscribe(topics);
            System.out.println("PlayerConsumer subscribed to topics: " + topics);
        } else {
            this.consumer.subscribe(Collections.singletonList(topic));
            System.out.println("PlayerConsumer subscribed to topic: " + topic);
        }
    }

    /**
     * Démarre la boucle de consommation bloquante.
     * Affiche un message sur la console et boucle en appelant régulièrement
     * 
     */
    public void start() {
        System.out.println("PlayerConsumer started, listening to " + topic);
        try {
            while (true) {
                ConsumerRecords<String, Object> recs = consumer.poll(Duration.ofSeconds(1));
                recs.forEach(r -> {
                    try {
                        Object val = r.value();
                        if (val instanceof GamePurchaseEvent) {
                            GamePurchaseEvent evt = (GamePurchaseEvent) val;
                            handleGamePurchase(evt);
                        } else if (val instanceof PlayerCreatedEvent) {
                            PlayerCreatedEvent evt = (PlayerCreatedEvent) val;
                            handlePlayerCreated(evt);
                        } else if (val instanceof DlcPurchaseEvent) {
                            DlcPurchaseEvent evt = (DlcPurchaseEvent) val;
                            handleDlcPurchase(evt);
                        } else if (val instanceof GameSessionEvent) {
                            GameSessionEvent evt = (GameSessionEvent) val;
                            handleGameSession(evt);
                        } else if (val instanceof CrashReportEvent) {
                            CrashReportEvent evt = (CrashReportEvent) val;
                            handleCrashReport(evt);
                        } else if (val instanceof NewRatingEvent) {
                            NewRatingEvent evt = (NewRatingEvent) val;
                            handleNewRating(evt);
                        } else if (val instanceof ReviewPublishedEvent) {
                            ReviewPublishedEvent evt = (ReviewPublishedEvent) val;
                            handleReviewPublished(evt);
                        } else if (val instanceof ReviewVotedEvent) {
                            ReviewVotedEvent evt = (ReviewVotedEvent) val;
                            handleReviewVoted(evt);
                        } else {
                            System.out.println("Ignored unknown event type: " + (val != null ? val.getClass() : null));
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
            }
        } finally {
            consumer.close();
        }
    }

    /**
     * Traite un {@link GamePurchaseEvent} : marque l'événement pour éviter les
     * doublons, transforme la donnée et met à jour la projection locale.
     *
     * @param evt événement d'achat reçu
     */
    public void handleGamePurchase(GamePurchaseEvent evt) {
        try {
            String eventId = evt.getEventId() == null ? "" : evt.getEventId().toString();
            if (!PlayerLibraryProjection.getInstance().markEventIfNew(eventId)) {
                System.out.println("PlayerConsumer skipped duplicate eventId=" + eventId);
                return;
            }
            String playerId = evt.getPlayerId().toString();
            var players = PlayerProjection.getInstance().snapshot();
            if (!players.containsKey(playerId)) {
                System.out.println("PlayerConsumer ignored purchase for unknown player=" + playerId + " game=" + evt.getGameId());
                return;
            }
            String purchaseDate = DateTimeFormatter.ISO_INSTANT
                    .format(Instant.ofEpochMilli(evt.getTimestamp()).atOffset(ZoneOffset.UTC));
            double pricePaid = evt.getPricePaid();
            GameOwnership go = new GameOwnership(evt.getGameId().toString(), evt.getGameName().toString(), purchaseDate, 0, null, pricePaid);
            var existing = PlayerLibraryProjection.getInstance().getLibrary(playerId);
            boolean alreadyOwns = false;
            for (Object o : existing) {
                try {
                    var ex = (org.steamproject.model.GameOwnership) o;
                    if (ex != null && ex.gameId() != null && ex.gameId().equals(evt.getGameId().toString())) { alreadyOwns = true; break; }
                } catch (Throwable t) { }
            }
            if (alreadyOwns) {
                System.out.println("PlayerConsumer ignored duplicate purchase for player=" + playerId + " game=" + evt.getGameId());
                return;
            }
            PlayerLibraryProjection.getInstance().addOwnership(playerId, go);
            System.out.println("PlayerConsumer processed purchase for player=" + playerId + " game=" + evt.getGameId());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void handlePlayerCreated(PlayerCreatedEvent evt) {
        try {
            String id = evt.getId().toString();
            String username = evt.getUsername() == null ? "" : evt.getUsername().toString();
            String email = evt.getEmail() == null ? null : evt.getEmail().toString();
            String reg = evt.getRegistrationDate() == null ? "" : evt.getRegistrationDate().toString();
            long ts = evt.getTimestamp();
            Boolean gdpr = null;
            try { gdpr = evt.getGdprConsent(); } catch (Throwable t) { /* ignore */ }
            String gdprDate = null;
            try { gdprDate = evt.getGdprConsentDate() == null ? null : evt.getGdprConsentDate().toString(); } catch (Throwable t) { /* ignore */ }
            String firstName = null; try { firstName = evt.getFirstName() == null ? null : evt.getFirstName().toString(); } catch (Throwable t) { /* ignore */ }
            String lastName = null; try { lastName = evt.getLastName() == null ? null : evt.getLastName().toString(); } catch (Throwable t) { /* ignore */ }
            String dob = null; try { dob = evt.getDateOfBirth() == null ? null : evt.getDateOfBirth().toString(); } catch (Throwable t) { /* ignore */ }
            PlayerProjection.getInstance().upsert(id, username, email, reg, firstName, lastName, dob, ts, gdpr, gdprDate);
            System.out.println("PlayerConsumer processed player-created id=" + id + " username=" + username);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    public void handleDlcPurchase(DlcPurchaseEvent evt) {
        try {
            String eventId = evt.getEventId() == null ? "" : evt.getEventId().toString();
            if (!PlayerLibraryProjection.getInstance().markEventIfNew(eventId)) return;
            String playerId = evt.getPlayerId().toString();
            var players = PlayerProjection.getInstance().snapshot();
            if (!players.containsKey(playerId)) {
                System.out.println("PlayerConsumer ignored DLC purchase for unknown player=" + playerId + " dlc=" + evt.getDlcId());
                return;
            }
            String purchaseDate = java.time.format.DateTimeFormatter.ISO_INSTANT
                    .format(java.time.Instant.ofEpochMilli(evt.getTimestamp()).atOffset(java.time.ZoneOffset.UTC));
            double pricePaid = evt.getPricePaid();
            org.steamproject.model.GameOwnership go = new org.steamproject.model.GameOwnership(evt.getDlcId().toString(), evt.getDlcName().toString(), purchaseDate, 0, evt.getGameId().toString(), pricePaid);
            PlayerLibraryProjection.getInstance().addOwnership(playerId, go);
            System.out.println("PlayerConsumer processed DLC purchase for player=" + playerId + " dlc=" + evt.getDlcId());
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    public void handleGameSession(GameSessionEvent evt) {
        try {
            String playerId = evt.getPlayerId();
            PlayerProjection.getInstance().recordSession(playerId, evt.getSessionId(), evt.getGameId(), evt.getGameName(), evt.getSessionDuration(), evt.getSessionType().toString(), evt.getTimestamp());
            System.out.println("PlayerConsumer recorded session player=" + playerId + " game=" + evt.getGameId());
            try { updateLibraryPlaytimeFromSession(evt); } catch (Throwable t) { /* ignore */ }
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void updateLibraryPlaytimeFromSession(GameSessionEvent evt) {
        try {
            String playerId = evt.getPlayerId();
            String gameId = evt.getGameId();
            int minutes = evt.getSessionDuration();
            String lastPlayed = java.time.Instant.ofEpochMilli(evt.getTimestamp()).toString();
            PlayerLibraryProjection.getInstance().addPlaytime(playerId, gameId, minutes, lastPlayed);
        } catch (Throwable t) {  }
    }

    public void handleCrashReport(CrashReportEvent evt) {
        try {
            String eventId = evt.getCrashId() == null ? "" : evt.getCrashId();
            if (!PlayerLibraryProjection.getInstance().markEventIfNew(eventId)) {
                System.out.println("PlayerConsumer skipped duplicate crashId=" + eventId);
                return;
            }
            String playerId = evt.getPlayerId();
            var library = PlayerLibraryProjection.getInstance().getLibrary(playerId);
            boolean owns = false;
            for (Object o : library) {
                try {
                    var go = (org.steamproject.model.GameOwnership) o;
                    if (go != null && go.gameId() != null && go.gameId().equals(evt.getGameId())) { owns = true; break; }
                } catch (Throwable t) {}
            }
            if (!owns) {
                System.out.println("PlayerConsumer ignored crash report for non-owned game player=" + playerId + " game=" + evt.getGameId());
                return;
            }

            PlayerProjection.getInstance().recordCrash(playerId, evt.getCrashId(), evt.getGameId(), evt.getGameName(), evt.getPlatform(), evt.getSeverity().toString(), evt.getErrorType(), evt.getErrorMessage() == null ? null : evt.getErrorMessage().toString(), evt.getTimestamp());
            try { org.steamproject.infra.kafka.consumer.GameProjection.getInstance().incrementIncidentCount(evt.getGameId()); } catch (Throwable t) { /* best-effort */ }
            try {
                org.steamproject.infra.kafka.consumer.GameProjection.getInstance().addEditorResponse(evt.getGameId(), evt.getCrashId(), evt.getErrorMessage() == null ? "" : evt.getErrorMessage().toString(), evt.getTimestamp());
            } catch (Throwable t) { }
            System.out.println("PlayerConsumer recorded crash player=" + playerId + " crash=" + evt.getCrashId());
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    public void handleNewRating(NewRatingEvent evt) {
        try {
            String playerId = evt.getPlayerId();
            PlayerProjection.getInstance().addReview(playerId, java.util.UUID.randomUUID().toString(), evt.getGameId(), evt.getRating(), evt.getComment(), null, evt.getIsRecommended(), evt.getTimestamp());
            System.out.println("PlayerConsumer recorded rating player=" + playerId + " game=" + evt.getGameId());
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    public void handleReviewPublished(ReviewPublishedEvent evt) {
        try {
            String playerId = evt.getPlayerId();
            PlayerProjection.getInstance().addReview(playerId, evt.getReviewId(), evt.getGameId(), evt.getRating(), evt.getTitle(), evt.getText(), evt.getIsSpoiler(), evt.getTimestamp());
            System.out.println("PlayerConsumer recorded review player=" + playerId + " review=" + evt.getReviewId());
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    public void handleReviewVoted(ReviewVotedEvent evt) {
        try {
            System.out.println("PlayerConsumer received review vote reviewId=" + evt.getReviewId() + " voter=" + evt.getVoterPlayerId() + " helpful=" + evt.getIsHelpful());
        } catch (Exception ex) { ex.printStackTrace(); }
    }
}
