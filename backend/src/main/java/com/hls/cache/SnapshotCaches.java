package com.hls.cache;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Creates the {@link SnapshotCache}s of reference data and keeps them by name, so a test or an operator
 * can see how often one loaded or clear them all.
 *
 * <p>{@code hls.cache.enabled} (default true) turns caching off everywhere; {@code hls.cache.ttl-seconds}
 * (default 60) is the longest a copy may be served without a reload.
 */
@Component
public class SnapshotCaches {

    private final Clock clock;
    private final boolean enabled;
    private final Duration timeToLive;
    private final Map<String, SnapshotCache<?>> byName = new ConcurrentHashMap<>();

    public SnapshotCaches(
            Clock clock,
            @Value("${hls.cache.enabled:true}") boolean enabled,
            @Value("${hls.cache.ttl-seconds:60}") long ttlSeconds) {
        this.clock = clock;
        this.enabled = enabled;
        this.timeToLive = Duration.ofSeconds(ttlSeconds);
    }

    /** A new cache called {@code name} that fills itself with {@code loader}. Names are unique. */
    public <T> SnapshotCache<T> create(String name, Supplier<T> loader) {
        SnapshotCache<T> cache = new SnapshotCache<>(name, loader, timeToLive, clock, enabled);
        if (byName.putIfAbsent(name, cache) != null) {
            throw new IllegalStateException("A cache called " + name + " already exists.");
        }
        return cache;
    }

    public boolean enabled() {
        return enabled;
    }

    /** The number of database loads of the named cache, or -1 when there is no such cache. */
    public long loads(String name) {
        SnapshotCache<?> cache = byName.get(name);
        return cache == null ? -1 : cache.loads();
    }

    /** Drops every cached copy. */
    public void invalidateAll() {
        byName.values().forEach(SnapshotCache::invalidate);
    }
}
