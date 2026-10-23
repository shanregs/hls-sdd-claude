package com.hls.transfer.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.school.api.SchoolDirectory;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import com.hls.teacher.api.TeacherPlacementSource;
import com.hls.teacher.api.TeacherPlacementSource.Assignment;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Names and placements read live from the owning modules (nothing is copied into the movement tables). */
@Component
class MovementFacts {

    private final TeacherDirectory teachers;
    private final TeacherPlacementSource placements;
    private final SchoolDirectory schools;
    private final AppUserRepository users;
    private final BusinessDate businessDate;

    MovementFacts(
            TeacherDirectory teachers,
            TeacherPlacementSource placements,
            SchoolDirectory schools,
            AppUserRepository users,
            BusinessDate businessDate) {
        this.teachers = teachers;
        this.placements = placements;
        this.schools = schools;
        this.users = users;
        this.businessDate = businessDate;
    }

    LocalDate today() {
        return businessDate.today();
    }

    Optional<TeacherInfo> teacher(UUID id) {
        return Optional.ofNullable(teachers.teacherInfo(List.of(id)).get(id));
    }

    Map<UUID, String> teacherNames(Collection<UUID> ids) {
        Map<UUID, String> names = new HashMap<>();
        teachers.teacherInfo(ids).forEach((id, info) -> names.put(id, info.name()));
        return names;
    }

    Map<UUID, String> schoolNames(Collection<UUID> ids) {
        Map<UUID, String> names = new HashMap<>();
        schools.schools(ids).forEach(s -> names.put(s.id(), s.name()));
        return names;
    }

    String userName(UUID userId) {
        if (userId == null) {
            return null;
        }
        return users.findById(userId).map(AppUser::getDisplayName).orElse(null);
    }

    /** The ACTIVE placement in effect on the date, if any. */
    Optional<Assignment> currentPlacement(UUID teacherId, LocalDate on) {
        return Optional.ofNullable(placements.currentOf(List.of(teacherId), on).get(teacherId));
    }

    List<Assignment> placementHistory(UUID teacherId) {
        return placements.historyOf(teacherId);
    }
}
