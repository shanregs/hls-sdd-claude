package com.hls.school.api.dto;

import java.util.UUID;

/**
 * FR-006. One of two states for a given School identifier's current Zone —
 * same "answer" pattern {@code organization.api.dto.AccountabilityAnswer}
 * already established for "current manager or unassigned."
 */
public record SchoolZoneAnswer(State state, UUID zoneId) {

    public enum State {
        CURRENT_ZONE,
        UNASSIGNED
    }

    public static SchoolZoneAnswer currentZone(UUID zoneId) {
        return new SchoolZoneAnswer(State.CURRENT_ZONE, zoneId);
    }

    public static SchoolZoneAnswer unassigned() {
        return new SchoolZoneAnswer(State.UNASSIGNED, null);
    }
}
