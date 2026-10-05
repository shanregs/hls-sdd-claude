package com.hls.leave.internal;

import com.hls.identity.user.Role;
import com.hls.teacher.api.TeacherScopeQueries;
import com.hls.teacher.api.TeacherScopeQueries.TeacherScope;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Which Teachers' leave a supervisor may see and decide (spec 009 FR-006). The answer comes only from
 * the shared {@link TeacherScopeQueries} of spec 005; leave has no scoping rules of its own.
 */
@Component
public class LeaveScope {

    /** A query-friendly view of a {@link TeacherScope}: never an empty id list, which some drivers reject. */
    public record Scope(boolean orgWide, List<UUID> teacherIds) {

        public boolean allows(UUID teacherId) {
            return orgWide || teacherIds.contains(teacherId);
        }
    }

    private static final UUID NOBODY = new UUID(0L, 0L);

    private final TeacherScopeQueries scopes;

    public LeaveScope(TeacherScopeQueries scopes) {
        this.scopes = scopes;
    }

    public Scope of(UUID userId, Set<Role> roles) {
        TeacherScope scope = scopes.teacherScope(userId, roles);
        List<UUID> ids = scope.orgWide() || scope.teacherIds().isEmpty()
                ? List.of(NOBODY)
                : List.copyOf(scope.teacherIds());
        return new Scope(scope.orgWide(), ids);
    }
}
