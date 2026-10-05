package com.hls.recruitment.marketing.internal;

import com.hls.identity.user.Role;
import com.hls.organization.api.ScopeQueries;
import com.hls.organization.api.ScopeView;
import com.hls.school.api.NotFoundException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The Zone scope of marketing data (Constitution Principle III): Admin and Director see every prospect; a Zone Manager
 * only those of the Zones they manage, and anything outside is answered as not found.
 */
@Component
class MarketingScope {

    private final ScopeQueries scopeQueries;

    MarketingScope(ScopeQueries scopeQueries) {
        this.scopeQueries = scopeQueries;
    }

    ScopeView of(UUID userId, Set<Role> roles) {
        return scopeQueries.scopeOf(userId, roles);
    }

    boolean allowsZone(ScopeView scope, UUID zoneId) {
        return scope.orgWide() || scope.zoneIds().contains(zoneId);
    }

    void requireZone(ScopeView scope, UUID zoneId, String what) {
        if (!allowsZone(scope, zoneId)) {
            throw new NotFoundException(what + " not found.");
        }
    }
}
