package com.relay.api.media;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class TempMediaStore {

    public record StoredMedia(byte[] bytes, String contentType, String filename, Instant createdAt) {}

    private final Map<String, StoredMedia> byId = new ConcurrentHashMap<>();

    public String put(byte[] bytes, String contentType, String filename) {
        String id = UUID.randomUUID().toString().replace("-", "");
        byId.put(
            id,
            new StoredMedia(
                bytes,
                contentType == null || contentType.isBlank() ? "image/jpeg" : contentType,
                filename == null || filename.isBlank() ? "image.jpg" : filename,
                Instant.now()
            )
        );
        return id;
    }

    public StoredMedia get(String id) {
        return byId.get(id);
    }

    public void remove(String id) {
        byId.remove(id);
    }
}
