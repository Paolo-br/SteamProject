package org.steamproject.infra.kafka.streams;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;


public class StreamsRestService {
    public static void main(String[] args) throws Exception {
        org.apache.kafka.streams.KafkaStreams streams = UserLibraryStreams.startStreams();
        int attempts = 0;
        while (attempts < 10) {
            try {
                streams.store(StoreQueryParameters.fromNameAndType(UserLibraryStreams.STORE_NAME, QueryableStoreTypes.keyValueStore()));
                break;
            } catch (Exception ex) {
                attempts++;
                Thread.sleep(500);
            }
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api/players", new PlayerLibraryHandler(streams));
        server.setExecutor(null);
        server.start();
        System.out.println("Streams REST service listening on http://localhost:8080/api/players/{playerId}/library");
    }

    static class PlayerLibraryHandler implements HttpHandler {
        private final KafkaStreams streams;

        PlayerLibraryHandler(KafkaStreams streams) {
            this.streams = streams;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            if (parts.length >= 5 && "library".equals(parts[4])) {
                String playerId = parts[3];
                try {
                    ReadOnlyKeyValueStore<String, String> store = streams.store(
                            StoreQueryParameters.fromNameAndType(UserLibraryStreams.STORE_NAME, QueryableStoreTypes.keyValueStore())
                    );
                    String response = store.get(playerId);
                    if (response == null) response = "[]";
                    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                } catch (Exception e) {
                    byte[] bytes = "[]".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                }
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        }
    }
}
