package com.hls.attendance.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** No delete method — codes are deactivated, never removed (FR-005). */
public interface AttendanceStatusCodeRepository extends Repository<AttendanceStatusCode, String> {

    AttendanceStatusCode save(AttendanceStatusCode statusCode);

    Optional<AttendanceStatusCode> findById(String code);

    List<AttendanceStatusCode> findByActiveTrue();

    List<AttendanceStatusCode> findAll();
}
