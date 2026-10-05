package com.hls.recruitment.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Marketing visits, calls and meetings as planned activities (contract C1); a null owner means everyone. */
public interface MarketingActivities {

    List<PlannedActivity> plannedBetween(LocalDate from, LocalDate to, UUID ownerUserId);
}
