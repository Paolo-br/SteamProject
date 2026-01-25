package org.steamproject.infra.kafka.producer;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.steamproject.events.GameReleasedEvent;

import java.util.Properties;
import java.util.concurrent.Future;

public class PublisherProducer {
    private final KafkaProducer<String, Object> producer;
    private final String topic;

    public PublisherProducer(String bootstrapServers, String schemaRegistryUrl, String topic) {
        this.topic = topic;
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", KafkaAvroSerializer.class.getName());
        props.put("schema.registry.url", schemaRegistryUrl);
        this.producer = new KafkaProducer<>(props);
    }

    public Future<RecordMetadata> sendGameReleased(String gameId, GameReleasedEvent evt) {
        ProducerRecord<String, Object> rec = new ProducerRecord<>(topic, gameId, evt);
        return producer.send(rec);
    }

    public void close() {
        producer.flush();
        producer.close();
    }
}

