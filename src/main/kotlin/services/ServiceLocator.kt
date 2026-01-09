package org.example.services

import org.example.services.api.DataService
import org.example.services.api.MockDataService
import org.example.services.kafka.FakeKafkaService

/**
 * Service Locator pattern pour l'injection de dépendances.
 *
 * Centralise l'accès aux services de l'application.
 * Permet de basculer facilement entre mock et implémentation réelle.
 *
 * Usage dans l'UI :
 * ```
 * val dataService = ServiceLocator.dataService
 * val games = dataService.getCatalog()
 * ```
 */
object ServiceLocator {

    /**
     * Service de données (actuellement mock, remplaçable par RealDataService).
     */
    private val mockDataService = MockDataService()

    /**
     * Service Kafka simulé.
     */
    private val _kafkaService = FakeKafkaService(mockDataService)

    /**
     * Interface de service de données exposée à l'UI.
     */
    val dataService: DataService
        get() = mockDataService

    /**
     * Service Kafka pour les événements temps réel.
     */
    val kafkaService: FakeKafkaService
        get() = _kafkaService

    /**
     * Initialise les services (à appeler au démarrage de l'application).
     */
    fun initialize() {
        println("Initialisation des services...")

        // Démarrer le service Kafka simulé
        // Les événements seront émis automatiquement toutes les X secondes
        _kafkaService.start(
            patchIntervalSeconds = 20,      // Nouveau patch toutes les 20 secondes
            priceIntervalSeconds = 15,       // Changement de prix toutes les 15 secondes
            incidentIntervalSeconds = 25     // Incident toutes les 25 secondes
        )

        println("Services initialisés et actifs")
    }

    /**
     * Arrête proprement les services (à appeler à la fermeture de l'application).
     */
    fun shutdown() {
        println("Arrêt des services...")
        _kafkaService.shutdown()
        println("Services arrêtés")
    }
}

