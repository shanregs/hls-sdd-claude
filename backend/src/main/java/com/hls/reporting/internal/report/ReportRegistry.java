package com.hls.reporting.internal.report;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.user.Role;
import com.hls.reporting.api.ReportSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Finds every {@link ReportSource} bean by key and lists the ones a caller may open, from the caller's grants. */
@Component
public class ReportRegistry {

    private final Map<String, ReportSource> byKey = new LinkedHashMap<>();
    private final PermissionGuard guard;

    public ReportRegistry(List<ReportSource> sources, PermissionGuard guard) {
        this.guard = guard;
        sources.stream()
                .sorted(java.util.Comparator.comparing(s -> s.definition().key()))
                .forEach(source -> {
                    String key = source.definition().key();
                    if (byKey.putIfAbsent(key, source) != null) {
                        throw new IllegalStateException("Two reports share the key " + key);
                    }
                });
    }

    public Optional<ReportSource> find(String key) {
        return Optional.ofNullable(byKey.get(key));
    }

    /** The reports whose View grant the caller holds, in key order. Teacher and System get only what they were granted. */
    public List<ReportSource> menuFor(Set<Role> roles) {
        return byKey.values().stream()
                .filter(s -> guard.isGranted(roles, s.definition().module(), PermissionAction.VIEW))
                .toList();
    }
}
