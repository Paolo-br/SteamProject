package org.steamproject.infra.kafka.producer;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.steamproject.events.GameReleasedEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import java.util.Properties;
import java.util.concurrent.Future;

public class PublisherProducer {
    private final KafkaProducer<String, Object> producer;

    public static final String TOPIC_GAME_RELEASED = "game-released-events";
    public static final String TOPIC_GAME_PUBLISHED = "game-published-events";
    public static final String TOPIC_GAME_UPDATED = "game-updated-events";
    public static final String TOPIC_PATCH_PUBLISHED = "patch-published-events";
    public static final String TOPIC_DLC_PUBLISHED = "dlc-published-events";
    public static final String TOPIC_VERSION_DEPRECATED = "game-version-deprecated-events";
    public static final String TOPIC_EDITOR_RESPONDED = "editor-responded-events";

    public PublisherProducer(String bootstrapServers, String schemaRegistryUrl) {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", KafkaAvroSerializer.class.getName());
        props.put("schema.registry.url", schemaRegistryUrl);
        this.producer = new KafkaProducer<>(props);
    }

    public Future<RecordMetadata> sendGameReleased(String gameId, GameReleasedEvent evt) {
        return sendToTopic(TOPIC_GAME_RELEASED, gameId, evt);
    }

    public Future<RecordMetadata> sendToTopic(String topic, String key, Object evt) {
        ProducerRecord<String, Object> rec = new ProducerRecord<>(topic, key, evt);
        return producer.send(rec);
    }


    public Future<RecordMetadata> publishGame(
            String gameId,
            String gameName,
            String publisherId,
            String publisherName,
            int releaseYear,
            String platform,
            List<String> platforms,
            String initialVersion,
            double initialPrice,
            String genre
    ) {
        GameReleasedEvent evt = GameReleasedEvent.newBuilder()
                .setTimestamp(Instant.now().toEpochMilli())
                .setInitialVersion(initialVersion == null ? "1.0.0" : initialVersion)
                .setReleaseYear(releaseYear)
                .setInitialPrice(initialPrice)
                .setGenre(genre == null ? "" : genre)
                .setEventId(UUID.randomUUID().toString())
                .setPlatform(platform == null ? "" : platform)
                .setPlatforms(platforms == null ? java.util.Collections.emptyList() : platforms)
                .setPublisherName(publisherName == null ? "" : publisherName)
                .setPublisherId(publisherId == null ? "" : publisherId)
                .setGameName(gameName == null ? "" : gameName)
                .setGameId(gameId)
                .build();

        return sendToTopic(TOPIC_GAME_RELEASED, gameId, evt);
    }

    public void close() {
        producer.flush();
        producer.close();
    }
    public Future<RecordMetadata> publishGamePublished(String gameId, String gameName, String publisherId, String platform, String genre, String initialVersion, double initialPrice) {
        org.steamproject.events.GamePublishedEvent evt = org.steamproject.events.GamePublishedEvent.newBuilder()
                .setGameId(gameId)
                .setPublisherId(publisherId == null ? "" : publisherId)
                .setGameName(gameName == null ? "" : gameName)
                .setPlatform(platform == null ? "" : platform)
                .setGenre(genre == null ? null : genre)
                .setInitialVersion(initialVersion == null ? "1.0.0" : initialVersion)
                .setInitialPrice(initialPrice)
                .setReleaseTimestamp(Instant.now().toEpochMilli())
                .build();
        return sendToTopic(TOPIC_GAME_PUBLISHED, gameId, evt);
    }

    public Future<RecordMetadata> publishGameUpdated(String gameId, String publisherId, String platform, java.util.Map<String, String> updates) {
        org.steamproject.events.GameUpdatedEvent.Builder b = org.steamproject.events.GameUpdatedEvent.newBuilder();
        b.setGameId(gameId);
        b.setPublisherId(publisherId == null ? "" : publisherId);
        b.setPlatform(platform == null ? "" : platform);
        java.util.Map<String, String> m = new java.util.HashMap<>();
        if (updates != null) updates.forEach((k,v) -> m.put(k, v));
        b.setUpdatedFields(m);
        b.setUpdateTimestamp(Instant.now().toEpochMilli());
        return sendToTopic(TOPIC_GAME_UPDATED, gameId, b.build());
    }

    public Future<RecordMetadata> publishPatch(String gameId, String gameName, String platform, String oldVersion, String newVersion, String changeLog) {
        org.steamproject.events.PatchPublishedEvent evt = org.steamproject.events.PatchPublishedEvent.newBuilder()
                .setGameId(gameId)
                .setGameName(gameName == null ? "" : gameName)
                .setPlatform(platform == null ? "" : platform)
                .setOldVersion(oldVersion == null ? "" : oldVersion)
                .setNewVersion(newVersion == null ? "" : newVersion)
                .setChangeLog(changeLog == null ? "" : changeLog)
                .setChanges(java.util.Collections.emptyList())
                .setTimestamp(Instant.now().toEpochMilli())
                .build();
        return sendToTopic(TOPIC_PATCH_PUBLISHED, gameId, evt);
    }

    public Future<RecordMetadata> publishDlc(String dlcId, String gameId, String publisherId, String platform, String dlcName, double price) {
        org.steamproject.events.DlcPublishedEvent evt = org.steamproject.events.DlcPublishedEvent.newBuilder()
                .setDlcId(dlcId == null ? java.util.UUID.randomUUID().toString() : dlcId)
                .setGameId(gameId)
                .setPublisherId(publisherId == null ? "" : publisherId)
                .setPlatform(platform == null ? "" : platform)
                .setDlcName(dlcName == null ? "" : dlcName)
                .setPrice(price)
                .setReleaseTimestamp(Instant.now().toEpochMilli())
                .build();
        return sendToTopic(TOPIC_DLC_PUBLISHED, gameId, evt);
    }

    public Future<RecordMetadata> publishVersionDeprecated(String gameId, String publisherId, String platform, String deprecatedVersion) {
        org.steamproject.events.GameVersionDeprecatedEvent evt = org.steamproject.events.GameVersionDeprecatedEvent.newBuilder()
                .setGameId(gameId)
                .setPublisherId(publisherId == null ? "" : publisherId)
                .setPlatform(platform == null ? "" : platform)
                .setDeprecatedVersion(deprecatedVersion == null ? "" : deprecatedVersion)
                .setDeprecatedAt(Instant.now().toEpochMilli())
                .build();
        return sendToTopic(TOPIC_VERSION_DEPRECATED, gameId, evt);
    }

    public Future<RecordMetadata> publishEditorResponse(String incidentId, String gameId, String publisherId, String platform, String responseMessage) {
        org.steamproject.events.EditorRespondedToIncidentEvent evt = org.steamproject.events.EditorRespondedToIncidentEvent.newBuilder()
                .setIncidentId(incidentId == null ? java.util.UUID.randomUUID().toString() : incidentId)
                .setGameId(gameId)
                .setPublisherId(publisherId == null ? "" : publisherId)
                .setPlatform(platform == null ? "" : platform)
                .setResponseMessage(responseMessage == null ? "" : responseMessage)
                .setResponseTimestamp(Instant.now().toEpochMilli())
                .build();
        return sendToTopic(TOPIC_EDITOR_RESPONDED, gameId, evt);
    }
}

