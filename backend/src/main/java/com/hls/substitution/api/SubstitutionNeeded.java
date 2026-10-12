package com.hls.substitution.api;

import java.time.LocalDate;
import java.util.UUID;

/** A requirement was raised or reopened; tells the School's Manager (spec 017a FR-006). */
public record SubstitutionNeeded(
        UUID requirementId, UUID schoolId, String schoolName, UUID teacherId, String teacherName, LocalDate date) {}
