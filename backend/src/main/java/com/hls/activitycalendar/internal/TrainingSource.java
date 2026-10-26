package com.hls.activitycalendar.internal;

import com.hls.training.api.TrainingActivities;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Training sessions (spec 024): published, held and cancelled sessions, one entry each. The owner is the Admin or Director
 * who created the session and the attendees are the assigned Managers, so a Manager sees the sessions they must attend.
 * Training has no Zone, so its entries appear under "No Zone".
 */
@Component
public class TrainingSource implements ActivitySource {

    private final TrainingActivities training;

    public TrainingSource(TrainingActivities training) {
        this.training = training;
    }

    @Override
    public String group() {
        return TRAINING;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public List<CalendarEntry> entries(LocalDate from, LocalDate to, UUID userIdOrNull) {
        return training.activitiesBetween(from, to, userIdOrNull).stream()
                .map(e -> new CalendarEntry(
                        "TRAINING",
                        e.subKind(),
                        e.id(),
                        e.date(),
                        e.date(),
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
