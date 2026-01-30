package org.steamproject.service;

import org.steamproject.model.PriceUpdate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de gestion des prix avec algorithme qualité/demande.
 * 
 * Prix dynamique basé sur :
 * - Prix de base (setBasePrice ou premier PriceUpdate)
 * - Facteur qualité : basé sur les ratings moyens (0.8 à 1.2)
 * - Facteur demande : basé sur les ventes récentes (0.9 à 1.3)
 * - Promotions ponctuelles
 */
public class PriceService {
    private final Map<String, Double> basePrice = new ConcurrentHashMap<>();
    private final Map<String, Double> current = new ConcurrentHashMap<>();
    private final Map<String, List<PriceUpdate>> history = new ConcurrentHashMap<>();
    
    // Facteurs de pricing dynamique
    private final Map<String, Double> qualityFactors = new ConcurrentHashMap<>();  // Rating-based
    private final Map<String, Double> demandFactors = new ConcurrentHashMap<>();   // Sales-based
    private final Map<String, Double> promotionFactors = new ConcurrentHashMap<>(); // Promotions
    
    // Statistiques pour le calcul de la demande
    private final Map<String, Integer> recentSalesCount = new ConcurrentHashMap<>();
    private final Map<String, Double> averageRatings = new ConcurrentHashMap<>();

    // Constantes de l'algorithme
    private static final double DEFAULT_BASE_PRICE = 29.99;
    private static final double MIN_QUALITY_FACTOR = 0.8;
    private static final double MAX_QUALITY_FACTOR = 1.2;
    private static final double MIN_DEMAND_FACTOR = 0.9;
    private static final double MAX_DEMAND_FACTOR = 1.3;
    private static final int HIGH_DEMAND_THRESHOLD = 100; // ventes récentes
    private static final int LOW_DEMAND_THRESHOLD = 10;

    /**
     * Définit le prix de base d'un jeu.
     */
    public synchronized void setBasePrice(String gameId, double price) {
        if (gameId == null || price < 0) return;
        basePrice.put(gameId, price);
        recalculatePrice(gameId);
    }

    /**
     * Applique une mise à jour de prix (événement externe).
     * Utilisation des accesseurs du record PriceUpdate (gameId(), newPrice())
     * au lieu des getters traditionnels.
     */
    public synchronized void applyPriceUpdate(PriceUpdate upd) {
        if (upd == null || upd.gameId() == null) return;
        
        // Mettre à jour le prix de base si c'est la première fois
        if (!basePrice.containsKey(upd.gameId())) {
            basePrice.put(upd.gameId(), upd.newPrice());
        }
        
        current.put(upd.gameId(), upd.newPrice());
        List<PriceUpdate> list = history.computeIfAbsent(upd.gameId(), k -> new ArrayList<>());
        list.add(upd);
    }

    /**
     * Met à jour le facteur qualité basé sur les ratings.
     * @param gameId L'ID du jeu
     * @param avgRating Le rating moyen (1.0 à 5.0)
     */
    public synchronized void updateQualityFactor(String gameId, double avgRating) {
        if (gameId == null) return;
        averageRatings.put(gameId, avgRating);
        
        // Convertir le rating (1-5) en facteur (0.8-1.2)
        // Rating 1 = 0.8, Rating 3 = 1.0, Rating 5 = 1.2
        double factor = MIN_QUALITY_FACTOR + ((avgRating - 1.0) / 4.0) * (MAX_QUALITY_FACTOR - MIN_QUALITY_FACTOR);
        factor = Math.max(MIN_QUALITY_FACTOR, Math.min(MAX_QUALITY_FACTOR, factor));
        qualityFactors.put(gameId, factor);
        
        recalculatePrice(gameId);
    }

    /**
     * Met à jour le facteur demande basé sur les ventes récentes.
     * @param gameId L'ID du jeu
     * @param salesCount Nombre de ventes récentes (ex: dernière semaine)
     */
    public synchronized void updateDemandFactor(String gameId, int salesCount) {
        if (gameId == null) return;
        recentSalesCount.put(gameId, salesCount);
        
        // Convertir les ventes en facteur de demande
        double factor;
        if (salesCount >= HIGH_DEMAND_THRESHOLD) {
            // Haute demande = prix plus élevé
            factor = MAX_DEMAND_FACTOR;
        } else if (salesCount <= LOW_DEMAND_THRESHOLD) {
            // Faible demande = prix réduit
            factor = MIN_DEMAND_FACTOR;
        } else {
            // Interpolation linéaire
            double ratio = (double)(salesCount - LOW_DEMAND_THRESHOLD) / (HIGH_DEMAND_THRESHOLD - LOW_DEMAND_THRESHOLD);
            factor = MIN_DEMAND_FACTOR + ratio * (MAX_DEMAND_FACTOR - MIN_DEMAND_FACTOR);
        }
        demandFactors.put(gameId, factor);
        
        recalculatePrice(gameId);
    }

    /**
     * Applique une promotion temporaire.
     * @param gameId L'ID du jeu
     * @param discountPercent Le pourcentage de réduction (0-100)
     */
    public synchronized void applyPromotion(String gameId, double discountPercent) {
        if (gameId == null || discountPercent < 0 || discountPercent > 100) return;
        promotionFactors.put(gameId, 1.0 - (discountPercent / 100.0));
        recalculatePrice(gameId);
    }

    /**
     * Retire une promotion.
     */
    public synchronized void removePromotion(String gameId) {
        if (gameId == null) return;
        promotionFactors.remove(gameId);
        recalculatePrice(gameId);
    }

    /**
     * Recalcule le prix actuel en fonction de tous les facteurs.
     */
    private void recalculatePrice(String gameId) {
        double base = basePrice.getOrDefault(gameId, DEFAULT_BASE_PRICE);
        double quality = qualityFactors.getOrDefault(gameId, 1.0);
        double demand = demandFactors.getOrDefault(gameId, 1.0);
        double promo = promotionFactors.getOrDefault(gameId, 1.0);
        
        double newPrice = base * quality * demand * promo;
        // Arrondir à 2 décimales
        newPrice = Math.round(newPrice * 100.0) / 100.0;
        // Prix minimum
        newPrice = Math.max(0.99, newPrice);
        
        current.put(gameId, newPrice);
    }

    /**
     * Récupère le prix actuel d'un jeu (calculé dynamiquement).
     */
    public Double getPrice(String gameId) {
        if (gameId == null) return null;
        // Si pas de prix actuel, retourner le prix de base ou null
        if (!current.containsKey(gameId)) {
            return basePrice.get(gameId);
        }
        return current.get(gameId);
    }

    /**
     * Récupère le prix de base (avant facteurs).
     */
    public Double getBasePrice(String gameId) {
        if (gameId == null) return null;
        return basePrice.get(gameId);
    }

    /**
     * Récupère l'historique des prix.
     */
    public List<PriceUpdate> getPriceHistory(String gameId) {
        if (gameId == null) return Collections.emptyList();
        return Collections.unmodifiableList(history.getOrDefault(gameId, new ArrayList<>()));
    }

    /**
     * Récupère les facteurs actuels pour un jeu (pour debug/affichage).
     */
    public PriceFactors getPriceFactors(String gameId) {
        return new PriceFactors(
            basePrice.getOrDefault(gameId, DEFAULT_BASE_PRICE),
            qualityFactors.getOrDefault(gameId, 1.0),
            demandFactors.getOrDefault(gameId, 1.0),
            promotionFactors.getOrDefault(gameId, 1.0),
            current.getOrDefault(gameId, DEFAULT_BASE_PRICE)
        );
    }

    /**
     * Récupère le rating moyen stocké pour un jeu.
     */
    public Double getAverageRating(String gameId) {
        return averageRatings.get(gameId);
    }

    /**
     * Récupère le nombre de ventes récentes stocké.
     */
    public Integer getRecentSalesCount(String gameId) {
        return recentSalesCount.get(gameId);
    }

    /**
     * Vérifie si un jeu est en promotion.
     */
    public boolean isOnPromotion(String gameId) {
        Double factor = promotionFactors.get(gameId);
        return factor != null && factor < 1.0;
    }

    /**
     * Retourne le pourcentage de réduction actuel.
     */
    public double getPromotionPercent(String gameId) {
        Double factor = promotionFactors.get(gameId);
        if (factor == null || factor >= 1.0) return 0.0;
        return (1.0 - factor) * 100.0;
    }

    /**
     * Classe interne représentant les facteurs de prix.
     */
    public static class PriceFactors {
        public final double basePrice;
        public final double qualityFactor;
        public final double demandFactor;
        public final double promotionFactor;
        public final double finalPrice;

        public PriceFactors(double basePrice, double qualityFactor, double demandFactor, 
                           double promotionFactor, double finalPrice) {
            this.basePrice = basePrice;
            this.qualityFactor = qualityFactor;
            this.demandFactor = demandFactor;
            this.promotionFactor = promotionFactor;
            this.finalPrice = finalPrice;
        }

        @Override
        public String toString() {
            return String.format("PriceFactors{base=%.2f, quality=%.2f, demand=%.2f, promo=%.2f, final=%.2f}",
                basePrice, qualityFactor, demandFactor, promotionFactor, finalPrice);
        }
    }
}
