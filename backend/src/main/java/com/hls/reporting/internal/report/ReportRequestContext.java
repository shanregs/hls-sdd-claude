package com.hls.reporting.internal.report;

import com.hls.identity.user.Role;
import com.hls.reporting.api.ReportContext;
import com.hls.reporting.api.ReportRequest;
import com.hls.reporting.api.ReportScope;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** The context of one report request; its memo lets the sections share a computation and is dropped afterwards. */
final class ReportRequestContext implements ReportContext {

    private final UUID userId;
    private final Set<Role> roles;
    private final LocalDate today;
    private final ReportScope scope;
    private final ReportRequest request;
    private final Map<String, Object> memo = new ConcurrentHashMap<>();

    ReportRequestContext(UUID userId, Set<Role> roles, LocalDate today, ReportScope scope, ReportRequest request) {
        this.userId = userId;
        this.roles = Set.copyOf(roles);
        this.today = today;
        this.scope = scope;
        this.request = request;
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
    public ReportScope scope() {
        return scope;
    }

    @Override
    public ReportRequest request() {
        return request;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T memo(String key, Supplier<T> compute) {
        return (T) memo.computeIfAbsent(key, k -> compute.get());
    }
}
