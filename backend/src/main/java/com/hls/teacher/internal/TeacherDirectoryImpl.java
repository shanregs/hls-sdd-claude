package com.hls.teacher.internal;

import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherPlacementSource;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class TeacherDirectoryImpl implements TeacherDirectory {

    private final TeacherRepository teachers;
    private final TeacherPlacementSource placements;

    TeacherDirectoryImpl(TeacherRepository teachers, TeacherPlacementSource placements) {
        this.teachers = teachers;
        this.placements = placements;
    }

    @Override
    public Map<UUID, TeacherInfo> teacherInfo(Collection<UUID> teacherIds) {
        if (teacherIds.isEmpty()) {
            return Map.of();
        }
        return teachers.findAllById(teacherIds).stream()
                .collect(Collectors.toMap(Teacher::getId, TeacherDirectoryImpl::toInfo, (a, b) -> a));
    }

    @Override
    public Optional<TeacherInfo> teacherOfUser(UUID userId) {
        return teachers.findByUserId(userId).map(TeacherDirectoryImpl::toInfo);
    }

    @Override
    public List<PlacementSpan> placementsOverlapping(
            Collection<UUID> teacherIds, LocalDate from, LocalDate to) {
        if (teacherIds.isEmpty()) {
            return List.of();
        }
        return placements.spansOverlapping(teacherIds, from, to);
    }

    @Override
    public Set<UUID> teachersPlacedDuring(LocalDate from, LocalDate to) {
        return new HashSet<>(placements.teachersAssignedDuring(from, to));
    }

    private static TeacherInfo toInfo(Teacher t) {
        return new TeacherInfo(t.getId(), t.getName(), t.getStatus().name(), t.getUserId());
    }
}
