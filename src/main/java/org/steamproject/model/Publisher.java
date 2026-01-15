package org.steamproject.model;

import org.steamproject.model.Game;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Publisher {
    private String id;
    private String name;
    private Integer foundedYear;
    private List<String> platforms;
    private List<String> genres;
    private Integer gamesPublished;
    private Integer activeGames;
    private Integer totalIncidents;
    private Double averageRating;
    private Integer reactivity; // pourcentage de réactivité côté éditeur (0..100)

    public Publisher() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getFoundedYear() { return foundedYear; }
    public void setFoundedYear(Integer foundedYear) { this.foundedYear = foundedYear; }

    public List<String> getPlatforms() { return platforms; }
    public void setPlatforms(List<String> platforms) { this.platforms = platforms; }

    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres; }

    public Integer getGamesPublished() { return gamesPublished; }
    public void setGamesPublished(Integer gamesPublished) { this.gamesPublished = gamesPublished; }

    public Integer getActiveGames() { return activeGames; }
    public void setActiveGames(Integer activeGames) { this.activeGames = activeGames; }

    public Integer getTotalIncidents() { return totalIncidents; }
    public void setTotalIncidents(Integer totalIncidents) { this.totalIncidents = totalIncidents; }

    public Double getAverageRating() { return averageRating; }
    public void setAverageRating(Double averageRating) { this.averageRating = averageRating; }

    public Integer getReactivity() { return reactivity; }
    public void setReactivity(Integer reactivity) { this.reactivity = reactivity; }

    @Override
    public String toString() {
        return "Publisher{" + "id='" + id + '\'' + ", name='" + name + '\'' + '}';
    }
}
