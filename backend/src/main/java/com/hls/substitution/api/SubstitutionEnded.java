package com.hls.substitution.api;

import java.time.LocalDate;
import java.util.UUID;

/** A substitute's assignment ended (changed, released, covered by the Manager, or the absence was withdrawn). */
public record SubstitutionEnded(
        UUID requirementId, UUID substituteTeacherId, UUID schoolId, String schoolName, LocalDate date, String reason) {}
