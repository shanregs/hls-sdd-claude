package com.hls.attendance.internal;

import com.hls.attendance.api.SchoolAttendance;
import com.hls.attendance.internal.AttendanceReportSource.TeacherFigures;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.PlacementSpan;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Spec 014b A6: the Attendance report's per-Teacher figures, grouped by School, so the two reads share one rule. */
@Service
@Transactional(readOnly = true)
class SchoolAttendanceImpl implements SchoolAttendance {

    private final AttendanceReportSource report;
    private final TeacherDirectory teachers;

    SchoolAttendanceImpl(AttendanceReportSource report, TeacherDirectory teachers) {
        this.report = report;
        this.teachers = teachers;
    }

    @Override
    public Map<UUID, SchoolRollup> rollupsBySchool(Collection<UUID> schoolIds, YearMonth month) {
        if (schoolIds.isEmpty()) {
            return Map.of();
        }
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        Set<UUID> wanted = new HashSet<>(schoolIds);
        Set<UUID> candidates = teachers.teachersPlacedDuring(from, to);
        Set<UUID> atSchools = new HashSet<>();
        for (PlacementSpan span : teachers.placementsOverlapping(candidates, from, to)) {
            if (wanted.contains(span.schoolId())) {
                atSchools.add(span.teacherId());
            }
        }
        if (atSchools.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<TeacherFigures>> bySchool = new LinkedHashMap<>();
        for (TeacherFigures t : report.figuresOf(atSchools, month).rows()) {
            if (t.schoolId() != null && wanted.contains(t.schoolId())) {
                bySchool.computeIfAbsent(t.schoolId(), k -> new ArrayList<>()).add(t);
            }
        }
        return bySchool.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> rollupOf(e.getKey(), e.getValue()), (a, b) -> a, LinkedHashMap::new));
    }

    /** The one place a School's figures are added up; the Attendance report's Schools rows use it too. */
    static SchoolRollup rollupOf(UUID schoolId, List<TeacherFigures> group) {
        return new SchoolRollup(
                schoolId,
                group.size(),
                sum(group, TeacherFigures::working),
                sum(group, TeacherFigures::present),
                sum(group, TeacherFigures::absent),
                sum(group, TeacherFigures::leave),
                sum(group, TeacherFigures::lop));
    }

    private static BigDecimal sum(List<TeacherFigures> rows, java.util.function.Function<TeacherFigures, BigDecimal> f) {
        return rows.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
