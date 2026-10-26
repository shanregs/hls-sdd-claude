package com.hls.training.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Training sessions as planned activities (roadmap contract C1, spec 024), for the Activity Calendar, Manager field days and
 * later specs. One entry per session. Teachers are never in the feed, and no topic, trainer, roster, outcome, reason or
 * remark is returned.
 */
public interface TrainingActivities {

    /** One planned activity (contract C1): kind, owner, date, place and status. */
    record PlannedActivity(String kind, UUID id, UUID ownerUserId, LocalDate date, String place, String status) {}

    /**
     * One session in any live status, for the Activity Calendar (spec 035).
     *
     * @param kind always {@code TRAINING}
     * @param subKind {@code WEEKEND} or {@code MONTHLY}
     * @param ownerUserId the user who created the session (Admin or Director)
     * @param attendeeUserIds the user ids of the assigned Managers
     * @param originalDate the first date before the session was moved, else null
     * @param status {@code PLANNED}, {@code HELD} or {@code CANCELLED}
     * @param completedOn the session date for a held session, else null
     */
    record ActivityEntry(
            String kind,
            String subKind,
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
            LocalDate completedOn) {}

    /** Published (not held, not cancelled) sessions dated in the range. A given user matches owner or attendee; null means all. */
    List<PlannedActivity> plannedBetween(LocalDate from, LocalDate to, UUID userId);

    /** Published, held and cancelled sessions dated in the range. Draft and skipped sessions are never returned. */
    List<ActivityEntry> activitiesBetween(LocalDate from, LocalDate to, UUID userId);
}
