package com.hls.substitution.internal;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.attendance.api.WorkingCalendar;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.PlacementSpan;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who is free to cover a requirement (spec 017a clarifications): an ACTIVE Teacher other than the absent one, with no
 * leave or absence on the date, whose substitutions on the date add up to at most one day with this one, and who is
 * either not placed in a School that day or placed in a School for which the date is not a working day.
 */
@Service
public class AvailabilityService {

    private static final BigDecimal WHOLE_DAY = new BigDecimal("1.00");

    /** One active Teacher with the verdict; {@code current} marks the substitute already assigned to the requirement. */
    public record Candidate(UUID teacherId, String name, boolean available, String reason, boolean current) {}

    private final TeacherDirectory teachers;
    private final AttendanceReadApi attendance;
    private final WorkingCalendar calendar;
    private final SchoolDirectory schools;
    private final SubstitutionRepository requirements;

    public AvailabilityService(
            TeacherDirectory teachers,
            AttendanceReadApi attendance,
            WorkingCalendar calendar,
            SchoolDirectory schools,
            SubstitutionRepository requirements) {
        this.teachers = teachers;
        this.attendance = attendance;
        this.calendar = calendar;
        this.schools = schools;
        this.requirements = requirements;
    }

    @Transactional(readOnly = true)
    public List<Candidate> candidates(SubstitutionRequirement requirement) {
        List<TeacherInfo> active = teachers.activeTeachers().stream()
                .filter(t -> !t.id().equals(requirement.getTeacherId()))
                .toList();
        return judge(requirement, active);
    }

    /** The verdict for one Teacher; empty when the Teacher is not active (or does not exist). */
    @Transactional(readOnly = true)
    public Optional<Candidate> candidate(SubstitutionRequirement requirement, UUID teacherId) {
        return candidates(requirement).stream().filter(c -> c.teacherId().equals(teacherId)).findFirst();
    }

    private List<Candidate> judge(SubstitutionRequirement requirement, List<TeacherInfo> active) {
        if (active.isEmpty()) {
            return List.of();
        }
        LocalDate date = requirement.getDutyDate();
        List<UUID> ids = active.stream().map(TeacherInfo::id).toList();

        Set<UUID> away = attendance.teachersAwayOn(ids, date);
        Map<UUID, BigDecimal> covered = new HashMap<>();
        for (SubstitutionRequirement other : requirements.assignedOn(ids, date)) {
            if (!other.getId().equals(requirement.getId())) {
                covered.merge(other.getSubstituteTeacherId(), other.getDayValue(), BigDecimal::add);
            }
        }
        Map<UUID, PlacementSpan> placement = new HashMap<>();
        for (PlacementSpan span : teachers.placementsOverlapping(ids, date, date)) {
            if (span.covers(date)) {
                placement.putIfAbsent(span.teacherId(), span);
            }
        }
        Set<UUID> placedSchools = placement.values().stream().map(PlacementSpan::schoolId).collect(Collectors.toSet());
        Map<UUID, List<LocalDate>> workingDays = placedSchools.isEmpty()
                ? Map.of()
                : calendar.workingDaysBySchool(placedSchools, date, date);
        Map<UUID, String> schoolNames = schools.schools(placedSchools).stream()
                .collect(Collectors.toMap(SchoolInfo::id, SchoolInfo::name));

        List<Candidate> result = new ArrayList<>();
        for (TeacherInfo t : active) {
            String reason = null;
            if (away.contains(t.id())) {
                reason = "On leave or absent that day";
            } else if (covered.getOrDefault(t.id(), BigDecimal.ZERO).add(requirement.getDayValue()).compareTo(WHOLE_DAY) > 0) {
                reason = "Already covering another class that day";
            } else if (placement.containsKey(t.id())) {
                UUID school = placement.get(t.id()).schoolId();
                boolean working = workingDays.getOrDefault(school, List.of()).contains(date);
                if (working) {
                    reason = "Teaching at " + schoolNames.getOrDefault(school, "their School") + " that day";
                }
            }
            result.add(new Candidate(
                    t.id(),
                    t.name(),
                    reason == null,
                    reason,
                    t.id().equals(requirement.getSubstituteTeacherId())));
        }
        result.sort(Comparator.comparing(Candidate::available).reversed().thenComparing(Candidate::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }
}
