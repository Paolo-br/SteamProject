package org.example.services

import org.example.services.api.DataService
import org.example.services.api.JavaBackedDataService
import org.example.services.api.MockDataService
import org.example.model.*

/**
 * Service Locator pattern pour l'injection de dépendances.
 */
object ServiceLocator {
    /** Service de données de secours (retourne des listes vides). */
    private val mockDataService: DataService = MockDataService()

    /** Préfère le service Java-backed si disponible. */
    private val javaBacked: DataService? = try {
        JavaBackedDataService()
    } catch (ex: Throwable) {
        null
    }

    val dataService: DataService
        get() = javaBacked ?: mockDataService

    /** Initialise les services (à appeler au démarrage de l'application). */
    fun initialize() {
        val which = if (javaBacked != null) "JavaBackedDataService" else "MockDataService"
        println("Initialisation des services front-only (using $which)...")
    }

    /** Arrête proprement les services (à appeler à la fermeture de l'application). */
    fun shutdown() {
        println("Arrêt des services front-only")
    }
}
