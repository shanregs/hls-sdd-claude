package com.hls.training.calendar.internal;

import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.school.api.NotFoundException;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import com.hls.teacher.api.TeacherScopeQueries;
import com.hls.teacher.api.TeacherScopeQueries.TeacherScope;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The one data scope of spec 024 (Constitution Principle III). Admin and Director reach everyone. A Manager reaches the
 * Teachers placed under them today (from {@link TeacherScopeQueries}, never re-implemented here) and their own Manager
 * record. A Teacher reaches only their own record. Anything out of scope answers "not found".
 */
@Component
public class TrainingScope {

    /** What a caller may reach. {@code teacherIds} is meaningful only when {@code orgWide} is false. */
    public record Scope(boolean orgWide, Set<UUID> teacherIds, UUID ownTeacherId, UUID ownManagerId) {

        public boolean allowsTeacher(UUID teacherId) {
            return orgWide || teacherIds.contains(teacherId) || teacherId.equals(ownTeacherId);
        }

        public boolean allowsManager(UUID managerId) {
            return orgWide || managerId.equals(ownManagerId);
        }

        public boolean allows(String personKind, UUID personId) {
            return Assignment.MANAGER.equals(personKind) ? allowsManager(personId) : allowsTeacher(personId);
        }

        /** Refuses with "not found" unless the person is in scope. */
        public void require(String personKind, UUID personId) {
            if (!allows(personKind, personId)) {
                throw new NotFoundException("Person not found.");
            }
        }
    }

    private final TeacherScopeQueries teacherScopes;
    private final TeacherDirectory teachers;
    private final ManagerQueries managers;

    public TrainingScope(TeacherScopeQueries teacherScopes, TeacherDirectory teachers, ManagerQueries managers) {
        this.teacherScopes = teacherScopes;
        this.teachers = teachers;
        this.managers = managers;
    }

    public Scope of(Caller caller) {
        if (caller.orgWide()) {
            return new Scope(true, Set.of(), null, null);
        }
        if (caller.isManager()) {
            TeacherScope reach = teacherScopes.teacherScope(caller.userId(), caller.roles());
            UUID own = managers.managerByUserId(caller.userId()).filter(ManagerRef::active).map(ManagerRef::id).orElse(null);
            return new Scope(false, reach.teacherIds(), null, own);
        }
        UUID teacher = teachers.teacherOfUser(caller.userId()).map(TeacherInfo::id).orElse(null);
        return new Scope(false, Set.of(), teacher, null);
    }
}
