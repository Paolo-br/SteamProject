package org.steamproject.infra.kafka.producer;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.steamproject.events.GamePurchaseEvent;

import java.util.Properties;
import java.util.concurrent.Future;

/**
 * Producer dédié aux événements liés aux joueurs (player scope).
 * Expose une méthode spécifique pour envoyer un achat de jeu.
 */
public class PlayerProducer {
    private final KafkaProducer<String, Object> producer;
    private final String topic;

    public PlayerProducer(String bootstrapServers, String schemaRegistryUrl, String topic) {
        this.topic = topic;
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", KafkaAvroSerializer.class.getName());
        props.put("schema.registry.url", schemaRegistryUrl);
        this.producer = new KafkaProducer<>(props);
    }

    public Future<RecordMetadata> sendGamePurchase(String playerId, GamePurchaseEvent evt) {
        ProducerRecord<String, Object> rec = new ProducerRecord<>(topic, playerId, evt);
        return producer.send(rec);
    }

    public void close() {
        producer.flush();
        producer.close();
    }
}
