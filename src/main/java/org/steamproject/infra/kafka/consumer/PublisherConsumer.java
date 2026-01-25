package org.steamproject.infra.kafka.consumer;

import java.util.Collections;
import java.util.Properties;

/**
 * Skeleton consumer for publisher-scoped events.
 * Should subscribe to a `publisher-*` topic and dispatch to type-specific handlers.
 */
public class PublisherConsumer {
    private final org.apache.kafka.clients.consumer.KafkaConsumer<String, Object> consumer;
    private final String topic;

    public PublisherConsumer(String bootstrap, String schemaRegistryUrl, String topic, String groupId) {
        this.topic = topic;
        java.util.Properties props = new java.util.Properties();
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringDeserializer.class.getName());
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, io.confluent.kafka.serializers.KafkaAvroDeserializer.class.getName());
        props.put("schema.registry.url", schemaRegistryUrl);
        props.put("specific.avro.reader", "true");
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        this.consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(props);
        this.consumer.subscribe(java.util.Collections.singletonList(topic));
    }

    public void start() {
        System.out.println("PublisherConsumer started, listening to " + topic);
        try {
            while (true) {
                var recs = consumer.poll(java.time.Duration.ofSeconds(1));
                recs.forEach(r -> {
                    try {
                        Object val = r.value();
                        if (val instanceof org.steamproject.events.GameReleasedEvent) {
                            org.steamproject.events.GameReleasedEvent evt = (org.steamproject.events.GameReleasedEvent) val;
                            handleGameReleased(evt);
                        } else {
                            System.out.println("PublisherConsumer ignored event type: " + (val != null ? val.getClass() : null));
                        }
                    } catch (Exception ex) { ex.printStackTrace(); }
                });
            }
        } finally {
            consumer.close();
        }
    }

    public void handleGameReleased(org.steamproject.events.GameReleasedEvent evt) {
        try {
            String eventId = evt.getEventId() == null ? "" : evt.getEventId().toString();
            // de-duplication
            if (!PublisherProjection.getInstance().markEventIfNew(eventId)) {
                System.out.println("PublisherConsumer skipped duplicate eventId=" + eventId);
                return;
            }

            String pubId = evt.getPublisherId().toString();
            String summary = evt.getGameId().toString() + "|" + evt.getGameName().toString() + "|" + evt.getReleaseYear();
            PublisherProjection.getInstance().addPublishedGame(pubId, summary);
            System.out.println("PublisherConsumer processed game released publisher=" + pubId + " game=" + evt.getGameId());
        } catch (Exception ex) { ex.printStackTrace(); }
    }
}
