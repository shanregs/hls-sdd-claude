package com.hls.attendance.manager.internal;

import com.hls.recruitment.api.DriveActivities;
import com.hls.recruitment.api.MarketingActivities;
import com.hls.recruitment.api.PlannedActivity;
import com.hls.tasks.api.TaskActivities;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The planned activities (contract C1) a Manager can link to a Field day: drives, marketing visits and tasks of that
 * user on that date, read through the owning modules' api packages only.
 */
@Component
public class PlannedActivities {

    /** One activity the Manager can link, named by its kind and id. */
    public record Planned(String kind, UUID id, LocalDate date, String place, String status) {}

    private final DriveActivities drives;
    private final MarketingActivities marketing;
    private final TaskActivities tasks;

    public PlannedActivities(DriveActivities drives, MarketingActivities marketing, TaskActivities tasks) {
        this.drives = drives;
        this.marketing = marketing;
        this.tasks = tasks;
    }

    /** The user's activities dated exactly {@code date}. */
    public List<Planned> forDay(UUID userId, LocalDate date) {
        List<Planned> out = new ArrayList<>();
        for (PlannedActivity a : drives.plannedBetween(date, date, userId)) {
            add(out, a.kind(), a.id(), a.date(), a.place(), a.status(), date);
        }
        for (PlannedActivity a : marketing.plannedBetween(date, date, userId)) {
            add(out, a.kind(), a.id(), a.date(), a.place(), a.status(), date);
        }
        for (TaskActivities.PlannedActivity a : tasks.plannedBetween(date, date, userId)) {
            add(out, a.kind(), a.id(), a.date(), a.place(), a.status(), date);
        }
        out.sort(Comparator.comparing(Planned::kind).thenComparing(Planned::id));
        return out;
    }

    private static void add(List<Planned> out, String kind, UUID id, LocalDate on, String place, String status, LocalDate date) {
        if (date.equals(on)) {
            out.add(new Planned(kind, id, on, place, status));
        }
    }
}
