package com.hls.training.calendar.internal;

import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import com.hls.teacher.api.TeacherPlacementSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Turns a group (School, Manager, Zone, all Teachers, all Managers, all) or a list of picked people into the people who
 * can be assigned, and says who is refused and why (spec 024 FR-011, FR-013). Everything is read through public interfaces
 * in bulk. A group only ever includes ACTIVE Teachers and active Managers; an individual pick may also be a Teacher on
 * leave, but IN_TRAINING and EXITED people are refused.
 */
@Component("trainingGroupResolver")
public class GroupResolver {

    public static final String SCHOOL = "SCHOOL";
    public static final String MANAGER = "MANAGER";
    public static final String ZONE = "ZONE";
    public static final String ALL_TEACHERS = "ALL_TEACHERS";
    public static final String ALL_MANAGERS = "ALL_MANAGERS";
    public static final String ALL = "ALL";

    /** Someone who cannot be assigned, with the reason shown to the Admin. */
    public record Refused(String kind, UUID id, String name, String reason) {}

    /** The outcome of resolving: people who can be assigned and people who cannot. */
    public record Resolved(Set<UUID> teacherIds, Set<UUID> managerIds, List<Refused> refused) {

        public int size() {
            return teacherIds.size() + managerIds.size();
        }
    }

    private final TeacherPlacementSource placements;
    private final SchoolDirectory schools;
    private final ManagerQueries managers;
    private final TeacherDirectory teachers;

    public GroupResolver(TeacherPlacementSource placements, SchoolDirectory schools, ManagerQueries managers, TeacherDirectory teachers) {
        this.placements = placements;
        this.schools = schools;
        this.managers = managers;
        this.teachers = teachers;
    }

    /** The people of a group on a date. */
    public Resolved group(String kind, UUID ref, LocalDate on) {
        if (kind == null) {
            throw new InvalidInputException("Choose a group.");
        }
        Set<UUID> teacherCandidates = new LinkedHashSet<>();
        Set<UUID> managerCandidates = new LinkedHashSet<>();
        switch (kind) {
            case SCHOOL -> {
                need(kind, ref);
                teacherCandidates.addAll(placements.teacherIdsAtSchoolsOn(List.of(ref), on));
            }
            case MANAGER -> {
                need(kind, ref);
                Set<UUID> schoolIds = managers.currentSchoolIds(List.of(ref)).getOrDefault(ref, Set.of());
                if (!schoolIds.isEmpty()) {
                    teacherCandidates.addAll(placements.teacherIdsAtSchoolsOn(schoolIds, on));
                }
            }
            case ZONE -> {
                need(kind, ref);
                List<UUID> schoolIds = schools.schoolsInZone(ref).stream().map(SchoolInfo::id).toList();
                if (!schoolIds.isEmpty()) {
                    teacherCandidates.addAll(placements.teacherIdsAtSchoolsOn(schoolIds, on));
                }
                managers.managersOfZone(ref).forEach(m -> managerCandidates.add(m.id()));
            }
            case ALL_TEACHERS -> teachers.activeTeachers().forEach(t -> teacherCandidates.add(t.id()));
            case ALL_MANAGERS -> managers.allManagers().forEach(m -> managerCandidates.add(m.id()));
            case ALL -> {
                teachers.activeTeachers().forEach(t -> teacherCandidates.add(t.id()));
                managers.allManagers().forEach(m -> managerCandidates.add(m.id()));
            }
            default -> throw new InvalidInputException("That group is not known.");
        }
        return filter(teacherCandidates, managerCandidates, false);
    }

    /** People picked one by one: ACTIVE and ON_LEAVE Teachers and active Managers pass; others are refused with a reason. */
    public Resolved individuals(String personKind, Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new InvalidInputException("Choose at least one person.");
        }
        if (Assignment.MANAGER.equals(personKind)) {
            return filter(Set.of(), new LinkedHashSet<>(ids), true);
        }
        if (!Assignment.TEACHER.equals(personKind)) {
            throw new InvalidInputException("The person must be a Teacher or a Manager.");
        }
        return filter(new LinkedHashSet<>(ids), Set.of(), true);
    }

    private Resolved filter(Set<UUID> teacherCandidates, Set<UUID> managerCandidates, boolean individual) {
        Set<UUID> teacherIds = new LinkedHashSet<>();
        Set<UUID> managerIds = new LinkedHashSet<>();
        List<Refused> refused = new ArrayList<>();
        if (!teacherCandidates.isEmpty()) {
            Map<UUID, TeacherInfo> info = teachers.teacherInfo(teacherCandidates);
            for (UUID id : teacherCandidates) {
                TeacherInfo t = info.get(id);
                if (t == null) {
                    refused.add(new Refused(Assignment.TEACHER, id, "Unknown", "This Teacher was not found."));
                } else if ("ACTIVE".equals(t.status()) || (individual && "ON_LEAVE".equals(t.status()))) {
                    teacherIds.add(id);
                } else if (individual || !"ON_LEAVE".equals(t.status())) {
                    refused.add(new Refused(Assignment.TEACHER, id, t.name(), reasonFor(t.status())));
                }
            }
        }
        if (!managerCandidates.isEmpty()) {
            Set<UUID> wanted = new HashSet<>(managerCandidates);
            Map<UUID, ManagerRef> refs = new java.util.HashMap<>();
            for (ManagerRef ref : managers.allManagers()) {
                if (wanted.contains(ref.id())) {
                    refs.put(ref.id(), ref);
                }
            }
            for (UUID id : managerCandidates) {
                ManagerRef m = refs.get(id);
                if (m == null) {
                    refused.add(new Refused(Assignment.MANAGER, id, "Unknown", "This Manager was not found."));
                } else if (m.active()) {
                    managerIds.add(id);
                } else {
                    refused.add(new Refused(Assignment.MANAGER, id, m.displayName(), "This Manager is no longer active."));
                }
            }
        }
        return new Resolved(teacherIds, managerIds, refused);
    }

    private static String reasonFor(String status) {
        return switch (status) {
            case "IN_TRAINING" -> "This Teacher is still in induction training.";
            case "EXITED" -> "This Teacher has left.";
            default -> "This Teacher cannot be assigned (" + status + ").";
        };
    }

    private static void need(String kind, UUID ref) {
        if (ref == null) {
            throw new InvalidInputException("Choose which " + kind.toLowerCase() + ".");
        }
    }
}
