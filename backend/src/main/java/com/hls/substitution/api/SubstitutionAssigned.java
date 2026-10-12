package com.hls.substitution.api;

import java.time.LocalDate;
import java.util.UUID;

/** A substitute was assigned to a requirement; tells the substitute (spec 017a FR-011). */
public record SubstitutionAssigned(
        UUID requirementId, UUID substituteTeacherId, UUID schoolId, String schoolName, LocalDate date) {}
