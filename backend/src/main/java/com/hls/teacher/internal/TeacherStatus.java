package com.hls.teacher.internal;

import java.util.EnumSet;
import java.util.Set;

/**
 * The Teacher status machine (spec 005 clarification Q3): IN_TRAINING to ACTIVE, ACTIVE and ON_LEAVE
 * both ways, and any status to EXITED. Exit is final.
 */
public enum TeacherStatus {
    IN_TRAINING,
    ACTIVE,
    ON_LEAVE,
    EXITED;

    public Set<TeacherStatus> allowedNext() {
        return switch (this) {
            case IN_TRAINING -> EnumSet.of(ACTIVE, EXITED);
            case ACTIVE -> EnumSet.of(ON_LEAVE, EXITED);
            case ON_LEAVE -> EnumSet.of(ACTIVE, EXITED);
            case EXITED -> EnumSet.noneOf(TeacherStatus.class);
        };
    }

    public boolean canMoveTo(TeacherStatus next) {
        return allowedNext().contains(next);
    }
}
