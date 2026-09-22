package com.hls.attendance.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** FR-007/FR-008/FR-009/FR-010. Computed live from Attendance Marks and the Non-Working Calendar (data-model.md). */
public record MonthlyAttendanceRollupView(
        UUID teacherId,
        String period,
        int trainingDaysTotal,
        BigDecimal trainingDaysAttended,
        BigDecimal daysWorked,
        BigDecimal daysLeave,
        int overallWorkingDays,
        int unmarkedDays,
        BigDecimal weightedAttendanceTotal,
        LockStatus lockStatus) {
}
