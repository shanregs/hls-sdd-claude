package com.hls.attendance.workingdays.internal;

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
 * Which Schools a caller may see (spec 037 FR-025, Constitution Principle III): Admin and Director all of them, a
 * Manager who was granted View only the Schools assigned to them. An out-of-scope School is indistinguishable from a
 * missing one.
 */
@Component
public class WorkingDaysScope {

    private static final String NOT_FOUND = "School not found.";

    private final List<SchoolScopeProvider> providers;

    public WorkingDaysScope(List<SchoolScopeProvider> providers) {
        this.providers = providers;
    }

    /** The School ids the caller may see, or {@code null} when unrestricted. */
    public Set<UUID> visibleSchoolIds(UUID userId, Set<Role> roles) {
        if (CallerContext.isOrgWide(roles)) {
            return null;
        }
        Set<UUID> visible = new HashSet<>();
        for (SchoolScopeProvider provider : providers) {
            provider.visibleSchoolIds(userId, roles).ifPresent(visible::addAll);
        }
        return visible;
    }

    /** Throws the same not-found as a missing School when the caller may not see this one. */
    public void requireSchool(UUID userId, Set<Role> roles, UUID schoolId) {
        Set<UUID> visible = visibleSchoolIds(userId, roles);
        if (visible != null && !visible.contains(schoolId)) {
            throw new NotFoundException(NOT_FOUND);
        }
    }
}
