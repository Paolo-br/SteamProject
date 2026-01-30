package org.steamproject.service;

import org.steamproject.ingestion.PublisherIngestion;
import org.steamproject.model.Publisher;

import java.util.Collections;
import java.util.List;


public class PublisherService {
    private final PublisherIngestion ingestion;
    private volatile List<Publisher> cache;

    public PublisherService(String resourcePath) {
        this.ingestion = new PublisherIngestion(resourcePath);
    }

    public PublisherService() {
        this("/data/vgsales.csv");
    }

    public List<Publisher> getAllPublishers() {
        if (cache == null) {
            synchronized (this) {
                if (cache == null) {
                    cache = Collections.unmodifiableList(ingestion.readAll());
                }
            }
        }
        return cache;
    }

    public Publisher getPublisherById(String id) {
        return getAllPublishers().stream().filter(p -> p.getId() != null && p.getId().equals(id)).findFirst().orElse(null);
    }
}
