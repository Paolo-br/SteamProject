package org.example.services

import org.example.services.api.DataService
import org.example.services.api.JavaBackedDataService
import org.example.services.api.MockDataService
import org.example.services.api.ProjectionDataService
import org.example.model.*

/**
 * Service Locator pattern pour l'injection de dépendances.
 */
object ServiceLocator {
    /** Service de données de secours (retourne des listes vides). */
    private val mockDataService: DataService = MockDataService()

    // Try to instantiate Java-backed service (local JVM service using CSV seed)
    private val javaBacked: DataService? = try {
        JavaBackedDataService()
    } catch (ex: Throwable) {
        null
    }

    // Projection-backed service (calls Streams/Projection REST endpoints).
    private val projectionService: DataService? = try {
        ProjectionDataService()
    } catch (ex: Throwable) {
        null
    }

    /**
     * Feature toggles (env or system properties):
     * - USE_PROJECTIONS=true|false (default true)
     * - FORCE_MOCK=true forces MockDataService regardless
     */
    // Resolve environment / system properties to booleans
    private val useProjections: Boolean = ((System.getProperty("use.projections")
        ?: System.getenv("USE_PROJECTIONS")
        ?: "true").toLowerCase() == "true")
    private val forceMock: Boolean = ((System.getProperty("force.mock") ?: System.getenv("FORCE_MOCK") ?: "false").toLowerCase() == "true")

    val dataService: DataService
        get() {
            if (forceMock) return mockDataService
            // Prefer projection service by default (production mode). If unavailable,
            // fall back to Java-backed service, then mock as last resort.
            if (useProjections) {
                return projectionService ?: javaBacked ?: mockDataService
            }
            // Projections disabled: prefer Java-backed then mock
            return javaBacked ?: mockDataService
        }

    /** Initialise les services (à appeler au démarrage de l'application). */
    fun initialize() {
        val which = when {
            forceMock -> "MockDataService (forced)"
            useProjections && projectionService != null -> "ProjectionDataService"
            javaBacked != null -> "JavaBackedDataService"
            else -> "MockDataService"
        }
        println("Initialisation des services front-only (using $which)...")
    }

    /** Arrête proprement les services (à appeler à la fermeture de l'application). */
    fun shutdown() {
        println("Arrêt des services front-only")
    }
}
