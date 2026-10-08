package com.hls.organization.api;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
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
        Employment employment,
        @JsonIgnore Map<String, Object> extras) {

    public record ZoneRef(UUID id, String name) {}

    /** One dated assignment row (Zone or School) kept for history. */
    public record AssignmentRow(String kind, UUID targetId, String targetName, LocalDate startsOn, LocalDate endsOn) {}

    /** The designation a Manager holds (null when none is in effect today). */
    public record DesignationRef(UUID id, String name, boolean retired) {}

    /** One row of the Manager's designation history, newest first. */
    public record DesignationRow(UUID designationId, String name, LocalDate effectiveOn, Instant recordedAt) {}

    /**
     * Employment details (spec 005a). {@code history} is filled only on the detail endpoint; {@code missing} holds
     * the codes DESIGNATION, JOINING_DATE and EXIT_DATE for what is not recorded.
     */
    public record Employment(
            String employeeId,
            LocalDate joiningDate,
            LocalDate exitDate,
            DesignationRef designation,
            List<DesignationRow> history,
            List<String> missing) {}

    @JsonAnyGetter
    public Map<String, Object> any() {
        return extras;
    }
}
