package com.hls.attendance.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface AttendanceReopenRecordRepository extends Repository<AttendanceReopenRecord, UUID> {

    AttendanceReopenRecord save(AttendanceReopenRecord record);

    List<AttendanceReopenRecord> findByLockIdOrderByReopenedAtAsc(UUID lockId);

    /** Finds the currently-open reopen cycle for a lock, if any (FR-013 re-lock). */
    Optional<AttendanceReopenRecord> findByLockIdAndRelockedAtIsNull(UUID lockId);
}
