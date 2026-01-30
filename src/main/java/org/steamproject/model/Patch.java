package org.steamproject.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Représente un patch / une mise à jour logicielle pour un jeu.
 *
 * Cette classe joue le rôle de transporteur de données contenant les
 * métadonnées du patch (versions, type, description, liste de changements,
 * taille et date de publication). On peut ajouter une validation à la
 * création si nécessaire.
 */
public class Patch {
    private String id;
    private String gameId;
    private String gameName;
    private String platform;
    private String oldVersion;
    private String newVersion;
    private PatchType type;
    private String description;
    private List<Change> changes = new ArrayList<>();
    private Integer sizeInMB;
    private String releaseDate;
    private long timestamp;

    public Patch() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public String getGameName() { return gameName; }
    public void setGameName(String gameName) { this.gameName = gameName; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getOldVersion() { return oldVersion; }
    public void setOldVersion(String oldVersion) { this.oldVersion = oldVersion; }

    public String getNewVersion() { return newVersion; }
    public void setNewVersion(String newVersion) { this.newVersion = newVersion; }

    public PatchType getType() { return type; }
    public void setType(PatchType type) { this.type = type; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<Change> getChanges() { return changes; }
    public void setChanges(List<Change> changes) { this.changes = changes; }

    public Integer getSizeInMB() { return sizeInMB; }
    public void setSizeInMB(Integer sizeInMB) { this.sizeInMB = sizeInMB; }

    public String getReleaseDate() { return releaseDate; }
    public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return "Patch{" +
                "id='" + id + '\'' +
                ", gameId='" + gameId + '\'' +
                ", gameName='" + gameName + '\'' +
                ", newVersion='" + newVersion + '\'' +
                ", type=" + type +
                '}';
    }
}
