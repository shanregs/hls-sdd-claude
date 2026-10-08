package com.hls.teacher.api;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only lookups of Teachers and their placements for later modules (spec 008 research.md
 * section 1). Bulk-oriented: callers pass id sets so a grid page needs one query, not one per Teacher.
 */
public interface TeacherDirectory {

    record TeacherInfo(UUID id, String name, String status, UUID userId) {}

    /** An ACTIVE placement; {@code endsOn} is inclusive and null while open-ended. */
    record PlacementSpan(UUID teacherId, UUID schoolId, LocalDate startsOn, LocalDate endsOn) {

        public boolean covers(LocalDate date) {
            return !date.isBefore(startsOn) && (endsOn == null || !date.isAfter(endsOn));
        }
    }

    Map<UUID, TeacherInfo> teacherInfo(Collection<UUID> teacherIds);

    Optional<TeacherInfo> teacherOfUser(UUID userId);

    /** ACTIVE placements of the given Teachers overlapping {@code from..to} inclusive. */
    List<PlacementSpan> placementsOverlapping(Collection<UUID> teacherIds, LocalDate from, LocalDate to);

    /** Ids of Teachers with an ACTIVE placement overlapping {@code from..to} inclusive. */
    Set<UUID> teachersPlacedDuring(LocalDate from, LocalDate to);

    /** A Teacher's designation and optional employee id (spec 005a); either may be null. */
    record TeacherEmployment(UUID teacherId, UUID designationId, String employeeId) {}

    /** The Teacher's current designation id; empty when none is set. Only the current one is kept. */
    Optional<UUID> currentDesignation(UUID teacherId);

    /** Bulk form of {@link #currentDesignation}; a Teacher with none is absent from the map. */
    Map<UUID, UUID> currentDesignations(Collection<UUID> teacherIds);

    /** Designation and employee id of the given Teachers (unknown ids absent). */
    Map<UUID, TeacherEmployment> employment(Collection<UUID> teacherIds);

    /** For each given designation, how many Teachers hold it (absent key means none). */
    Map<UUID, Long> holderCountsByDesignation(Collection<UUID> designationIds);
}
