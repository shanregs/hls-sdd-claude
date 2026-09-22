package com.hls.attendance.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface AttendanceTeacherMonthLockRepository extends Repository<AttendanceTeacherMonthLock, UUID> {

    AttendanceTeacherMonthLock save(AttendanceTeacherMonthLock lock);

    Optional<AttendanceTeacherMonthLock> findByTeacherIdAndPeriod(UUID teacherId, String period);
}
