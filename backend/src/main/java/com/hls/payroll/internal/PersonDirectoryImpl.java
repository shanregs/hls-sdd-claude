package com.hls.payroll.internal;

import com.hls.designation.api.DesignationDirectory;
import com.hls.designation.api.DesignationDirectory.DesignationInfo;
import com.hls.designation.api.DesignationDirectory.Kind;
import com.hls.organization.api.ManagerQueries;
import com.hls.teacher.api.TeacherDirectory;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The port over the designation (005a), organization (Manager) and teacher modules' public interfaces. */
@Service
@Transactional(readOnly = true)
class PersonDirectoryImpl implements PersonDirectory {

    private final DesignationDirectory designations;
    private final ManagerQueries managers;
    private final TeacherDirectory teachers;

    PersonDirectoryImpl(DesignationDirectory designations, ManagerQueries managers, TeacherDirectory teachers) {
        this.designations = designations;
        this.managers = managers;
        this.teachers = teachers;
    }

    @Override
    public List<DesignationRef> designations(Kind kind, boolean includeRetired) {
        return designations.designations(kind, includeRetired).stream()
                .map(PersonDirectoryImpl::ref)
                .toList();
    }

    @Override
    public Optional<DesignationRef> designation(UUID designationId) {
        return designations.find(designationId).map(PersonDirectoryImpl::ref);
    }

    @Override
    public Map<UUID, UUID> managerDesignationsOn(Collection<UUID> managerIds, LocalDate date) {
        return managerIds.isEmpty() ? Map.of() : managers.designationsOn(managerIds, date);
    }

    @Override
    public Map<UUID, ManagerDates> managers(Collection<UUID> managerIds) {
        Map<UUID, ManagerDates> result = new HashMap<>();
        if (!managerIds.isEmpty()) {
            managers.employment(managerIds)
                    .forEach((id, e) -> result.put(id, new ManagerDates(e.joiningDate(), e.exitDate())));
        }
        return result;
    }

    @Override
    public Map<UUID, Long> managerHolders(Collection<UUID> designationIds) {
        return designationIds.isEmpty() ? Map.of() : managers.holderCountsByDesignation(designationIds);
    }

    @Override
    public Set<UUID> existingTeachers(Collection<UUID> teacherIds) {
        return teacherIds.isEmpty() ? Set.of() : new HashSet<>(teachers.teacherInfo(teacherIds).keySet());
    }

    @Override
    public Map<UUID, List<UUID>> teacherHolders(Collection<UUID> designationIds) {
        return teachers.teachersByDesignation(designationIds);
    }

    @Override
    public Map<UUID, RecordedSalary> teacherSalariesOn(Collection<UUID> teacherIds, LocalDate date) {
        Map<UUID, RecordedSalary> result = new HashMap<>();
        teachers.salariesOn(teacherIds, date)
                .forEach((id, s) -> result.put(id, new RecordedSalary(s.amount(), s.effectiveOn())));
        return result;
    }

    @Override
    public List<Placement> placements(Collection<UUID> teacherIds, LocalDate from, LocalDate to) {
        return teachers.placementsOverlapping(teacherIds, from, to).stream()
                .map(p -> new Placement(p.teacherId(), p.schoolId(), p.startsOn(), p.endsOn()))
                .sorted(Comparator.comparing(Placement::from))
                .toList();
    }

    private static DesignationRef ref(DesignationInfo info) {
        return new DesignationRef(info.id(), info.name(), info.kind(), info.retired());
    }
}
