package org.steamproject.model;

public class PriceUpdate {
    private String id;
    private String gameId;
    private Double oldPrice;
    private Double newPrice;
    private String currency;
    private String date;
    private long timestamp;

    public PriceUpdate() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public Double getOldPrice() { return oldPrice; }
    public void setOldPrice(Double oldPrice) { this.oldPrice = oldPrice; }

    public Double getNewPrice() { return newPrice; }
    public void setNewPrice(Double newPrice) { this.newPrice = newPrice; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return "PriceUpdate{" + "gameId='" + gameId + '\'' + ", newPrice=" + newPrice + '}';
    }
}
