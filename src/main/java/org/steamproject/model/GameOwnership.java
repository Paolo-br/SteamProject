package org.steamproject.model;

/**
 * Représente la possession d'un jeu par un joueur.
*/
public record GameOwnership(
    String gameId,
    String gameName,
    String purchaseDate,
    Integer playtime,
    String lastPlayed,
    Double pricePaid
) {
    /**
     * Constructeur compact avec valeurs par défaut.
     */
    public GameOwnership {
        if (playtime == null) playtime = 0;
        if (pricePaid == null) pricePaid = 0.0;
    }
    
    /**
     * Constructeur simplifié pour les cas courants.
     */
    public GameOwnership(String gameId, String gameName, String purchaseDate) {
        this(gameId, gameName, purchaseDate, 0, null, 0.0);
    }
    
    /**
     * Crée une nouvelle instance avec le temps de jeu mis à jour.
    */
    public GameOwnership withPlaytime(Integer newPlaytime) {
        return new GameOwnership(gameId, gameName, purchaseDate, newPlaytime, lastPlayed, pricePaid);
    }
    
    /**
     * Crée une nouvelle instance avec la date de dernière session mise à jour.
     */
    public GameOwnership withLastPlayed(String newLastPlayed) {
        return new GameOwnership(gameId, gameName, purchaseDate, playtime, newLastPlayed, pricePaid);
    }
    
    /**
     * Crée une nouvelle instance avec le temps de jeu incrémenté.
    */
    public GameOwnership withAdditionalPlaytime(int hoursToAdd) {
        return new GameOwnership(gameId, gameName, purchaseDate, 
                                 (playtime != null ? playtime : 0) + hoursToAdd, 
                                 lastPlayed, pricePaid);
    }
    
    /**
     * Vérifie si le joueur a suffisamment joué pour laisser un avis.
     * Règle que l'on a choisi d'imposer: minimum 2 heures de jeu pour pouvoir évaluer.
     */
    public boolean canLeaveReview() {
        return playtime != null && playtime >= 2;
    }
    
    @Override
    public String toString() {
        return "GameOwnership{" +
                "gameId='" + gameId + '\'' +
                ", gameName='" + gameName + '\'' +
                ", playtime=" + playtime +
                '}';
    }
}
