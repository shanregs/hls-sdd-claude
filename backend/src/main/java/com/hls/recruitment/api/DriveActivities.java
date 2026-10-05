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
}
