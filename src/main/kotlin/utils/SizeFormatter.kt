package org.example.utils

/**
 * Utilitaires pour le formatage des tailles de fichiers.
 */
object SizeFormatter {
    
    /**
     * Formate une taille en Mo vers une chaîne lisible (Mo ou Go).
     * 
     * @param sizeInMB Taille en mégaoctets
     * @return Chaîne formatée (ex: "1.5 Go" ou "500 Mo")
     */
    fun formatSize(sizeInMB: Long): String {
        return when {
            sizeInMB <= 0 -> "N/A"
            sizeInMB >= 1024 -> {
                val sizeInGB = sizeInMB / 1024.0
                String.format("%.1f Go", sizeInGB)
            }
            else -> "$sizeInMB Mo"
        }
    }
    
    /**
     * Formate une taille en Mo vers une chaîne lisible (Mo ou Go).
     * Version pour Int.
     */
    fun formatSize(sizeInMB: Int): String = formatSize(sizeInMB.toLong())
}
