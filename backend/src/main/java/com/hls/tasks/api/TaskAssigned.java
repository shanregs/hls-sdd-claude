package com.hls.tasks.api;

import java.time.LocalDate;
import java.util.UUID;

/** Published when a task is created for a Manager or reassigned to one; the assignee is told. */
public record TaskAssigned(UUID taskId, String title, UUID assigneeUserId, UUID assignedByUserId, LocalDate dueOn) {}
