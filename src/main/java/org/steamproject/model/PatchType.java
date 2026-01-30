package org.steamproject.model;

/**
 * Cette approche utilise une classe d'énumération enrichie (enum avec champs et méthodes)
 * afin de respecter le principe vu en cours : les enum peuvent avoir des champs,
 * constructeurs et méthodes, ce qui les rend plus expressives qu'une simple liste de constantes.
 */
public enum PatchType {
    FIX("Correction", "Correction de bugs et problèmes"),
    ADD("Ajout", "Ajout de nouvelles fonctionnalités"),
    OPTIMIZATION("Optimisation", "Amélioration des performances");

    private final String label;
    private final String description;

    // Constructeur privé (implicite pour les enum)
    PatchType(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /**
     * Retourne le libellé court du type de patch.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Retourne la description complète du type de patch.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Vérifie si ce type de patch est critique.
    */
    public boolean isCritical() {
        return this == FIX;
    }
}
