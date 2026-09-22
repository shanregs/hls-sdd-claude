package com.hls.attendance.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** No delete method — a mark is corrected by editing its value, never removed (data-model.md). */
public interface AttendanceMarkRepository extends Repository<AttendanceMark, UUID> {

    AttendanceMark save(AttendanceMark mark);

    Optional<AttendanceMark> findById(UUID id);

    Optional<AttendanceMark> findByTeacherIdAndMarkDate(UUID teacherId, LocalDate markDate);

    List<AttendanceMark> findByTeacherIdAndMarkDateBetween(UUID teacherId, LocalDate start, LocalDate end);
}
