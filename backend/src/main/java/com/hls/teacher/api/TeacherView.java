package com.hls.teacher.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A Teacher as shown on lists and detail screens. There is deliberately no salary member: salary is
 * reachable only through its own permission-guarded endpoints (spec 005 FR-019).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TeacherView(
        UUID id,
        String name,
        String phone,
        String email,
        String address,
        String status,
        LocalDate statusEffectiveOn,
        List<String> allowedNextStatuses,
        UUID userId,
        Long version,
        SchoolRef school,
        ManagerRef manager,
        PendingPlacement pendingPlacement,
        List<PlacementRow> placements) {

    public record SchoolRef(UUID id, String name) {}

    public record ManagerRef(UUID id, String displayName) {}

    public record PendingPlacement(UUID schoolId, String schoolName, LocalDate startsOn) {}

    /**
     * One assignment row of the Teacher; {@code positionNumber} is the position of the School's contract the
     * Teacher fills, or null while the assignment is not mapped to one (spec 012).
     */
    public record PlacementRow(
            UUID schoolId,
            String schoolName,
            LocalDate startsOn,
            LocalDate endsOn,
            String status,
            Integer positionNumber) {}
}
