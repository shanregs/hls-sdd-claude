package com.hls.substitution.payout;

import com.hls.payroll.api.DayRateWork;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.teacher.api.TeacherDirectory;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the pay run reads of the substitute days (spec 017b FR-011): the Present days of locked substitute-months, and the
 * Substitutes who have days that cannot be paid yet. Implements the port that payroll owns, so payroll never depends on
 * this module. A fixed number of queries however many days there are.
 */
@Service
@Transactional(readOnly = true)
class DayRateWorkImpl implements DayRateWork {

    private final SubstituteDayRepository days;
    private final MonthLockRepository locks;
    private final TeacherDirectory teachers;
    private final SchoolDirectory schools;

    DayRateWorkImpl(
            SubstituteDayRepository days,
            MonthLockRepository locks,
            TeacherDirectory teachers,
            SchoolDirectory schools) {
        this.days = days;
        this.locks = locks;
        this.teachers = teachers;
        this.schools = schools;
    }

    @Override
    public Map<UUID, List<WorkedDay>> presentDays(YearMonth month) {
        return worked(month, false);
    }

    @Override
    public Map<UUID, List<WorkedDay>> unpaidPresentDays(YearMonth month) {
        return worked(month, true);
    }

    @Override
    public List<NotReady> notReady(YearMonth month) {
        // only days that would be paid (Present) or could still become Present (not marked) matter
        List<SubstituteDay> relevant = days.between(month.atDay(1), month.atEndOfMonth()).stream()
                .filter(d -> d.isLive() && d.getAttendance() != DayAttendance.ABSENT)
                .toList();
        if (relevant.isEmpty()) {
            return List.of();
        }
        Set<UUID> locked = lockedTeachers(month);
        Set<UUID> active = teachers.activeSubstituteTeacherIds();
        Map<UUID, String> reasons = new LinkedHashMap<>();
        for (SubstituteDay d : relevant) {
            UUID id = d.getSubstituteTeacherId();
            if (!active.contains(id)) {
                reasons.putIfAbsent(id, "not an Active Substitute");
            } else if (!locked.contains(id)) {
                reasons.putIfAbsent(id, "attendance is not locked");
            }
        }
        List<NotReady> result = new ArrayList<>();
        reasons.forEach((id, reason) -> result.add(new NotReady(id, reason)));
        return result;
    }

    private Map<UUID, List<WorkedDay>> worked(YearMonth month, boolean onlyUnheld) {
        List<SubstituteDay> present = days.between(month.atDay(1), month.atEndOfMonth()).stream()
                .filter(d -> d.isLive() && d.getAttendance() == DayAttendance.PRESENT)
                .filter(d -> !onlyUnheld || d.getRunId() == null)
                .toList();
        if (present.isEmpty()) {
            return Map.of();
        }
        Set<UUID> locked = lockedTeachers(month);
        Set<UUID> active = teachers.activeSubstituteTeacherIds();
        Map<UUID, String> schoolNames = schools
                .schools(present.stream().map(SubstituteDay::getSchoolId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(SchoolInfo::id, SchoolInfo::name));
        Map<UUID, List<WorkedDay>> result = new LinkedHashMap<>();
        for (SubstituteDay d : present) {
            UUID teacher = d.getSubstituteTeacherId();
            if (!locked.contains(teacher) || !active.contains(teacher)) {
                continue;
            }
            LocalDate date = d.getDayDate();
            result.computeIfAbsent(teacher, k -> new ArrayList<>())
                    .add(new WorkedDay(
                            d.getId(),
                            teacher,
                            d.getSchoolId(),
                            schoolNames.getOrDefault(d.getSchoolId(), "?"),
                            date,
                            d.getDayValue()));
        }
        return result;
    }

    private Set<UUID> lockedTeachers(YearMonth month) {
        Set<UUID> set = new HashSet<>();
        for (MonthLock lock : locks.lockedIn(month.atDay(1))) {
            set.add(lock.getSubstituteTeacherId());
        }
        return set;
    }
}
