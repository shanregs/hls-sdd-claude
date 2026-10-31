package com.hls.activitycalendar.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One cell entry of the calendar (contracts/calendar-api.md). Carries no notes, outcome text, evidence, attachments or
 * incentive data (FR-020).
 */
public record CalendarItem(
        String key,
        UUID activityId,
        String kind,
        String subKind,
        LocalDate date,
        String status,
        boolean doneLate,
        LocalDate completedOn,
        boolean rescheduled,
        LocalDate originalDate,
        Integer daysMoved,
        Integer daysLate,
        List<Person> people,
        UUID zoneId,
        String zoneName,
        String place,
        @JsonInclude(JsonInclude.Include.NON_NULL) Link link,
        boolean sameDay) {

    /** The form that does not say whether the item was logged on the spot (spec 040): it was not. */
    public CalendarItem(
            String key,
            UUID activityId,
            String kind,
            String subKind,
            LocalDate date,
            String status,
            boolean doneLate,
            LocalDate completedOn,
            boolean rescheduled,
            LocalDate originalDate,
            Integer daysMoved,
            Integer daysLate,
            List<Person> people,
            UUID zoneId,
            String zoneName,
            String place,
            Link link) {
        this(key, activityId, kind, subKind, date, status, doneLate, completedOn, rescheduled, originalDate, daysMoved, daysLate, people, zoneId, zoneName, place, link, false);
    }

    /** The owner or an attending Manager; {@code manager} tells a Manager from another user such as the Admin who created it. */
    public record Person(UUID userId, String name, String zoneName, boolean manager) {}

    /** A link to the owning screen, present only when the caller may view that screen. */
    public record Link(String screen, String path) {}

    public boolean involves(UUID userId) {
        return people.stream().anyMatch(p -> p.userId().equals(userId));
    }
}
