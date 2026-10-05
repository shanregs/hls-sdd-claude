package com.hls.recruitment.api;

import java.time.LocalDate;
import java.util.UUID;

/** One planned activity of a user (contract C1): its kind, owner, date, place and status. */
public record PlannedActivity(String kind, UUID id, UUID ownerUserId, LocalDate date, String place, String status) {}
