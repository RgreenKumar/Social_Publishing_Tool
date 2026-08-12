package com.relay.api.media;

import com.relay.api.media.TempMediaStore.StoredMedia;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final TempMediaStore store;

    public MediaController(TempMediaStore store) {
        this.store = store;
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> get(@PathVariable String id) {
        StoredMedia media = store.get(id);
        if (media == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(media.contentType()));
        headers.setContentLength(media.bytes().length);
        headers.set(HttpHeaders.CACHE_CONTROL, "public, max-age=300");
        return new ResponseEntity<>(media.bytes(), headers, HttpStatus.OK);
    }
}
