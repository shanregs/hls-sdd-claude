package com.hls.leave.internal;

import com.hls.cache.SnapshotCache;
import com.hls.cache.SnapshotCaches;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The leave types (Casual, Sick, Personal, Other), kept in memory: a handful of rows that are read for every leave
 * list and every application. Nothing edits them yet; when an editor is added it must call {@link #changed()}.
 *
 * <p>The returned {@link LeaveType}s are shared: read them, never change them.
 */
@Component
public class LeaveTypeCatalog {

    private record Snapshot(List<LeaveType> all, Map<UUID, LeaveType> byId) {}

    private final SnapshotCache<Snapshot> cache;

    public LeaveTypeCatalog(LeaveTypeRepository repository, SnapshotCaches caches) {
        this.cache = caches.create("leaveTypes", () -> {
            List<LeaveType> all = repository.findAll().stream()
                    .sorted(Comparator.comparingInt(LeaveType::getSortOrder))
                    .toList();
            return new Snapshot(all, all.stream().collect(Collectors.toUnmodifiableMap(LeaveType::getId, Function.identity())));
        });
    }

    /** Every leave type, in display order. */
    public List<LeaveType> all() {
        return cache.get().all();
    }

    /** The leave types that can be chosen now, in display order. */
    public List<LeaveType> active() {
        return all().stream().filter(LeaveType::isActive).toList();
    }

    public Optional<LeaveType> find(UUID id) {
        return Optional.ofNullable(cache.get().byId().get(id));
    }

    /** Leave type names by id. */
    public Map<UUID, String> namesById() {
        return cache.get().byId().values().stream()
                .collect(Collectors.toUnmodifiableMap(LeaveType::getId, LeaveType::getName));
    }

    /** Call next to every change to a leave type. */
    void changed() {
        cache.invalidateAround();
    }
}
