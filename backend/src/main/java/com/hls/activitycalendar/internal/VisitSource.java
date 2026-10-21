package com.hls.activitycalendar.internal;

import com.hls.recruitment.api.ActivityEntry;
import com.hls.recruitment.api.MarketingActivities;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** School visits, calls and meetings (spec 023). */
@Component
public class VisitSource implements ActivitySource {

    private final MarketingActivities marketing;

    public VisitSource(MarketingActivities marketing) {
        this.marketing = marketing;
    }

    @Override
    public String group() {
        return VISITS;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public List<CalendarEntry> entries(LocalDate from, LocalDate to, UUID userIdOrNull) {
        return marketing.activitiesBetween(from, to, userIdOrNull).stream().map(VisitSource::toEntry).toList();
    }

    /** VISIT becomes School visit, CALL a Call, PROPOSAL_MEETING and FOLLOW_UP a Meeting. */
    static String kindOf(String feedKind) {
        return switch (feedKind) {
            case "SCHOOL_VISIT" -> "SCHOOL_VISIT";
            case "SCHOOL_CALL" -> "CALL";
            default -> "MEETING";
        };
    }

    private static CalendarEntry toEntry(ActivityEntry e) {
        return new CalendarEntry(
                kindOf(e.kind()),
                null,
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
                e.completedOn());
    }
}
