package com.hls.attendance.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Training seen from Manager attendance (spec 024 amends spec 032). Owned by {@code attendance} and implemented by
 * {@code training}, so attendance never depends on training: the Field day link offers the sessions a Manager is assigned
 * to, and payroll can ask which days a Manager attended training.
 */
public interface ManagerTrainingDays {

    /** A session a Manager is assigned to, as a choice on the Field day. */
    record TrainingSessionRef(UUID sessionId, LocalDate date, String place, String status) {}

    /** Published and held sessions on the date that the Manager (by user id) is assigned to. */
    List<TrainingSessionRef> assignedSessionsOn(UUID managerUserId, LocalDate date);

    /** The Manager's attended training days in the range: 1.00 or 0.50 for each day recorded Present. */
    Map<LocalDate, BigDecimal> attendedDays(UUID managerId, LocalDate from, LocalDate to);
}
