package org.steamproject.infra.kafka.producer;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

/**
 * Producteur pour événements liés à une plateforme de distribution.
 *
 * Exemples d'utilisation : envoyer des mises à jour de catalogue pour une
 * plateforme particulière. Adapté pour de petits outils ou tests.
 */
public class PlatformProducer {
    private final KafkaProducer<String, Object> producer;
    private final String topic;

    public PlatformProducer(String bootstrapServers, String schemaRegistryUrl, String topic) {
        this.topic = topic;
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", KafkaAvroSerializer.class.getName());
        props.put("schema.registry.url", schemaRegistryUrl);
        this.producer = new KafkaProducer<>(props);
    }

    public void close() {
        producer.flush();
        producer.close();
    }

    /**
     * Envoie un événement de mise à jour de catalogue pour une plateforme.
     *
     * @param gameId identifiant du jeu (clé du message)
     * @param evt    {@link org.steamproject.events.PlatformCatalogUpdateEvent} contenant les détails
     * @return Future correspondant à l'envoi Kafka
     */
    public java.util.concurrent.Future<org.apache.kafka.clients.producer.RecordMetadata> sendCatalogUpdate(String gameId, org.steamproject.events.PlatformCatalogUpdateEvent evt) {
        org.apache.kafka.clients.producer.ProducerRecord<String, Object> rec = new org.apache.kafka.clients.producer.ProducerRecord<>(topic, gameId, evt);
        return producer.send(rec);
    }
}
