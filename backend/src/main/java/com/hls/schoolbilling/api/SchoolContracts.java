package com.hls.schoolbilling.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only access to School contracts for specs 013 (payroll) and 022 (billing), so they never read the
 * module's tables (Constitution Principle VII). A Teacher whose assignment sits on a contract that no longer
 * covers the date is reported as unmapped, never as filling a position.
 */
public interface SchoolContracts {

    /** The live contract of the School whose dates include {@code date}, with its positions. */
    Optional<ContractView> contractOf(UUID schoolId, LocalDate date);

    /** The position the Teacher fills on {@code date} and its salary; empty while the Teacher is unmapped. */
    Optional<TeacherPosition> positionOf(UUID teacherId, LocalDate date);

    /** Live contracts of the School overlapping {@code from..to} inclusive, oldest first. */
    List<ContractView> contractsOverlapping(UUID schoolId, LocalDate from, LocalDate to);

    /** Positions, filled and vacant of the School's contract in effect on {@code date}; all zero when there is none. */
    Occupancy occupancyOf(UUID schoolId, LocalDate date);

    /** {@link #occupancyOf} for many Schools at once, in a fixed number of queries; every given id has an entry. */
    java.util.Map<UUID, Occupancy> occupancyOfAll(java.util.Collection<UUID> schoolIds, LocalDate date);

    /** Teachers at the School in {@code from..to} who are not mapped to a position of a contract in effect. */
    Set<UUID> unmappedTeachers(UUID schoolId, LocalDate from, LocalDate to);
}
