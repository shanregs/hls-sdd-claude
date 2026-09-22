package com.hls.attendance.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** No delete method — a date is deactivated, never removed (FR-022, data-model.md). */
public interface AttendanceNonWorkingDateRepository extends Repository<AttendanceNonWorkingDate, UUID> {

    AttendanceNonWorkingDate save(AttendanceNonWorkingDate date);

    Optional<AttendanceNonWorkingDate> findById(UUID id);

    List<AttendanceNonWorkingDate> findByActiveTrueAndDateBetween(LocalDate start, LocalDate end);

    List<AttendanceNonWorkingDate> findByActiveTrue();
}
