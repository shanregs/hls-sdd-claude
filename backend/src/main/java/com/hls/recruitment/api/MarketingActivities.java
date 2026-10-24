package com.hls.recruitment.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Marketing visits, calls and meetings as planned activities (contract C1); a null owner means everyone. */
public interface MarketingActivities {

    List<PlannedActivity> plannedBetween(LocalDate from, LocalDate to, UUID ownerUserId);

    /**
     * Every visit, call and meeting dated in the range with status PLANNED, COMPLETED and CANCELLED (spec 035). A null
     * user means everyone's; a given user matches creator or attendee.
     */
    List<ActivityEntry> activitiesBetween(LocalDate from, LocalDate to, UUID userId);
}
