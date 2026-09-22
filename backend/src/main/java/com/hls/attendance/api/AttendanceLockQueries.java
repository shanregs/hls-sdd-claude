package com.hls.attendance.api;

import com.hls.attendance.api.dto.LockStatusView;
import java.util.UUID;

/** Public read surface for a teacher-month's lock state (FR-011/FR-013). */
public interface AttendanceLockQueries {

    LockStatusView lockStatus(UUID teacherId, String period);
}
