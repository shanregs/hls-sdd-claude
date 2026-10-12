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
}
