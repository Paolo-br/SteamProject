package org.steamproject.ingestion;

import org.steamproject.model.Game;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Ingestion qui lit un CSV et retourne des objets
 * métiers `Game` sans aucune dépendance à Kafka / Avro / events.
 */
public class GameIngestion {
    private final String resourcePath;

    public GameIngestion(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public List<Game> readAll() throws IOException {
        List<Game> out = new ArrayList<>();

        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) throw new FileNotFoundException("Resource not found: " + resourcePath);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                boolean first = true;
                while ((line = br.readLine()) != null) {
                    if (first) { first = false; continue; } // skip header
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    String[] cols = splitCsv(line);
                    Game g = mapToGame(cols);
                    out.add(g);
                }
            }
        }

        return out;
    }

    private Game mapToGame(String[] cols) {
        Game g = new Game();
        g.setName(safe(cols, 0));
        g.setPlatform(safe(cols, 1));
        g.setYear(parseInt(safe(cols, 2), null));
        g.setGenre(safe(cols, 3));
        g.setPublisher(safe(cols, 4));
        g.setNaSales(parseDoubleSafe(safe(cols, 5)));
        g.setEuSales(parseDoubleSafe(safe(cols, 6)));
        g.setJpSales(parseDoubleSafe(safe(cols, 7)));
        g.setOtherSales(parseDoubleSafe(safe(cols, 8)));
        g.setGlobalSales(parseDoubleSafe(safe(cols, 9)));
        return g;
    }

    private static String safe(String[] cols, int idx) {
        if (cols == null || idx < 0 || idx >= cols.length) return "";
        String v = cols[idx];
        if (v == null) return "";
        v = v.trim();
        if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
            v = v.substring(1, v.length()-1);
        }
        return v;
    }

    private static Integer parseInt(String s, Integer def) {
        if (s == null || s.isEmpty()) return def;
        try { return (int) Double.parseDouble(s); } catch (NumberFormatException ex) { return def; }
    }

    private static Double parseDoubleSafe(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException ex) { return null; }
    }

    private static String[] splitCsv(String line) {
        return line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
    }
}
