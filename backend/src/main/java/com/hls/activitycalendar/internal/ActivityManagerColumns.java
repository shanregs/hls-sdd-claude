package com.hls.activitycalendar.internal;

import com.hls.activitycalendar.internal.ActivityCalendarService.Caller;
import com.hls.identity.user.Role;
import com.hls.reporting.api.ManagerColumns;
import com.hls.reporting.api.TileContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Planned, done, missed and rescheduled activities per Manager for the month (spec 014c A7), from the same summary the
 * Activity Calendar shows ({@link CalendarSummary#byManager()}), so the figures equal the calendar. A Manager with no
 * activity in the month is absent from the answer ("no data yet", never a false zero). When a source of the calendar
 * could not be loaded the counts would be incomplete, so this throws and the caller shows the columns as not available.
 */
@Component
class ActivityManagerColumns implements ManagerColumns {

    static final String PLANNED = "activity.planned";
    static final String DONE = "activity.done";
    static final String MISSED = "activity.missed";
    static final String RESCHEDULED = "activity.rescheduled";

    /** The org-wide view: a ManagerColumns caller has already limited the Managers asked for. */
    private static final Set<Role> ORG_WIDE = Set.of(Role.ADMIN);

    private final ActivityCalendarService calendar;

    ActivityManagerColumns(ActivityCalendarService calendar) {
        this.calendar = calendar;
    }

    @Override
    public List<Column> columns() {
        return List.of(
                new Column(PLANNED, "Activities planned", "/operations/activity-calendar?managerId={managerId}"),
                new Column(DONE, "Activities done", "/operations/activity-calendar?managerId={managerId}&status=DONE"),
                new Column(MISSED, "Activities missed", "/operations/activity-calendar?managerId={managerId}&status=MISSED"),
                new Column(RESCHEDULED, "Activities rescheduled", "/operations/activity-calendar?managerId={managerId}"));
    }

    @Override
    public Map<UUID, Map<String, Long>> figures(TileContext ctx, Set<UUID> managerUserIds) {
        CalendarResponse response = ctx.memo("activity.managerColumns." + ctx.month(), () -> calendar.month(
                new Caller(ctx.userId(), ORG_WIDE), ctx.month()));
        if (response.partial()) {
            throw new IllegalStateException("The Activity Calendar could not load every source");
        }
        Map<UUID, Map<String, Long>> result = new HashMap<>();
        List<CalendarSummary.ManagerCounts> managers = response.summary().byManager();
        if (managers == null) {
            return result;
        }
        for (CalendarSummary.ManagerCounts m : managers) {
            if (managerUserIds.contains(m.userId())) {
                result.put(m.userId(), Map.of(
                        PLANNED, (long) m.planned(),
                        DONE, (long) m.done(),
                        MISSED, (long) m.missed(),
                        RESCHEDULED, (long) m.rescheduled()));
            }
        }
        return result;
    }
}
