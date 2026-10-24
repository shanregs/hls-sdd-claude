package com.hls.recruitment.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Campus drives as planned activities (roadmap contract C1), for the calendar and incentive specs. One record per
 * drive date for a drive the user scheduled or attends; a null user means every drive.
 */
public interface DriveActivities {

    List<PlannedActivity> plannedBetween(LocalDate from, LocalDate to, UUID ownerUserId);

    /**
     * One entry per drive date in the range for every status: PLANNED, HELD, CANCELLED (spec 035). A null user means
     * every drive; a given user matches the scheduler or an interviewer.
     */
    List<ActivityEntry> activitiesBetween(LocalDate from, LocalDate to, UUID userId);
}
