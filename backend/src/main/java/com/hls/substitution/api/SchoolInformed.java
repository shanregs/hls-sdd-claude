package com.hls.substitution.api;

import java.time.LocalDate;
import java.util.UUID;

/** Someone recorded that the School was informed; tells the School's Manager when somebody else did it. */
public record SchoolInformed(
        UUID requirementId, UUID schoolId, String schoolName, LocalDate date, UUID informedByUserId) {}
