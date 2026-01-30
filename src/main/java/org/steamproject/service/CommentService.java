package org.steamproject.service;

import org.steamproject.model.Comment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Service de gestion des commentaires/feedbacks.
*/
public class CommentService {
    private final List<Comment> store = new ArrayList<>();
    private final List<Comment> routed = new ArrayList<>();

    public synchronized Comment addComment(Comment c) {
        if (c == null) return null;
        if (c.getId() == null) c.setId(java.util.UUID.randomUUID().toString());
        if (c.getTimestamp() == 0) c.setTimestamp(java.time.Instant.now().toEpochMilli());

        // Analyse du texte pour déterminer la sévérité
        String text = c.getText() == null ? "" : c.getText().toLowerCase(Locale.ROOT);
        
        // Utilisation du switch expression moderne avec pattern matching
        // pour attribuer la sévérité en fonction des mots-clés détectés.

        Comment.Severity severity = determineSeverity(text);
        c.setSeverity(severity);
        
        // Utilisation de la méthode requiresRouting() de l'enum Severity
        // plutôt qu'une comparaison directe. L'encapsulation de la logique
        // dans l'enum rend le code plus maintenable.
        if (severity.requiresRouting()) {
            c.setRouted(true);
            routed.add(c);
        } else {
            c.setRouted(false);
        }

        store.add(c);
        return c;
    }
    
    /**
     * Détermine la sévérité en fonction du contenu du texte.
    */
    private Comment.Severity determineSeverity(String text) {
        if (text.contains("crash") || text.contains("freeze") || 
            text.contains("stability") || text.contains("disconnect") || 
            text.contains("fatal")) {
            return Comment.Severity.HIGH;
        } else if (text.contains("lag") || text.contains("slow") || 
                   text.contains("glitch")) {
            return Comment.Severity.MEDIUM;
        } else {
            return Comment.Severity.LOW;
        }
    }

    public List<Comment> getAllComments() { 
        return Collections.unmodifiableList(store); 
    }

    public List<Comment> getCommentsForGame(String gameId) {
        if (gameId == null) return Collections.emptyList();
        List<Comment> out = new ArrayList<>();
        for (Comment c : store) {
            if (gameId.equals(c.getGameId())) {
                out.add(c);
            }
        }
        return out;
    }

    public List<Comment> getRoutedComments() { 
        return Collections.unmodifiableList(routed); 
    }
    
    /**
     * Récupère les commentaires triés par sévérité décroissante.
    */
    public List<Comment> getCommentsBySeverity(String gameId) {
        List<Comment> comments = new ArrayList<>(getCommentsForGame(gameId));
        comments.sort(Comparator.comparingInt(
            c -> -c.getSeverity().getPriority() // Tri décroissant
        ));
        return comments;
    }
}
