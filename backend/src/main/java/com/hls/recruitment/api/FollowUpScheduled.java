package com.hls.recruitment.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Published when a completed visit or call records a follow-up date. Spec 025 (tasks) will listen and create the
 * task; until it exists nothing listens and the prospect only shows "follow-up overdue" (contract C2).
 */
public record FollowUpScheduled(UUID activityId, UUID prospectId, UUID ownerUserId, LocalDate dueOn, String title) {}
