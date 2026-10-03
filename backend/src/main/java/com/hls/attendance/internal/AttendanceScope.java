package com.hls.attendance.internal;

import com.hls.identity.user.Role;
import com.hls.school.api.NotFoundException;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherScopeQueries;
import com.hls.teacher.api.TeacherScopeQueries.TeacherScope;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Which Teachers a caller may see in attendance. All scoping comes from the shared
 * {@link TeacherScopeQueries} (spec 008 FR-004, Constitution Principle III); this class only combines
 * it with "placed during the month". An out-of-scope Teacher is indistinguishable from a missing one.
 */
@Component
public class AttendanceScope {

    private static final String NOT_FOUND = "Teacher not found.";

    private final TeacherScopeQueries teacherScope;
    private final TeacherDirectory teachers;

    public AttendanceScope(TeacherScopeQueries teacherScope, TeacherDirectory teachers) {
        this.teacherScope = teacherScope;
        this.teachers = teachers;
    }

    /** The Teachers placed during the month that the caller may see. */
    public Set<UUID> allowedDuring(UUID userId, Set<Role> roles, YearMonth month) {
        Set<UUID> placed = teachers.teachersPlacedDuring(month.atDay(1), month.atEndOfMonth());
        TeacherScope scope = teacherScope.teacherScope(userId, roles);
        if (scope.orgWide()) {
            return placed;
        }
        Set<UUID> allowed = new HashSet<>(placed);
        allowed.retainAll(scope.teacherIds());
        return allowed;
    }

    /** Throws the same not-found as a missing Teacher when the caller may not see this one. */
    public void requireInScope(UUID userId, Set<Role> roles, UUID teacherId) {
        TeacherScope scope = teacherScope.teacherScope(userId, roles);
        if (!scope.allows(teacherId) || teachers.teacherInfo(List.of(teacherId)).isEmpty()) {
            throw new NotFoundException(NOT_FOUND);
        }
    }
}
