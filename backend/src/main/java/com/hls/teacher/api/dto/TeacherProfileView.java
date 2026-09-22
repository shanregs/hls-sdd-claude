package com.hls.teacher.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** FR-006/007/008. The read-side shape of one Teacher Profile. No bank-detail fields (out of scope). */
public record TeacherProfileView(
        UUID id,
        String name,
        String phone,
        String email,
        BigDecimal hlsOfferedSalary,
        TeacherStatus status,
        Instant createdAt) {
}
