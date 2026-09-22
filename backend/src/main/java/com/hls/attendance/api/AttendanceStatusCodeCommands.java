package com.hls.attendance.api;

import com.hls.attendance.api.dto.AttendanceStatusCodeView;
import com.hls.attendance.api.dto.CreateStatusCodeRequest;
import java.util.UUID;

/** Public write surface for the configurable Attendance Status Codes (FR-005, Admin-or-Director — enforced by controller). */
public interface AttendanceStatusCodeCommands {

    /** {@code actingRole} is whichever of "ADMIN"/"DIRECTOR" the caller's JWT carried — recorded verbatim in the audit trail. */
    AttendanceStatusCodeView createStatusCode(CreateStatusCodeRequest request, UUID actingUserId, String actingRole);
}
