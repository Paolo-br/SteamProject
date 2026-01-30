package org.steamproject.model;

/**
 * Représente un commentaire/feedback d'un joueur sur un jeu.
 * 
 * Cette classe utilise une enum interne (nested class) enrichie pour la sévérité.
 * Conformément au cours sur les classes internes, l'enum Severity est déclarée
 * à l'intérieur de Comment car elle n'a de sens que dans ce contexte.
 */
public class Comment {
    
    /**
     * Enum interne static représentant les niveaux de sévérité.
     * 
     * Cette enum interne statique illustre plusieurs concepts du cours :
     * - Enum avec champs et méthodes (label, priorité, couleur)
     * - Classe interne static (accessible via Comment.Severity)
     * - Encapsulation de la logique de priorité dans l'enum
     */
    public enum Severity {
        LOW("Faible", 1, "#4CAF50"),
        MEDIUM("Moyen", 2, "#FF9800"),
        HIGH("Critique", 3, "#F44336");

        private final String label;
        private final int priority;
        private final String colorHex;

        Severity(String label, int priority, String colorHex) {
            this.label = label;
            this.priority = priority;
            this.colorHex = colorHex;
        }

        public String getLabel() { return label; }
        public int getPriority() { return priority; }
        public String getColorHex() { return colorHex; }
        
        /**
         * Vérifie si ce niveau nécessite un routage automatique vers le support.
         */
        public boolean requiresRouting() {
            return this == HIGH;
        }
        
        /**
         * Compare deux sévérités par priorité.
         * Utile pour le tri des commentaires.
         */
        public boolean isMoreSevereThan(Severity other) {
            return this.priority > other.priority;
        }
    }

    private String id;
    private String gameId;
    private String playerId;
    private String text;
    private Severity severity;
    private boolean routed;
    private long timestamp;

    public Comment() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public boolean isRouted() { return routed; }
    public void setRouted(boolean routed) { this.routed = routed; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    @Override
    public String toString() {
        return "Comment{" +
                "id='" + id + '\'' +
                ", severity=" + (severity != null ? severity.getLabel() : "null") +
                ", routed=" + routed +
                '}';
    }
}
