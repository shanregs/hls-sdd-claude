package com.hls.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * An in-memory copy of one small, read-mostly table (reference data such as the permission matrix),
 * loaded whole on first use and served from memory until it is invalidated or its time limit passes.
 *
 * <p>Rules that keep it correct:
 *
 * <ul>
 *   <li>The cached value must be immutable or treated as read-only by every caller.
 *   <li>A writer calls {@link #invalidateAround()} next to its write. That drops the copy at once and again when
 *       the writer's transaction ends, whether it committed or rolled back, so a reader that loaded the data
 *       in between never keeps an uncommitted or rolled-back view.
 *   <li>A load that raced with an invalidation is returned to its caller but not stored.
 *   <li>The time limit is a safety net for changes made by another application instance or by hand in the
 *       database; in-process writes invalidate straight away.
 * </ul>
 *
 * <p>When disabled (tests, or {@code hls.cache.enabled=false}) every call loads from the database.
 */
public final class SnapshotCache<T> {

    private record Snapshot<V>(V value, Instant loadedAt) {}

    private final String name;
    private final Supplier<T> loader;
    private final Duration timeToLive;
    private final Clock clock;
    private final boolean enabled;

    private final Object loadLock = new Object();
    private final AtomicLong generation = new AtomicLong();
    private final AtomicLong loads = new AtomicLong();
    private volatile Snapshot<T> snapshot;

    SnapshotCache(String name, Supplier<T> loader, Duration timeToLive, Clock clock, boolean enabled) {
        this.name = Objects.requireNonNull(name);
        this.loader = Objects.requireNonNull(loader);
        this.timeToLive = Objects.requireNonNull(timeToLive);
        this.clock = Objects.requireNonNull(clock);
        this.enabled = enabled;
    }

    /** The cached value, loading it first when there is none or it is older than the time limit. */
    public T get() {
        if (!enabled) {
            loads.incrementAndGet();
            return loader.get();
        }
        Snapshot<T> current = snapshot;
        if (isFresh(current)) {
            return current.value();
        }
        synchronized (loadLock) {
            current = snapshot;
            if (isFresh(current)) {
                return current.value();
            }
            long before = generation.get();
            T value = loader.get();
            loads.incrementAndGet();
            if (generation.get() == before) {
                snapshot = new Snapshot<>(value, clock.instant());
            }
            return value;
        }
    }

    /** Drops the cached copy; the next {@link #get()} loads again. */
    public void invalidate() {
        generation.incrementAndGet();
        snapshot = null;
    }

    /**
     * Call next to a write. Drops the copy now and once more when the surrounding transaction completes (commit or
     * rollback); outside a transaction it just drops it now.
     */
    public void invalidateAround() {
        invalidate();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    invalidate();
                }
            });
        }
    }

    public String name() {
        return name;
    }

    /** How many times the data was loaded from the database (for tests and diagnostics). */
    public long loads() {
        return loads.get();
    }

    private boolean isFresh(Snapshot<T> current) {
        return current != null && Duration.between(current.loadedAt(), clock.instant()).compareTo(timeToLive) < 0;
    }
}
