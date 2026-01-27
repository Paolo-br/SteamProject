package org.steamproject.infra.kafka.consumer;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.steamproject.events.PlayerCreatedEvent;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

/**
 * Consommateur pour les événements {@code PlayerCreatedEvent}.
 *
 * Met à jour la projection locale des joueurs lorsqu'un nouvel événement
 * de création de joueur est reçu.
 */
public class PlayerCreatedConsumer {
    private final KafkaConsumer<String, Object> consumer;
    private final String topic;

    public PlayerCreatedConsumer(String bootstrap, String schemaRegistryUrl, String topic, String groupId) {
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
    /**
     * Démarre la boucle de consommation et appelle {@link #handlePlayerCreated(PlayerCreatedEvent)}
     * pour chaque enregistrement valide.
     */
    public void start() {
        System.out.println("PlayerCreatedConsumer started, listening to " + topic);
        try {
            while (true) {
                ConsumerRecords<String, Object> recs = consumer.poll(Duration.ofSeconds(1));
                recs.forEach(r -> {
                    try {
                        Object val = r.value();
                        if (val instanceof PlayerCreatedEvent) {
                            PlayerCreatedEvent evt = (PlayerCreatedEvent) val;
                            handlePlayerCreated(evt);
                        } else {
                            System.out.println("Ignored unknown player-created event type: " + (val != null ? val.getClass() : null));
                        }
                    } catch (Exception ex) { ex.printStackTrace(); }
                });
            }
        } finally { consumer.close(); }
    }

    /**
     * Traite un {@link PlayerCreatedEvent} et met à jour la projection
     * persistée en mémoire.
     *
     * @param evt événement contenant les données du joueur
     */
    private void handlePlayerCreated(PlayerCreatedEvent evt) {
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
            PlayerProjection.getInstance().upsert(id, username, email, reg, ts, gdpr, gdprDate);
            System.out.println("PlayerCreatedConsumer processed player=" + id + " username=" + username);
        } catch (Exception ex) { ex.printStackTrace(); }
    }
}
