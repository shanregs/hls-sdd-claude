package com.hls.substitution.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One day a substitute covered for an absent Teacher, credited to the substitute (spec 017a FR-009). The absent Teacher
 * never has a credit for the day.
 *
 * @param dayValue 0.50 or 1.00
 */
public record SubstituteCredit(
        UUID requirementId,
        UUID substituteTeacherId,
        UUID absentTeacherId,
        UUID schoolId,
        LocalDate date,
        BigDecimal dayValue) {}
