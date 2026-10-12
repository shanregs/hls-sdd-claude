package com.hls.tasks.internal;

import java.util.EnumSet;
import java.util.Set;

/** Where a task is in its life: Assigned, Acknowledged, In progress, Completed, Verified, or Cancelled. */
public enum TaskStatus {
    ASSIGNED,
    ACKNOWLEDGED,
    IN_PROGRESS,
    COMPLETED,
    VERIFIED,
    CANCELLED;

    /** Statuses in which the work is still to be done by the assignee (a task here can be overdue). */
    public static final Set<TaskStatus> OPEN = EnumSet.of(ASSIGNED, ACKNOWLEDGED, IN_PROGRESS);

    /** Statuses that keep a source key reserved (matches the partial unique index). */
    public static final Set<TaskStatus> LIVE = EnumSet.of(ASSIGNED, ACKNOWLEDGED, IN_PROGRESS, COMPLETED);

    public boolean isOpen() {
        return OPEN.contains(this);
    }
}
