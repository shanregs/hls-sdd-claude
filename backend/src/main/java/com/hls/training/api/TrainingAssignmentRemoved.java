package com.hls.training.api;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** People no longer have to attend a session (an assignment was removed before it was held, spec 024). */
public record TrainingAssignmentRemoved(UUID sessionId, Set<UUID> userIds, LocalDate date, String topic) {}
