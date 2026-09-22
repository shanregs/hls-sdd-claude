package com.hls.attendance.api;

import com.hls.attendance.api.dto.AttendanceMarkView;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public read surface for Attendance Marks (FR-010). */
public interface AttendanceMarkQueries {

    Optional<AttendanceMarkView> markForDate(UUID teacherId, LocalDate date);

    List<AttendanceMarkView> marksForMonth(UUID teacherId, String period);
}
