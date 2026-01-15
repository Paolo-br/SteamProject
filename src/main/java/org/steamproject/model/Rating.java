package org.steamproject.model;

public class Rating {
    private String username;
    private int rating; // 1..5
    private String comment;
    private String date;

    public Rating() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    @Override
    public String toString() {
        return "Rating{" + "username='" + username + '\'' + ", rating=" + rating + '}';
    }
}
