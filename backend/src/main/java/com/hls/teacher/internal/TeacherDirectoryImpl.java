package com.hls.teacher.internal;

import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherPlacementSource;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
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
    private final SalaryHistoryRepository salaries;

    TeacherDirectoryImpl(
            TeacherRepository teachers, TeacherPlacementSource placements, SalaryHistoryRepository salaries) {
        this.teachers = teachers;
        this.placements = placements;
        this.salaries = salaries;
    }

    @Override
    public Optional<SalaryOn> salaryOn(UUID teacherId, LocalDate date) {
        return salaries
                .findFirstByTeacherIdAndEffectiveOnLessThanEqualOrderByEffectiveOnDescCreatedAtDesc(teacherId, date)
                .map(e -> new SalaryOn(e.getAmount(), e.getEffectiveOn()));
    }

    @Override
    public Map<UUID, SalaryOn> salariesOn(Collection<UUID> teacherIds, LocalDate date) {
        if (teacherIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, SalaryHistoryEntry> latest = new HashMap<>();
        for (SalaryHistoryEntry entry : salaries.findInEffectCandidates(teacherIds, date)) {
            latest.merge(entry.getTeacherId(), entry, (a, b) -> newer(a, b) ? a : b);
        }
        Map<UUID, SalaryOn> result = new HashMap<>();
        latest.forEach((id, entry) -> result.put(id, new SalaryOn(entry.getAmount(), entry.getEffectiveOn())));
        return result;
    }

    /** Later effective date wins; the same date goes to the entry recorded last. */
    private static boolean newer(SalaryHistoryEntry a, SalaryHistoryEntry b) {
        int byDate = a.getEffectiveOn().compareTo(b.getEffectiveOn());
        return byDate != 0 ? byDate > 0 : a.getCreatedAt().compareTo(b.getCreatedAt()) >= 0;
    }

    @Override
    public Map<UUID, List<UUID>> teachersByDesignation(Collection<UUID> designationIds) {
        if (designationIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<UUID>> result = new HashMap<>();
        for (Object[] row : teachers.idsByDesignation(designationIds)) {
            result.computeIfAbsent((UUID) row[0], k -> new java.util.ArrayList<>()).add((UUID) row[1]);
        }
        return result;
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
    public List<TeacherInfo> allTeachers() {
        return teachers.findAll().stream()
                .map(TeacherDirectoryImpl::toInfo)
                .sorted(java.util.Comparator.comparing(t -> t.name().toLowerCase(java.util.Locale.ROOT)))
                .toList();
    }

    @Override
    public List<TeacherInfo> activeTeachers() {
        List<UUID> ids = teachers.findIdsByStatus(TeacherStatus.ACTIVE);
        return ids.isEmpty() ? List.of() : teachers.findAllById(ids).stream().map(TeacherDirectoryImpl::toInfo).toList();
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

    @Override
    public Optional<UUID> currentDesignation(UUID teacherId) {
        return teachers.findById(teacherId).map(Teacher::getDesignationId);
    }

    @Override
    public Map<UUID, UUID> currentDesignations(Collection<UUID> teacherIds) {
        Map<UUID, UUID> result = new HashMap<>();
        if (!teacherIds.isEmpty()) {
            teachers.findAllById(teacherIds).forEach(t -> {
                if (t.getDesignationId() != null) {
                    result.put(t.getId(), t.getDesignationId());
                }
            });
        }
        return result;
    }

    @Override
    public Map<UUID, TeacherEmployment> employment(Collection<UUID> teacherIds) {
        if (teacherIds.isEmpty()) {
            return Map.of();
        }
        return teachers.findAllById(teacherIds).stream()
                .collect(Collectors.toMap(
                        Teacher::getId,
                        t -> new TeacherEmployment(t.getId(), t.getDesignationId(), t.getEmployeeId())));
    }

    @Override
    public Map<UUID, Long> holderCountsByDesignation(Collection<UUID> designationIds) {
        Map<UUID, Long> result = new HashMap<>();
        for (Object[] row : teachers.countByDesignation()) {
            UUID id = (UUID) row[0];
            if (designationIds == null || designationIds.isEmpty() || designationIds.contains(id)) {
                result.put(id, (Long) row[1]);
            }
        }
        return result;
    }

    private static TeacherInfo toInfo(Teacher t) {
        return new TeacherInfo(t.getId(), t.getName(), t.getStatus().name(), t.getUserId());
    }
}
