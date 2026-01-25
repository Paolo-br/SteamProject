package org.steamproject.infra.kafka.consumer;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.steamproject.events.GamePurchaseEvent;
import org.steamproject.model.GameOwnership;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Properties;

public class PurchaseEventConsumer {
    public static void main(String[] args) {
        String bootstrap = System.getProperty("kafka.bootstrap", "localhost:9092");
        String sr = System.getProperty("schema.registry", "http://localhost:8081");
        String topic = System.getProperty("kafka.topic", "game-purchase-events");

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "purchase-consumer-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
        props.put("schema.registry.url", sr);
        props.put("specific.avro.reader", "true");

        try (KafkaConsumer<String, Object> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));
            System.out.println("PurchaseEventConsumer started, listening to " + topic);
            while (true) {
                ConsumerRecords<String, Object> recs = consumer.poll(Duration.ofSeconds(1));
                recs.forEach(r -> {
                    try {
                        Object val = r.value();
                        if (val instanceof GamePurchaseEvent) {
                            GamePurchaseEvent evt = (GamePurchaseEvent) val;
                            String playerId = evt.getPlayerId().toString();
                            String purchaseDate = DateTimeFormatter.ISO_INSTANT
                                    .format(Instant.ofEpochMilli(evt.getTimestamp()).atOffset(ZoneOffset.UTC));
                            GameOwnership go = new GameOwnership(evt.getGameId().toString(), evt.getGameName().toString(), purchaseDate);
                            PlayerLibraryProjection.getInstance().addOwnership(playerId, go);
                            System.out.println("Processed purchase for player=" + playerId + " game=" + evt.getGameId());
                        } else {
                            System.out.println("Received unexpected record type: " + (val != null ? val.getClass() : null));
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
            }
        }
    }
}
