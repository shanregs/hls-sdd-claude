package com.hls.attendance.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A Teacher's day is now an absence or approved leave on a working day (amendment A2 to spec 008), so a class may be
 * uncovered. Published inside the transaction of every write that leaves such a mark; consumers must tolerate a repeat.
 *
 * @param kind {@code ABSENT} for an absence marked by the Teacher or a supervisor, {@code LEAVE} for approved leave
 * @param lossOfPay whether the day is unpaid (an absence with no leave behind it, or a Loss-of-Pay leave day)
 */
public record TeacherAbsenceRecorded(
        UUID teacherId, UUID schoolId, LocalDate date, BigDecimal dayValue, String kind, boolean lossOfPay, UUID actorUserId) {

    public static final String ABSENT = "ABSENT";
    public static final String LEAVE = "LEAVE";
}
