package org.steamproject.model;

/**
 * Représente un DLC (contenu téléchargeable) pour un jeu.
 * Le DLC nécessite une version minimale du jeu principal.
 */
public class DLC {
    private String id;
    private String parentGameId;
    private String name;
    private String description;
    private double price;
    private String minGameVersion; // Version minimale requise du jeu principal
    private String releaseDate;
    private long sizeInMB;
    private boolean standalone; // Peut être joué sans le jeu principal

    public DLC() {}

    public DLC(String id, String parentGameId, String name, String description, 
               double price, String minGameVersion, String releaseDate, long sizeInMB) {
        this.id = id;
        this.parentGameId = parentGameId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.minGameVersion = minGameVersion;
        this.releaseDate = releaseDate;
        this.sizeInMB = sizeInMB;
        this.standalone = false;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getParentGameId() { return parentGameId; }
    public void setParentGameId(String parentGameId) { this.parentGameId = parentGameId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getMinGameVersion() { return minGameVersion; }
    public void setMinGameVersion(String minGameVersion) { this.minGameVersion = minGameVersion; }

    public String getReleaseDate() { return releaseDate; }
    public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }

    public long getSizeInMB() { return sizeInMB; }
    public void setSizeInMB(long sizeInMB) { this.sizeInMB = sizeInMB; }

    public boolean isStandalone() { return standalone; }
    public void setStandalone(boolean standalone) { this.standalone = standalone; }

    @Override
    public String toString() {
        return "DLC{" +
                "id='" + id + '\'' +
                ", parentGameId='" + parentGameId + '\'' +
                ", name='" + name + '\'' +
                ", price=" + price +
                ", minGameVersion='" + minGameVersion + '\'' +
                '}';
    }
}
