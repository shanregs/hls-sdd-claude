package com.hls.performance.internal;

import com.hls.school.api.NotFoundException;
import com.hls.teacher.api.TeacherScopeQueries;
import com.hls.teacher.api.TeacherScopeQueries.TeacherScope;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The data scope of spec 026 (Constitution Principle III): Admin and Director reach every Teacher; a Manager reaches the
 * Teachers placed today at their Schools, and the records they wrote themselves. Anything else is "not found".
 */
@Component
class ScopeGuard {

    private final TeacherScopeQueries scopes;

    ScopeGuard(TeacherScopeQueries scopes) {
        this.scopes = scopes;
    }

    TeacherScope scope(Caller caller) {
        return scopes.teacherScope(caller.userId(), caller.roles());
    }

    /** True if the Teacher is in scope, or the caller wrote the record ({@code authorId}). */
    boolean canSee(Caller caller, UUID teacherId, UUID authorId) {
        if (caller.orgWide()) {
            return true;
        }
        if (authorId != null && authorId.equals(caller.userId())) {
            return true;
        }
        return scope(caller).allows(teacherId);
    }

    /** Refuses with "not found" unless the Teacher is in scope. */
    void requireTeacher(Caller caller, UUID teacherId) {
        if (!scope(caller).allows(teacherId)) {
            throw new NotFoundException("Teacher not found.");
        }
    }

    /** Refuses with "not found" unless the record's Teacher is in scope or the caller wrote it. */
    void requireRecord(Caller caller, UUID teacherId, UUID authorId, String what) {
        if (!canSee(caller, teacherId, authorId)) {
            throw new NotFoundException(what + " not found.");
        }
    }
}
