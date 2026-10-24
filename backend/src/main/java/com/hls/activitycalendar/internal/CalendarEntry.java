package com.hls.activitycalendar.internal;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * What a source adapter returns: one dated activity of any native status. Built from a feed record; nothing else
 * crosses the module boundary (no notes, outcome, evidence or attachments).
 *
 * @param kind CAMPUS_DRIVE, SCHOOL_VISIT, CALL, MEETING, TASK or TRAINING
 * @param subKind the task type for a task, else null
 * @param lastDate the last date of a multi-date drive (the date its status is judged on), else the date itself
 * @param originalDate the first planned date when the item was moved, else null
 * @param nativeStatus the status name in the owning module (PLANNED, COMPLETED, HELD, VERIFIED, CANCELLED, ...)
 * @param completedOn the business date it was completed or held, null when unknown
 */
public record CalendarEntry(
        String kind,
        String subKind,
        UUID id,
        LocalDate date,
        LocalDate lastDate,
        LocalDate originalDate,
        UUID ownerUserId,
        Set<UUID> attendeeUserIds,
        String place,
        UUID zoneId,
        UUID schoolId,
        String linkedType,
        UUID linkedId,
        String nativeStatus,
        LocalDate completedOn) {

    public CalendarEntry {
        attendeeUserIds = attendeeUserIds == null ? Set.of() : Set.copyOf(attendeeUserIds);
        lastDate = lastDate == null ? date : lastDate;
    }
}
