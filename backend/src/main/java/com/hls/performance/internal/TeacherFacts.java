package com.hls.performance.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import com.hls.teacher.api.TeacherPlacementSource;
import com.hls.teacher.api.TeacherPlacementSource.Assignment;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * What the performance module needs to know about a Teacher today: name, status, current School and Zone, and the School's
 * Manager. Always read live from the owning modules; the Manager of a Teacher is never stored here.
 */
@Component
class TeacherFacts {

    /** A Teacher as of today; school and manager fields are null while the Teacher is not placed or the School has no Manager. */
    record TeacherContext(
            UUID teacherId,
            String name,
            String status,
            UUID schoolId,
            String schoolName,
            UUID zoneId,
            UUID managerUserId,
            String managerName) {

        boolean active() {
            return "ACTIVE".equals(status);
        }

        boolean placed() {
            return schoolId != null;
        }
    }

    private final TeacherDirectory teachers;
    private final TeacherPlacementSource placements;
    private final SchoolDirectory schools;
    private final ManagerQueries managers;
    private final AppUserRepository users;
    private final BusinessDate businessDate;

    TeacherFacts(
            TeacherDirectory teachers,
            TeacherPlacementSource placements,
            SchoolDirectory schools,
            ManagerQueries managers,
            AppUserRepository users,
            BusinessDate businessDate) {
        this.teachers = teachers;
        this.placements = placements;
        this.schools = schools;
        this.managers = managers;
        this.users = users;
        this.businessDate = businessDate;
    }

    /** The Teacher today, or not found. */
    TeacherContext require(UUID teacherId) {
        return find(teacherId).orElseThrow(() -> new NotFoundException("Teacher not found."));
    }

    Optional<TeacherContext> find(UUID teacherId) {
        TeacherInfo info = teachers.teacherInfo(java.util.List.of(teacherId)).get(teacherId);
        if (info == null) {
            return Optional.empty();
        }
        Assignment placement = placements.currentOf(java.util.List.of(teacherId), businessDate.today()).get(teacherId);
        if (placement == null) {
            return Optional.of(new TeacherContext(teacherId, info.name(), info.status(), null, null, null, null, null));
        }
        SchoolInfo school = schools.school(placement.schoolId()).orElse(null);
        ManagerRef manager = managers.managerOfSchool(placement.schoolId()).filter(ManagerRef::active).orElse(null);
        return Optional.of(new TeacherContext(
                teacherId,
                info.name(),
                info.status(),
                placement.schoolId(),
                school == null ? null : school.name(),
                school == null ? null : school.zoneId(),
                manager == null ? null : manager.userId(),
                manager == null ? null : manager.displayName()));
    }

    Map<UUID, String> teacherNames(Collection<UUID> teacherIds) {
        Map<UUID, String> names = new HashMap<>();
        teachers.teacherInfo(teacherIds).forEach((id, info) -> names.put(id, info.name()));
        return names;
    }

    Map<UUID, String> schoolNames(Collection<UUID> schoolIds) {
        Map<UUID, String> names = new HashMap<>();
        schools.schools(schoolIds).forEach(s -> names.put(s.id(), s.name()));
        return names;
    }

    Map<UUID, UUID> schoolZones(Collection<UUID> schoolIds) {
        Map<UUID, UUID> zones = new HashMap<>();
        schools.schools(schoolIds).forEach(s -> zones.put(s.id(), s.zoneId()));
        return zones;
    }

    String userName(UUID userId) {
        if (userId == null) {
            return null;
        }
        return users.findById(userId).map(AppUser::getDisplayName).orElse(null);
    }

    Map<UUID, String> userNames(Collection<UUID> userIds) {
        Map<UUID, String> names = new HashMap<>();
        for (UUID id : userIds) {
            if (id != null) {
                names.put(id, userName(id));
            }
        }
        return names;
    }
}
