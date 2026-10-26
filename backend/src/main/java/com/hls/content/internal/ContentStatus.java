package com.hls.content.internal;

import java.util.EnumSet;
import java.util.Set;

/** Where a content piece is in its life (spec 029 FR-007). */
public enum ContentStatus {
    DRAFT("Draft"),
    ASSIGNED("Assigned"),
    SUBMITTED("Submitted"),
    APPROVED("Approved"),
    RETURNED("Returned");

    /** The states in which the author can still prepare and submit a version. */
    public static final Set<ContentStatus> OPEN_FOR_SUBMISSION = EnumSet.of(DRAFT, ASSIGNED, RETURNED, APPROVED);

    /** The states that count as overdue once the due date has passed: nothing has been delivered for review. */
    public static final Set<ContentStatus> UNDELIVERED = EnumSet.of(DRAFT, ASSIGNED, RETURNED);

    private final String label;

    ContentStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
