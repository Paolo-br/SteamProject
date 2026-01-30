package org.steamproject.service;

import org.steamproject.model.Rating;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RatingService {
    private final Map<String, List<Rating>> store = new HashMap<>();

    public List<Rating> getRatings(String gameId) {
        if (gameId == null) return Collections.emptyList();
        return Collections.unmodifiableList(store.getOrDefault(gameId, new ArrayList<>()));
    }

    public synchronized boolean addRating(String gameId, Rating rating) {
        if (gameId == null || rating == null) return false;
        List<Rating> list = store.computeIfAbsent(gameId, k -> new ArrayList<>());
        list.add(rating);
        return true;
    }
}
