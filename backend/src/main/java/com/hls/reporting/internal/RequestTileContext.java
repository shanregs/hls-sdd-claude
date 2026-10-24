package com.hls.reporting.internal;

import com.hls.identity.user.Role;
import com.hls.reporting.api.TileContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** The context of one request; its memo lets the tiles of that request share a computation and is dropped afterwards. */
public final class RequestTileContext implements TileContext {

    private final UUID userId;
    private final Set<Role> roles;
    private final LocalDate today;
    private final YearMonth month;
    private final Optional<UUID> zoneId;
    private final Map<String, Object> memo = new ConcurrentHashMap<>();

    public RequestTileContext(UUID userId, Set<Role> roles, LocalDate today, YearMonth month, Optional<UUID> zoneId) {
        this.userId = userId;
        this.roles = Set.copyOf(roles);
        this.today = today;
        this.month = month;
        this.zoneId = zoneId;
    }

    @Override
    public UUID userId() {
        return userId;
    }

    @Override
    public Set<Role> roles() {
        return roles;
    }

    @Override
    public LocalDate today() {
        return today;
    }

    @Override
    public YearMonth month() {
        return month;
    }

    @Override
    public Optional<UUID> zoneId() {
        return zoneId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T memo(String key, Supplier<T> compute) {
        return (T) memo.computeIfAbsent(key, k -> compute.get());
    }
}
