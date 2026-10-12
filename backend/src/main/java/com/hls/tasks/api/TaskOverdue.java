package com.hls.tasks.api;

import java.time.LocalDate;
import java.util.UUID;

/** Published once per task and due date when an open task passes its due date; the assignee is told. */
public record TaskOverdue(UUID taskId, String title, UUID assigneeUserId, LocalDate dueOn) {}
