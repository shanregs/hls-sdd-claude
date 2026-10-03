package com.hls.organization.web;

import com.hls.organization.api.ScopeQueries;
import com.hls.organization.api.ScopeView;
import com.hls.school.api.CallerContext;
import com.hls.school.api.SchoolDirectory;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own scope summary for the dashboard (never another user's data). */
@RestController
public class ScopeController {

    private final ScopeQueries scopeQueries;
    private final SchoolDirectory schoolDirectory;

    public ScopeController(ScopeQueries scopeQueries, SchoolDirectory schoolDirectory) {
        this.scopeQueries = scopeQueries;
        this.schoolDirectory = schoolDirectory;
    }

    public record ZoneRef(UUID id, String name) {}

    public record ScopeSummary(boolean orgWide, int zoneCount, int schoolCount, List<ZoneRef> zones) {}

    @GetMapping("/api/v1/me/scope")
    public ScopeSummary mine(@AuthenticationPrincipal Jwt jwt) {
        ScopeView scope = scopeQueries.scopeOf(CallerContext.userId(jwt), CallerContext.roles(jwt));
        List<ZoneRef> zones = schoolDirectory.zones(scope.zoneIds()).stream()
                .map(z -> new ZoneRef(z.id(), z.name()))
                .sorted(Comparator.comparing(ZoneRef::name))
                .toList();
        return new ScopeSummary(scope.orgWide(), scope.zoneIds().size(), scope.schoolIds().size(), zones);
    }
}
