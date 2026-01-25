package org.steamproject.infra.kafka.producer;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.steamproject.events.GamePurchaseEvent;

import java.util.Properties;
import java.util.concurrent.Future;

public class GamePurchaseProducer {
    private final KafkaProducer<String, Object> producer;
    private final String topic;

    public GamePurchaseProducer(String bootstrapServers, String schemaRegistryUrl, String topic) {
        this.topic = topic;
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", KafkaAvroSerializer.class.getName());
        props.put("schema.registry.url", schemaRegistryUrl);
        this.producer = new KafkaProducer<>(props);
    }

    public Future<RecordMetadata> send(String key, GamePurchaseEvent event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);
        return producer.send(record);
    }

    public void close() {
        producer.flush();
        producer.close();
    }
}
