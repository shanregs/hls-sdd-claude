package com.hls.activitycalendar.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Placeholder for training sessions (spec 024 does not exist yet): it reports itself unavailable and has no entries, so
 * the calendar says "Training is not included yet" instead of hiding the gap. Spec 024 later supplies its own
 * {@link ActivitySource} bean for the TRAINING group; this one is removed.
 */
@Component
public class TrainingSource implements ActivitySource {

    @Override
    public String group() {
        return TRAINING;
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public List<CalendarEntry> entries(LocalDate from, LocalDate to, UUID userIdOrNull) {
        return List.of();
    }
}
