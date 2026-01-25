package org.steamproject.infra.kafka.consumer;

public class PlatformConsumerApp {
    public static void main(String[] args) {
        String bootstrap = System.getProperty("kafka.bootstrap", "localhost:9092");
        String sr = System.getProperty("schema.registry", "http://localhost:8081");
        String topic = System.getProperty("kafka.topic.platform", "platform-catalog-events");
        String group = System.getProperty("kafka.group", "platform-consumer-group");

        PlatformConsumer pc = new PlatformConsumer(bootstrap, sr, topic, group);
        pc.start();
    }
}
