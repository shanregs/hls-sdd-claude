package com.hls.schoolbilling.api;

import java.math.BigDecimal;
import java.util.UUID;

/** The position a Teacher fills on a date, and the salary the School pays for it. */
public record TeacherPosition(UUID contractId, UUID positionId, int number, UUID schoolId, BigDecimal salary) {}
