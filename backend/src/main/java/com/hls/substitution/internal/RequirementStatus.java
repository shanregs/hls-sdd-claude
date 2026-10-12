package com.hls.substitution.internal;

/** Where a substitution requirement stands (spec 017a data-model.md). */
public enum RequirementStatus {
    OPEN,
    ASSIGNED,
    MANAGER_COVER,
    CANCELLED;

    public boolean isLive() {
        return this != CANCELLED;
    }
}
