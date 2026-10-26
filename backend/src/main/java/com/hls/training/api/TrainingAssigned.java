package com.hls.training.api;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** People were given a new expected-attendance obligation for a session (spec 024); {@code userIds} are user accounts. */
public record TrainingAssigned(UUID sessionId, Set<UUID> userIds, LocalDate date, String topic) {}
