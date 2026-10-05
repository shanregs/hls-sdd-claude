package com.hls.schoolbilling.internal;

import com.hls.teacher.api.TeacherDirectory.PlacementSpan;
import com.hls.teacher.api.TeacherPlacementSource;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers {@code teacher}'s "where is this Teacher?" questions from contract assignments (spec 012), and runs
 * the three assignment operations through {@link AssignmentService}. The service is injected lazily: it
 * needs the Teacher and School directories, which in turn use this source, so an eager reference would be a
 * circular bean dependency.
 */
@Service
class TeacherPlacementSourceImpl implements TeacherPlacementSource {

    private final ContractAssignmentRepository assignments;
    private final ContractPositionRepository positions;
    private final AssignmentService service;

    TeacherPlacementSourceImpl(
            ContractAssignmentRepository assignments,
            ContractPositionRepository positions,
            @Lazy AssignmentService service) {
        this.assignments = assignments;
        this.positions = positions;
        this.service = service;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlacementSpan> spansOverlapping(Collection<UUID> teacherIds, LocalDate from, LocalDate to) {
        if (teacherIds.isEmpty()) {
            return List.of();
        }
        return assignments.overlapping(teacherIds, from, to).stream()
                .map(a -> new PlacementSpan(a.getTeacherId(), a.getSchoolId(), a.getStartsOn(), a.getEndsOn()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> teachersAssignedDuring(LocalDate from, LocalDate to) {
        return new HashSet<>(assignments.teacherIdsAssignedBetween(from, to));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasCurrentOrFutureAssignment(UUID schoolId, LocalDate from) {
        return assignments.existsCurrentOrFuture(schoolId, from);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> teacherCountsBySchool(Collection<UUID> schoolIds, LocalDate on) {
        if (schoolIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : assignments.countBySchoolOn(schoolIds, on)) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> teacherIdsAtSchoolsOn(Collection<UUID> schoolIds, LocalDate on) {
        if (schoolIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(assignments.teacherIdsAtSchoolsOn(schoolIds, on));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Assignment> currentOf(Collection<UUID> teacherIds, LocalDate on) {
        if (teacherIds.isEmpty()) {
            return Map.of();
        }
        return toViews(assignments.inEffectOn(teacherIds, on)).stream()
                .collect(Collectors.toMap(Assignment::teacherId, a -> a, (a, b) -> a));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Assignment> pendingOf(Collection<UUID> teacherIds, LocalDate on) {
        if (teacherIds.isEmpty()) {
            return Map.of();
        }
        return toViews(assignments.scheduledAfter(teacherIds, on)).stream()
                .collect(Collectors.toMap(Assignment::teacherId, a -> a, (a, b) -> a));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Assignment> historyOf(UUID teacherId) {
        return toViews(assignments.findByTeacherIdOrderByStartsOnDesc(teacherId));
    }

    @Override
    public void assign(UUID actor, UUID teacherId, UUID schoolId, UUID positionId, LocalDate effectiveOn) {
        service.assign(actor, teacherId, schoolId, positionId, effectiveOn);
    }

    @Override
    public void cancelPending(UUID actor, UUID teacherId) {
        service.cancelPending(actor, teacherId);
    }

    @Override
    public void endForExit(UUID teacherId, LocalDate exitDate) {
        service.endForExit(teacherId, exitDate);
    }

    private List<Assignment> toViews(List<ContractAssignment> rows) {
        Set<UUID> positionIds = rows.stream()
                .map(ContractAssignment::getPositionId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Integer> numbers = positionIds.isEmpty()
                ? Map.of()
                : positions.findAllById(positionIds).stream()
                        .collect(Collectors.toMap(ContractPosition::getId, ContractPosition::getNumber));
        return rows.stream()
                .map(a -> new Assignment(
                        a.getId(),
                        a.getTeacherId(),
                        a.getSchoolId(),
                        a.getPositionId(),
                        a.getPositionId() == null ? null : numbers.get(a.getPositionId()),
                        a.getStartsOn(),
                        a.getEndsOn(),
                        a.getStatus().name()))
                .toList();
    }
}
