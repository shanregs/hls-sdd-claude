package com.hls.tasks.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contract C1: tasks as planned activities, in the same shape as drives, visits and sessions. */
public interface TaskActivities {

    /** One task as a planned activity: kind is the task type, date the due date, status the task status. */
    record PlannedActivity(String kind, UUID id, UUID ownerUserId, LocalDate date, String place, String status) {}

    /** Non-cancelled tasks due between the dates (inclusive); a null owner means everyone's. */
    List<PlannedActivity> plannedBetween(LocalDate from, LocalDate to, UUID ownerUserId);
}
