package com.hls.attendance.internal;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceMarkRepository extends JpaRepository<AttendanceMark, UUID> {

    List<AttendanceMark> findByLeaveRequestId(UUID leaveRequestId);

    Optional<AttendanceMark> findByTeacherIdAndMarkDate(UUID teacherId, LocalDate markDate);

    List<AttendanceMark> findByTeacherIdAndMarkDateBetween(UUID teacherId, LocalDate from, LocalDate to);

    List<AttendanceMark> findByTeacherIdInAndMarkDateBetween(
            Collection<UUID> teacherIds, LocalDate from, LocalDate to);

    boolean existsByStatusCodeId(UUID statusCodeId);
}
