package org.steamproject.infra.kafka.producer;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.steamproject.events.GamePurchaseEvent;
import org.steamproject.events.ReviewVotedEvent;

import java.util.Properties;
import java.util.concurrent.Future;

/**
 * Producteur dédié aux événements liés aux joueurs (scope "player").
 *
 * Cette classe encapsule un {@code KafkaProducer} configuré pour sérialiser
 * les valeurs avec Avro via le Schema Registry et expose des méthodes
 * utilitaires pour envoyer des événements liés aux joueurs.
 * 
 * Événements supportés :
 * - GamePurchaseEvent : achat de jeu
 * - ReviewVotedEvent : vote sur une évaluation (utile/pas utile)
 */
public class PlayerProducer {
    private final KafkaProducer<String, Object> producer;
    private final String topic;
    private final String bootstrapServers;
    private final String schemaRegistryUrl;
    
    public static final String REVIEW_VOTED_TOPIC = "review-voted-events";

    public PlayerProducer(String bootstrapServers, String schemaRegistryUrl, String topic) {
        this.topic = topic;
        this.bootstrapServers = bootstrapServers;
        this.schemaRegistryUrl = schemaRegistryUrl;
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", KafkaAvroSerializer.class.getName());
        props.put("schema.registry.url", schemaRegistryUrl);
        this.producer = new KafkaProducer<>(props);
    }

    /**
     * Envoie un événement d'achat de jeu pour un joueur donné.
     *
     * @param playerId identifiant du joueur utilisé comme clé du message
     * @param evt      événement de type {@link GamePurchaseEvent} à publier
     * @return {@link Future} de {@link RecordMetadata} représentant l'envoi asynchrone
     */
    public Future<RecordMetadata> sendGamePurchase(String playerId, GamePurchaseEvent evt) {
        ProducerRecord<String, Object> rec = new ProducerRecord<>(topic, playerId, evt);
        return producer.send(rec);
    }

    /**
     * Envoie un événement de vote sur une évaluation.
     * 
     * La clé du message est le reviewId pour permettre l'agrégation par évaluation
     * dans Kafka Streams avec partitionnement cohérent.
     *
     * @param reviewId identifiant de l'évaluation votée (clé pour partitionnement)
     * @param evt      événement de type {@link ReviewVotedEvent} à publier
     * @return {@link Future} de {@link RecordMetadata} représentant l'envoi asynchrone
     */
    public Future<RecordMetadata> sendReviewVote(String reviewId, ReviewVotedEvent evt) {
        ProducerRecord<String, Object> rec = new ProducerRecord<>(REVIEW_VOTED_TOPIC, reviewId, evt);
        return producer.send(rec);
    }

    public void close() {
        producer.flush();
        producer.close();
    }
}
