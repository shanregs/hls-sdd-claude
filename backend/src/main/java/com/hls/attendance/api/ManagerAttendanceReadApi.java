package com.hls.attendance.api;

import java.time.YearMonth;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * The read contract for Manager attendance (spec 032): payroll and reports ask here instead of recomputing the month.
 * A locked month returns the figures frozen at the lock. It carries no location and no photo.
 */
public interface ManagerAttendanceReadApi {

    /** The month's figures for one Manager. */
    ManagerMonth month(UUID managerId, YearMonth month);

    /** Bulk form; every asked Manager is in the map. A fixed number of queries however many are asked. */
    Map<UUID, ManagerMonth> months(Collection<UUID> managerIds, YearMonth month);

    /**
     * The given Managers who have approved leave for the whole day on the date (spec 024 "on leave" suggestion). A half day
     * of leave does not count. One query however many are asked; unknown Managers are simply absent.
     */
    java.util.Set<UUID> managersOnLeaveOn(Collection<UUID> managerIds, java.time.LocalDate date);
}
