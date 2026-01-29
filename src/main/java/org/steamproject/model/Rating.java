package org.steamproject.model;

/**
 * Représente une évaluation/note donnée par un joueur à un jeu.
 *
 * Cette classe utilise le motif Builder (classe interne) pour construire
 * des instances de manière fluide et sécurisée.
 */
public class Rating {
    private final String username;
    private final int rating; // 1..5
    private final String comment;
    private final String date;

    /**
     * Constructeur privé : force l'utilisation du Builder.
    */
    private Rating(RatingBuilder builder) {
        this.username = builder.username;
        this.rating = builder.rating;
        this.comment = builder.comment;
        this.date = builder.date;
    }
    
    /**
     * Constructeur par défaut pour compatibilité avec la désérialisation.
     */
    public Rating() {
        this.username = null;
        this.rating = 0;
        this.comment = null;
        this.date = null;
    }

    /**
     * Point d'entrée pour créer un Rating via le Builder.
     */
    public static RatingBuilder builder() {
        return new RatingBuilder();
    }

    // Accesseurs (getters uniquement pour l'immuabilité)
    public String getUsername() { return username; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }
    public String getDate() { return date; }
    
    /**
     * Vérifie si l'évaluation est positive (>= 4 étoiles).
     */
    public boolean isPositive() {
        return rating >= 4;
    }

    @Override
    public String toString() {
        return "Rating{" + "username='" + username + '\'' + ", rating=" + rating + '}';
    }

    /**
     * Classe interne static Builder pour construire des instances de Rating.
    */
    public static class RatingBuilder {
        private String username;
        private int rating;
        private String comment;
        private String date;

        RatingBuilder() {}

        public RatingBuilder username(String username) {
            this.username = username;
            return this;
        }

        public RatingBuilder rating(int rating) {
            this.rating = rating;
            return this;
        }

        public RatingBuilder comment(String comment) {
            this.comment = comment;
            return this;
        }

        public RatingBuilder date(String date) {
            this.date = date;
            return this;
        }

        /**
         * Construit l'objet Rating avec validation.
        */
        public Rating build() {
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("Le nom d'utilisateur est obligatoire");
            }
            if (rating < 1 || rating > 5) {
                throw new IllegalArgumentException("La note doit être entre 1 et 5");
            }
            return new Rating(this);
        }
    }
}
