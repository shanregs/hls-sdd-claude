package com.hls.tasks.api;

import java.time.LocalDate;
import java.util.UUID;

/** Published once per task when it has stayed open for its escalation days past the due date; the Directors are told. */
public record TaskEscalated(UUID taskId, String title, UUID assigneeUserId, LocalDate dueOn, int daysOverdue) {}
