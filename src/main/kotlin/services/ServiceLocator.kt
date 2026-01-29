package org.example.services

import org.example.services.api.DataService
import org.example.services.api.JavaBackedDataService
import org.example.services.api.ProjectionDataService
import org.example.model.*

/**
 * Patron Service Locator pour l'injection de dépendances.
 * Fournit une résolution centralisée des implémentations de `DataService`.
 */
object ServiceLocator {
    // Mock implementation removed; rely on available services (projection/java-backed)

    // Tentative d'instanciation du service Java local (seed CSV)
    private val javaBacked: DataService? = try {
        JavaBackedDataService()
    } catch (ex: Throwable) {
        null
    }

    // Service basé sur les projections (appelle les endpoints REST de projection).
    private val projectionService: DataService? = try {
        ProjectionDataService()
    } catch (ex: Throwable) {
        null
    }


    // Résolution des variables d'environnement / propriétés système en booléens
    private val useProjections: Boolean = ((System.getProperty("use.projections")
        ?: System.getenv("USE_PROJECTIONS")
        ?: "true").toLowerCase() == "true")

    val dataService: DataService
        get() {
            // Par défaut on préfère le service de projection (mode production). Si indisponible,
            // on revient au service Java local. Si aucun service n'est disponible, lever une exception.
            if (useProjections) {
                return projectionService ?: javaBacked
                    ?: throw IllegalStateException("Aucun DataService disponible: ProjectionDataService et JavaBackedDataService indisponibles")
            }
            // Si les projections sont désactivées : préférer le service Java local puis la projection
            return javaBacked ?: projectionService
                ?: throw IllegalStateException("Aucun DataService disponible: JavaBackedDataService et ProjectionDataService indisponibles")
        }

    /** Initialise les services (à appeler au démarrage de l'application). */
    fun initialize() {
        val which = when {
            useProjections && projectionService != null -> "ProjectionDataService"
            javaBacked != null -> "JavaBackedDataService"
            else -> "Aucun DataService disponible"
        }
        println("Initialisation des services frontaux (utilisation : $which)...")
    }

    /** Arrête proprement les services (à appeler à la fermeture de l'application). */
    fun shutdown() {
        println("Arrêt des services frontaux")
    }
}
