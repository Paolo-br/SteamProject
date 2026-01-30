package org.steamproject.model;

/**
 * Cette classe utilise le pattern record pour représenter un transporteur
 * de données immuable. Conformément au cours, un record :
 * - génère automatiquement toString(), hashCode(), equals() et les accesseurs
 * - protège de la modification des champs (déclarés final)
 * - permet une validation lors de la construction via un constructeur compact
 * 
 */
public record Change(PatchType type, String description) {
    
    /**
     * Constructeur compact avec validation.
     * Valide que la description n'est pas null ou vide.
     */
    public Change {
        if (type == null) {
            throw new IllegalArgumentException("Le type de changement ne peut pas être null");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("La description du changement ne peut pas être vide");
        }
    }
    
    /**
     * Méthode utilitaire pour créer un changement de type FIX.
     * Les records peuvent avoir des méthodes statiques et d'instance.
     */
    public static Change fix(String description) {
        return new Change(PatchType.FIX, description);
    }
    
    /**
     * Méthode utilitaire pour créer un changement de type ADD.
     */
    public static Change add(String description) {
        return new Change(PatchType.ADD, description);
    }
    
    /**
     * Méthode utilitaire pour créer un changement de type OPTIMIZATION.
     */
    public static Change optimization(String description) {
        return new Change(PatchType.OPTIMIZATION, description);
    }
}
