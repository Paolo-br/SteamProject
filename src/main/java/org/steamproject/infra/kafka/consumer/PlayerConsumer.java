package org.steamproject.infra.kafka.consumer;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.steamproject.events.GamePurchaseEvent;
import org.steamproject.model.GameOwnership;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Properties;

/**
 * Consumer dédié aux événements de players. Traite les `GamePurchaseEvent`.
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
        this.consumer.subscribe(Collections.singletonList(topic));
    }

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

    public void handleGamePurchase(GamePurchaseEvent evt) {
        try {
            String eventId = evt.getEventId() == null ? "" : evt.getEventId().toString();
            // De-duplication: ignore if we've processed this eventId already
            if (!PlayerLibraryProjection.getInstance().markEventIfNew(eventId)) {
                System.out.println("PlayerConsumer skipped duplicate eventId=" + eventId);
                return;
            }

            String playerId = evt.getPlayerId().toString();
            String purchaseDate = DateTimeFormatter.ISO_INSTANT
                    .format(Instant.ofEpochMilli(evt.getTimestamp()).atOffset(ZoneOffset.UTC));
            GameOwnership go = new GameOwnership(evt.getGameId().toString(), evt.getGameName().toString(), purchaseDate);
            PlayerLibraryProjection.getInstance().addOwnership(playerId, go);
            System.out.println("PlayerConsumer processed purchase for player=" + playerId + " game=" + evt.getGameId());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
