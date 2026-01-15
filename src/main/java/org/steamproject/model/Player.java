package org.steamproject.model;

public class Player {
    private String id;
    private String username;
    private String email;
    private String registrationDate; 
    private Integer totalPlaytime;
    private String lastEvaluationDate;
    private Integer evaluationsCount;
    private java.util.List<GameOwnership> library;

    public Player() {}

    public java.util.List<GameOwnership> getLibrary() { return library; }
    public void setLibrary(java.util.List<GameOwnership> library) { this.library = library; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRegistrationDate() { return registrationDate; }
    public void setRegistrationDate(String registrationDate) { this.registrationDate = registrationDate; }

    public Integer getTotalPlaytime() { return totalPlaytime; }
    public void setTotalPlaytime(Integer totalPlaytime) { this.totalPlaytime = totalPlaytime; }

    public String getLastEvaluationDate() { return lastEvaluationDate; }
    public void setLastEvaluationDate(String lastEvaluationDate) { this.lastEvaluationDate = lastEvaluationDate; }

    public Integer getEvaluationsCount() { return evaluationsCount; }
    public void setEvaluationsCount(Integer evaluationsCount) { this.evaluationsCount = evaluationsCount; }

    @Override
    public String toString() {
        return "Player{" +
                "id='" + id + '\'' +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", totalPlaytime=" + totalPlaytime +
                '}';
    }
}
