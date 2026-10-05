package com.hls.attendance.internal;

import com.hls.cache.SnapshotCache;
import com.hls.cache.SnapshotCaches;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Every attendance status code, kept in memory. There are only a dozen or so, and they are read for every grid,
 * calendar, history and mark (to turn a code id into its letter, name and weight), so they are loaded once and
 * served from memory until {@link #changed()} is called by the code that edits them.
 *
 * <p>The returned {@link StatusCode}s are shared: read them, never change them. Edits go through
 * {@link StatusCodeService}, which loads its own copy from the database.
 */
@Component
public class StatusCodeCatalog {

    private record Snapshot(Map<UUID, StatusCode> byId, Map<String, StatusCode> byShortCode) {}

    private final SnapshotCache<Snapshot> cache;

    public StatusCodeCatalog(StatusCodeRepository repository, SnapshotCaches caches) {
        this.cache = caches.create("attendanceStatusCodes", () -> {
            Map<UUID, StatusCode> byId = new HashMap<>();
            Map<String, StatusCode> byShortCode = new HashMap<>();
            for (StatusCode code : repository.findAll()) {
                byId.put(code.getId(), code);
                byShortCode.put(code.getShortCode().toLowerCase(), code);
            }
            return new Snapshot(Map.copyOf(byId), Map.copyOf(byShortCode));
        });
    }

    /** All status codes by id. Read-only. */
    public Map<UUID, StatusCode> byId() {
        return cache.get().byId();
    }

    public Optional<StatusCode> find(UUID id) {
        return Optional.ofNullable(cache.get().byId().get(id));
    }

    /** The code with this short code, ignoring case. */
    public Optional<StatusCode> findByShortCode(String shortCode) {
        return Optional.ofNullable(cache.get().byShortCode().get(shortCode.toLowerCase()));
    }

    /** Call next to every change to a status code. */
    void changed() {
        cache.invalidateAround();
    }
}
