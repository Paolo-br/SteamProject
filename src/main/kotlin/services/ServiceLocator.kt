package org.example.services

import org.example.services.api.DataService
import org.example.services.api.MockDataService

/**
 * Service Locator pattern pour l'injection de dépendances.
 *
 * Version front-only :
 * - Ne conserve que le service de données mock
 * - Aucune dépendance à Kafka ou à un backend réel
 */
object ServiceLocator {

    /**
     * Service de données (actuellement mock, remplaçable par une implémentation réelle plus tard).
     */
    private val mockDataService = MockDataService()

    /**
     * Interface de service de données exposée à l'UI.
     */
    val dataService: DataService
        get() = mockDataService

    /**
     * Initialise les services (à appeler au démarrage de l'application).
     */
    fun initialize() {
        println("Initialisation des services front-only (MockDataService)...")
    }

    /**
     * Arrête proprement les services (à appeler à la fermeture de l'application).
     */
    fun shutdown() {
        println("Arrêt des services front-only")
    }
}
