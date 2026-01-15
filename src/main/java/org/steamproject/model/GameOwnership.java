package org.steamproject.model;

public class GameOwnership {
    private String gameId;
    private String gameName;
    private String purchaseDate;
    private Integer playtime;
    private String lastPlayed;
    private Double pricePaid;

    public GameOwnership() {}

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public String getGameName() { return gameName; }
    public void setGameName(String gameName) { this.gameName = gameName; }

    public String getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(String purchaseDate) { this.purchaseDate = purchaseDate; }

    public Integer getPlaytime() { return playtime; }
    public void setPlaytime(Integer playtime) { this.playtime = playtime; }

    public String getLastPlayed() { return lastPlayed; }
    public void setLastPlayed(String lastPlayed) { this.lastPlayed = lastPlayed; }

    public Double getPricePaid() { return pricePaid; }
    public void setPricePaid(Double pricePaid) { this.pricePaid = pricePaid; }

    @Override
    public String toString() {
        return "GameOwnership{" +
                "gameId='" + gameId + '\'' +
                ", gameName='" + gameName + '\'' +
                ", playtime=" + playtime +
                '}';
    }
}
