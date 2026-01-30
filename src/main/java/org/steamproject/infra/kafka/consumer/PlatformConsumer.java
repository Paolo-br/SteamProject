package org.steamproject.infra.kafka.consumer;

/**
 * Consommateur Kafka dédié aux événements de mise à jour du catalogue des plateformes.
 * 
 * Ce consommateur écoute le topic des événements PlatformCatalogUpdateEvent
 * et maintient à jour la projection PlatformProjection en temps réel.
 * Il utilise Avro pour la désérialisation des événements avec Schema Registry.
 */
public class PlatformConsumer {
    private final org.apache.kafka.clients.consumer.KafkaConsumer<String, Object> consumer;
    private final String topic;

    /**
     * Construit un nouveau consommateur Kafka pour les événements de plateforme.
     * 
     * Configure automatiquement la désérialisation Avro avec Schema Registry
     * et démarre à partir du début du topic (earliest).
     * 
     * @param bootstrap Adresse du serveur Kafka (ex: localhost:9092)
     * @param schemaRegistryUrl URL du Schema Registry Confluent
     * @param topic Nom du topic à consommer
     * @param groupId Identifiant du groupe de consommateurs
     */
    public PlatformConsumer(String bootstrap, String schemaRegistryUrl, String topic, String groupId) {
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

    /**
     * Démarre la boucle de consommation des événements.
     * 
     * Cette méthode bloque le thread courant et consomme en continu les événements
     * du topic. Seuls les événements de type PlatformCatalogUpdateEvent sont traités,
     * les autres types sont ignorés avec un message de log.
     * 
     * La méthode garantit la fermeture propre du consommateur en cas d'arrêt.
     */
    public void start() {
        System.out.println("PlatformConsumer started, listening to " + topic);
        try {
            while (true) {
                var recs = consumer.poll(java.time.Duration.ofSeconds(1));
                recs.forEach(r -> {
                    try {
                        Object val = r.value();
                        if (val instanceof org.steamproject.events.PlatformCatalogUpdateEvent) {
                            org.steamproject.events.PlatformCatalogUpdateEvent evt = (org.steamproject.events.PlatformCatalogUpdateEvent) val;
                            handleCatalogUpdate(evt);
                        } else {
                            System.out.println("PlatformConsumer ignored event type: " + (val != null ? val.getClass() : null));
                        }
                    } catch (Exception ex) { ex.printStackTrace(); }
                });
            }
        } finally { consumer.close(); }
    }

    /**
     * Traite un événement de mise à jour du catalogue de plateforme.
     * 
     * Extrait les informations de l'événement et met à jour la projection
     * en ajoutant ou supprimant le jeu du catalogue selon l'action spécifiée.
     * 
     * @param evt Événement de mise à jour du catalogue à traiter
     */
    public void handleCatalogUpdate(org.steamproject.events.PlatformCatalogUpdateEvent evt) {
        try {
            String platformId = evt.getPlatformId().toString();
            String summary = evt.getGameId().toString() + "|" + (evt.getGameName() != null ? evt.getGameName().toString() : "");
            boolean add = evt.getAction() == org.steamproject.events.CatalogAction.ADD;
            PlatformProjection.getInstance().applyCatalogChange(platformId, summary, add);
            System.out.println("PlatformConsumer applied catalog " + (add ? "ADD" : "REMOVE") + " for platform=" + platformId + " game=" + evt.getGameId());
        } catch (Exception ex) { ex.printStackTrace(); }
    }
}