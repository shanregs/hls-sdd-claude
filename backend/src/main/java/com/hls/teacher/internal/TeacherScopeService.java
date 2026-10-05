package com.hls.teacher.internal;

import com.hls.identity.user.Role;
import com.hls.organization.api.ScopeQueries;
import com.hls.organization.api.ScopeView;
import com.hls.school.api.CallerContext;
import com.hls.teacher.api.TeacherPlacementSource;
import com.hls.teacher.api.TeacherScopeQueries;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teacher-level scope on top of {@link ScopeQueries}: a Manager sees the Teachers placed today at
 * their Schools; a Teacher only their own record; Admin/Director everyone (including unplaced).
 * Derived on every call, so a changed assignment or placement applies on the next request.
 */
@Service
@Transactional(readOnly = true)
public class TeacherScopeService implements TeacherScopeQueries {

    private final ScopeQueries scopeQueries;
    private final TeacherPlacementSource placementSource;
    private final TeacherRepository teacherRepository;
    private final Clock clock;

    public TeacherScopeService(
            ScopeQueries scopeQueries,
            TeacherPlacementSource placementSource,
            TeacherRepository teacherRepository,
            Clock clock) {
        this.scopeQueries = scopeQueries;
        this.placementSource = placementSource;
        this.teacherRepository = teacherRepository;
        this.clock = clock;
    }

    @Override
    public TeacherScope teacherScope(UUID userId, Set<Role> roles) {
        if (CallerContext.isOrgWide(roles)) {
            return new TeacherScope(true, Set.of());
        }
        Set<UUID> ids = new HashSet<>();
        if (roles.contains(Role.MANAGER)) {
            ScopeView scope = scopeQueries.scopeOf(userId, roles);
            if (!scope.schoolIds().isEmpty()) {
                ids.addAll(placementSource.teacherIdsAtSchoolsOn(scope.schoolIds(), LocalDate.now(clock)));
            }
        }
        if (roles.contains(Role.TEACHER)) {
            teacherRepository.findByUserId(userId).ifPresent(t -> ids.add(t.getId()));
        }
        return new TeacherScope(false, ids);
    }
}
