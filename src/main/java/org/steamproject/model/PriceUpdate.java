package org.steamproject.model;

/**
 * Représente une mise à jour de prix pour un jeu.

 */
public record PriceUpdate(
    String id,
    String gameId,
    Double oldPrice,
    Double newPrice,
    String currency,
    String date,
    long timestamp
) {
    /**
     * Constructeur compact avec validation.
    */
    public PriceUpdate {
        if (newPrice != null && newPrice < 0) {
            throw new IllegalArgumentException("Le nouveau prix ne peut pas être négatif");
        }
        if (currency == null) {
            currency = "EUR"; // Devise par défaut
        }
    }
    
    /**
     * Méthode utilitaire pour créer un PriceUpdate avec timestamp automatique.
     */
    public static PriceUpdate now(String id, String gameId, Double oldPrice, 
                                   Double newPrice, String currency, String date) {
        return new PriceUpdate(id, gameId, oldPrice, newPrice, currency, date, 
                               System.currentTimeMillis());
    }
    
    /**
     * Calcule le pourcentage de variation du prix.
    */
    public double getPriceChangePercent() {
        if (oldPrice == null || oldPrice == 0 || newPrice == null) {
            return 0.0;
        }
        return ((newPrice - oldPrice) / oldPrice) * 100.0;
    }
    
    /**
     * Vérifie si c'est une réduction de prix.
     */
    public boolean isDiscount() {
        return oldPrice != null && newPrice != null && newPrice < oldPrice;
    }
    
    /**
     * Vérifie si c'est une augmentation de prix.
     */
    public boolean isPriceIncrease() {
        return oldPrice != null && newPrice != null && newPrice > oldPrice;
    }
    
    @Override
    public String toString() {
        return "PriceUpdate{" + "gameId='" + gameId + '\'' + ", newPrice=" + newPrice + '}';
    }
}
