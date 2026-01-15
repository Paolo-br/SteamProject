package org.steamproject.service;

import org.steamproject.model.PriceUpdate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PriceService {
    private final Map<String, Double> current = new HashMap<>();
    private final Map<String, List<PriceUpdate>> history = new HashMap<>();

    public synchronized void applyPriceUpdate(PriceUpdate upd) {
        if (upd == null || upd.getGameId() == null) return;
        current.put(upd.getGameId(), upd.getNewPrice());
        List<PriceUpdate> list = history.computeIfAbsent(upd.getGameId(), k -> new ArrayList<>());
        list.add(upd);
    }

    public Double getPrice(String gameId) {
        if (gameId == null) return null;
        return current.get(gameId);
    }

    public List<PriceUpdate> getPriceHistory(String gameId) {
        if (gameId == null) return Collections.emptyList();
        return Collections.unmodifiableList(history.getOrDefault(gameId, new ArrayList<>()));
    }
}
