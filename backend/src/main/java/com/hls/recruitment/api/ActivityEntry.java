package com.hls.recruitment.api;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * One dated activity in any status, for the Activity Calendar (spec 035, contract C1 second method). Carries no notes,
 * outcome, attachments or other free text: only what the calendar needs to place and classify the item.
 *
 * @param kind the kind string of the source (CAMPUS_DRIVE, SCHOOL_VISIT, SCHOOL_CALL, ...)
 * @param ownerUserId the creator or scheduler
 * @param attendeeUserIds attendees or interviewers (may be empty)
 * @param originalDate the first planned date when the item was moved, else null
 * @param status the stored status of the source
 * @param completedOn the business date it was completed or held, null when unknown
 */
public record ActivityEntry(
        String kind,
        UUID id,
        UUID ownerUserId,
        Set<UUID> attendeeUserIds,
        LocalDate date,
        LocalDate originalDate,
        String place,
        UUID schoolId,
        UUID zoneId,
        String linkedType,
        UUID linkedId,
        String status,
        LocalDate completedOn,
        boolean sameDay) {

    /** The form that does not say whether the item was logged on the spot (spec 040): it was not. */
    public ActivityEntry(
            String kind,
            UUID id,
            UUID ownerUserId,
            Set<UUID> attendeeUserIds,
            LocalDate date,
            LocalDate originalDate,
            String place,
            UUID schoolId,
            UUID zoneId,
            String linkedType,
            UUID linkedId,
            String status,
            LocalDate completedOn) {
        this(kind, id, ownerUserId, attendeeUserIds, date, originalDate, place, schoolId, zoneId, linkedType, linkedId, status, completedOn, false);
    }
}
