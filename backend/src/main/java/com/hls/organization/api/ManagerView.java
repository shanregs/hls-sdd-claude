package com.hls.organization.api;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A Manager with their Zones; {@code history} is filled only on the detail endpoint. */
public record ManagerView(
        UUID id,
        UUID userId,
        String displayName,
        String phone,
        boolean active,
        Long version,
        List<ZoneRef> zones,
        long schoolCount,
        List<AssignmentRow> history,
        @JsonIgnore Map<String, Object> extras) {

    public record ZoneRef(UUID id, String name) {}

    /** One dated assignment row (Zone or School) kept for history. */
    public record AssignmentRow(String kind, UUID targetId, String targetName, LocalDate startsOn, LocalDate endsOn) {}

    @JsonAnyGetter
    public Map<String, Object> any() {
        return extras;
    }
}
