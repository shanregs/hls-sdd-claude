package com.hls.substitution.internal;

import com.hls.identity.user.Role;
import com.hls.school.api.CallerContext;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.SchoolScopeProvider;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Which requirements a caller may see (spec 017a FR-013): Admin and Director all, a Zone Manager those of the Schools
 * they manage, nobody else. Fails closed; an out-of-scope id reads as not found.
 */
@Component
public class SubstitutionScope {

    private static final UUID NOBODY = new UUID(0L, 0L);

    /** {@code schoolIds} is never empty, because some drivers reject an empty IN list. */
    public record Scope(boolean all, Set<UUID> schoolIds) {

        public boolean allows(UUID schoolId) {
            return all || schoolIds.contains(schoolId);
        }
    }

    private final List<SchoolScopeProvider> providers;

    public SubstitutionScope(List<SchoolScopeProvider> providers) {
        this.providers = providers;
    }

    public Scope of(UUID userId, Set<Role> roles) {
        if (CallerContext.isOrgWide(roles)) {
            return new Scope(true, Set.of(NOBODY));
        }
        Set<UUID> visible = new HashSet<>();
        for (SchoolScopeProvider provider : providers) {
            provider.visibleSchoolIds(userId, roles).ifPresent(visible::addAll);
        }
        if (visible.isEmpty()) {
            visible.add(NOBODY);
        }
        return new Scope(false, visible);
    }

    public void require(UUID userId, Set<Role> roles, UUID schoolId) {
        if (!of(userId, roles).allows(schoolId)) {
            throw new NotFoundException("Substitution requirement not found.");
        }
    }
}
