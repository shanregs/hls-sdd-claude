package com.hls.activitycalendar.internal;

import com.hls.recruitment.api.ActivityEntry;
import com.hls.recruitment.api.DriveActivities;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Campus drives (spec 016). A drive is judged on its last date, so the feed is asked for a wider window to find it. */
@Component
public class DriveSource implements ActivitySource {

    /** A drive does not run longer than this around the month shown. */
    private static final int WINDOW_DAYS = 60;

    private final DriveActivities drives;

    public DriveSource(DriveActivities drives) {
        this.drives = drives;
    }

    @Override
    public String group() {
        return DRIVES;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public List<CalendarEntry> entries(LocalDate from, LocalDate to, UUID userIdOrNull) {
        List<ActivityEntry> wide = drives.activitiesBetween(from.minusDays(WINDOW_DAYS), to.plusDays(WINDOW_DAYS), userIdOrNull);
        Map<UUID, LocalDate> last = new HashMap<>();
        for (ActivityEntry e : wide) {
            last.merge(e.id(), e.date(), (a, b) -> a.isAfter(b) ? a : b);
        }
        return wide.stream()
                .filter(e -> !e.date().isBefore(from) && !e.date().isAfter(to))
                .map(e -> new CalendarEntry(
                        "CAMPUS_DRIVE",
                        null,
                        e.id(),
                        e.date(),
                        last.get(e.id()),
                        e.originalDate(),
                        e.ownerUserId(),
                        e.attendeeUserIds(),
                        e.place(),
                        e.zoneId(),
                        e.schoolId(),
                        e.linkedType(),
                        e.linkedId(),
                        e.status(),
                        e.completedOn()))
                .sorted(Comparator.comparing(CalendarEntry::date))
                .toList();
    }
}
