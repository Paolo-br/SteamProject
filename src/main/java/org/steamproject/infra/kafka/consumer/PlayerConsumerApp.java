package org.steamproject.infra.kafka.consumer;

/**
 * Small launcher for `PlayerConsumer` to be used by Gradle JavaExec task.
 */
public class PlayerConsumerApp {
    public static void main(String[] args) {
        String bootstrap = System.getProperty("kafka.bootstrap", "localhost:9092");
        String sr = System.getProperty("schema.registry", "http://localhost:8081");
        String topic = System.getProperty("kafka.topic", "game-purchase-events");
        String group = System.getProperty("kafka.group", "player-consumer-group");

        PlayerConsumer consumer = new PlayerConsumer(bootstrap, sr, topic, group);
        consumer.start();
    }
}
