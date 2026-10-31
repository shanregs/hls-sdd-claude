package com.hls.training.calendar.internal;

import com.hls.organization.api.ManagerQueries;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.training.calendar.internal.TrainingScope.Scope;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Finds people to pick for an individual assignment or a walk-in (spec 024 FR-009, FR-014). Only Teachers who are ACTIVE or
 * ON_LEAVE and active Managers are offered. Admin and Director reach everyone; a Manager only the Teachers placed under
 * them (never Managers, since a Manager may not record attendance for Managers); a Teacher nobody.
 */
@Service("trainingPeopleSearch")
public class PeopleSearch {

    /** One pickable person. */
    public record Found(String kind, UUID id, String name) {}

    static final int MAX = 20;
    private static final Set<String> OPEN_STATUS = Set.of("ACTIVE", "ON_LEAVE");

    private final TeacherDirectory teachers;
    private final ManagerQueries managers;
    private final TrainingScope scopes;

    PeopleSearch(TeacherDirectory teachers, ManagerQueries managers, TrainingScope scopes) {
        this.teachers = teachers;
        this.managers = managers;
        this.scopes = scopes;
    }

    /** {@code kind} is TEACHER, MANAGER or null for both; {@code query} matches part of the name, ignoring case. */
    public List<Found> search(Caller caller, String kind, String query) {
        Scope scope = scopes.of(caller);
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Found> out = new ArrayList<>();
        if (caller.isTeacher()) {
            return out;
        }
        if (kind == null || Assignment.TEACHER.equals(kind)) {
            for (var t : teachers.allTeachers()) {
                if (out.size() >= MAX) {
                    break;
                }
                boolean reachable = scope.orgWide() || scope.teacherIds().contains(t.id());
                if (reachable && OPEN_STATUS.contains(t.status()) && matches(t.name(), needle)) {
                    out.add(new Found(Assignment.TEACHER, t.id(), t.name()));
                }
            }
        }
        if (scope.orgWide() && (kind == null || Assignment.MANAGER.equals(kind))) {
            for (var m : managers.allManagers()) {
                if (out.size() >= MAX) {
                    break;
                }
                if (m.active() && matches(m.displayName(), needle)) {
                    out.add(new Found(Assignment.MANAGER, m.id(), m.displayName()));
                }
            }
        }
        return out;
    }

    private static boolean matches(String name, String needle) {
        return needle.isEmpty() || name != null && name.toLowerCase(Locale.ROOT).contains(needle);
    }
}
