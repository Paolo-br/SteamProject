package org.steamproject.model;

/**
 * Représente un joueur sur la plateforme.
 * Contient les informations protégées par le RGPD.
 */
public class Player {
    private String id;
    private String username;            // Pseudo unique (RGPD)
    private String email;
    private String registrationDate;
    // === Champs RGPD ===
    private String firstName;           // Prénom (RGPD)
    private String lastName;            // Nom (RGPD)
    private String dateOfBirth;         // Date de naissance ISO 8601 (RGPD)
    private Boolean gdprConsent;        // Consentement RGPD accepté
    private String gdprConsentDate;     // Date du consentement RGPD
    // === Statistiques ===
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

    
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public Boolean getGdprConsent() { return gdprConsent; }
    public void setGdprConsent(Boolean gdprConsent) { this.gdprConsent = gdprConsent; }

    public String getGdprConsentDate() { return gdprConsentDate; }
    public void setGdprConsentDate(String gdprConsentDate) { this.gdprConsentDate = gdprConsentDate; }

    @Override
    public String toString() {
        return "Player{" +
                "id='" + id + '\'' +
                ", username='" + username + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", email='" + email + '\'' +
                ", dateOfBirth='" + dateOfBirth + '\'' +
                ", totalPlaytime=" + totalPlaytime +
                '}';
    }
}
