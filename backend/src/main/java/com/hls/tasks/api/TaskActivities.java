package com.hls.tasks.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contract C1: tasks as planned activities, in the same shape as drives, visits and sessions. */
public interface TaskActivities {

    /** One task as a planned activity: kind is the task type, date the due date, status the task status. */
    record PlannedActivity(String kind, UUID id, UUID ownerUserId, LocalDate date, String place, String status) {}

    /**
     * One task for the Activity Calendar (spec 035), in any status including CANCELLED. No title, description, note or
     * evidence. {@code kind} is the task type, {@code status} the task status name, {@code originalDate} the first due
     * date when it was moved, {@code completedOn} the business date it was completed (null when open or sent back).
     */
    record ActivityEntry(
            String kind,
            UUID id,
            UUID ownerUserId,
            LocalDate date,
            LocalDate originalDate,
            String place,
            String linkedType,
            UUID linkedId,
            String status,
            LocalDate completedOn) {}

    /** Non-cancelled tasks due between the dates (inclusive); a null owner means everyone's. */
    List<PlannedActivity> plannedBetween(LocalDate from, LocalDate to, UUID ownerUserId);

    /** Every task due between the dates (inclusive) including CANCELLED ones; a null owner means everyone's. */
    List<ActivityEntry> activitiesBetween(LocalDate from, LocalDate to, UUID ownerUserId);
}
