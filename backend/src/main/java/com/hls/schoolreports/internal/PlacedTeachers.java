package com.hls.schoolreports.internal;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.reporting.api.MonthState;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.PlacementSpan;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Which Teachers decide whether a month is still open for the School report: those placed at the Schools the caller may see,
 * never another Zone's Teachers. A null School set means the whole organization.
 */
final class PlacedTeachers {

    private static final int LOOK_BACK_MONTHS = 12;

    private PlacedTeachers() {}

    /** Teachers with an ACTIVE placement overlapping {@code from..to} at one of the given Schools (null: anywhere). */
    static Set<UUID> during(TeacherDirectory teachers, Set<UUID> schoolIds, LocalDate from, LocalDate to) {
        Set<UUID> placed = teachers.teachersPlacedDuring(from, to);
        if (schoolIds == null || placed.isEmpty()) {
            return placed;
        }
        return inSchools(teachers.placementsOverlapping(placed, from, to), schoolIds, from, to);
    }

    private static Set<UUID> inSchools(List<PlacementSpan> spans, Set<UUID> schoolIds, LocalDate from, LocalDate to) {
        Set<UUID> result = new HashSet<>();
        for (PlacementSpan s : spans) {
            if (overlaps(s, from, to) && (schoolIds == null || schoolIds.contains(s.schoolId()))) {
                result.add(s.teacherId());
            }
        }
        return result;
    }

    private static boolean overlaps(PlacementSpan s, LocalDate from, LocalDate to) {
        return !s.startsOn().isAfter(to) && (s.endsOn() == null || !s.endsOn().isBefore(from));
    }

    static MonthState stateOf(Set<UUID> placed, Set<UUID> locked) {
        if (placed.isEmpty()) {
            return MonthState.NONE;
        }
        return locked.containsAll(placed) ? MonthState.LOCKED : MonthState.OPEN;
    }

    static MonthState stateOf(TeacherDirectory teachers, AttendanceReadApi attendance, Set<UUID> schoolIds, YearMonth month) {
        Set<UUID> placed = during(teachers, schoolIds, month.atDay(1), month.atEndOfMonth());
        return placed.isEmpty() ? MonthState.NONE : stateOf(placed, attendance.lockedTeachers(placed, month));
    }

    /**
     * The latest month, looking back 12 from {@code start}, in which at least one Teacher was placed and all of them are
     * locked. Placements are read once for the whole window; attendance once per month that has Teachers.
     */
    static Optional<YearMonth> lastLocked(
            TeacherDirectory teachers, AttendanceReadApi attendance, Set<UUID> schoolIds, YearMonth start) {
        YearMonth oldest = start.minusMonths(LOOK_BACK_MONTHS - 1L);
        Set<UUID> everyone = teachers.teachersPlacedDuring(oldest.atDay(1), start.atEndOfMonth());
        if (everyone.isEmpty()) {
            return Optional.empty();
        }
        List<PlacementSpan> spans = teachers.placementsOverlapping(everyone, oldest.atDay(1), start.atEndOfMonth());
        for (YearMonth month = start; !month.isBefore(oldest); month = month.minusMonths(1)) {
            Set<UUID> placed = inSchools(spans, schoolIds, month.atDay(1), month.atEndOfMonth());
            if (!placed.isEmpty() && attendance.lockedTeachers(placed, month).containsAll(placed)) {
                return Optional.of(month);
            }
        }
        return Optional.empty();
    }
}
