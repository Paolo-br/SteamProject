package org.steamproject.infra.kafka.consumer;

public class PublisherConsumerApp {
    public static void main(String[] args) {
        String bootstrap = System.getProperty("kafka.bootstrap", "localhost:9092");
        String sr = System.getProperty("schema.registry", "http://localhost:8081");
        String topic = System.getProperty("kafka.topic.pub", "game-released-events,game-published-events,game-updated-events,patch-published-events,dlc-published-events,game-version-deprecated-events,editor-responded-events");
        String group = System.getProperty("kafka.group", "publisher-consumer-group");

        PublisherConsumer pc = new PublisherConsumer(bootstrap, sr, topic, group);
        pc.start();
    }
}
