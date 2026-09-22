package com.hls.attendance.api;

import com.hls.attendance.api.dto.AddNonWorkingDateRequest;
import com.hls.attendance.api.dto.NonWorkingDateView;
import java.util.UUID;

/** Public write surface for the shared Non-Working Calendar (FR-022, Admin-only). */
public interface AttendanceNonWorkingCalendarCommands {

    NonWorkingDateView addNonWorkingDate(AddNonWorkingDateRequest request, UUID actingUserId);

    NonWorkingDateView deactivateNonWorkingDate(UUID id, UUID actingUserId);
}
