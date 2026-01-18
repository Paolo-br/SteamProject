package org.steamproject.model;

/**
 * Représente un achat effectué par un joueur.
 */
public class Purchase {
    private String purchaseId;
    private String gameId;
    private String gameName;
    private String playerId;
    private String playerUsername;
    private double pricePaid;
    private String platform;
    private long timestamp;
    private boolean isDlc;
    private String dlcId; // null si achat de jeu principal

    public Purchase() {}

    public Purchase(String purchaseId, String gameId, String gameName, String playerId, 
                    String playerUsername, double pricePaid, String platform, long timestamp) {
        this.purchaseId = purchaseId;
        this.gameId = gameId;
        this.gameName = gameName;
        this.playerId = playerId;
        this.playerUsername = playerUsername;
        this.pricePaid = pricePaid;
        this.platform = platform;
        this.timestamp = timestamp;
        this.isDlc = false;
        this.dlcId = null;
    }

    // Getters & Setters
    public String getPurchaseId() { return purchaseId; }
    public void setPurchaseId(String purchaseId) { this.purchaseId = purchaseId; }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public String getGameName() { return gameName; }
    public void setGameName(String gameName) { this.gameName = gameName; }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getPlayerUsername() { return playerUsername; }
    public void setPlayerUsername(String playerUsername) { this.playerUsername = playerUsername; }

    public double getPricePaid() { return pricePaid; }
    public void setPricePaid(double pricePaid) { this.pricePaid = pricePaid; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isDlc() { return isDlc; }
    public void setDlc(boolean dlc) { isDlc = dlc; }

    public String getDlcId() { return dlcId; }
    public void setDlcId(String dlcId) { this.dlcId = dlcId; }

    @Override
    public String toString() {
        return "Purchase{" +
                "purchaseId='" + purchaseId + '\'' +
                ", gameId='" + gameId + '\'' +
                ", gameName='" + gameName + '\'' +
                ", playerId='" + playerId + '\'' +
                ", pricePaid=" + pricePaid +
                ", platform='" + platform + '\'' +
                ", isDlc=" + isDlc +
                '}';
    }
}
