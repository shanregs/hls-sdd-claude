package com.hls.attendance.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One Teacher's current mark on one date. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record MarkView(
        LocalDate date,
        String code,
        String codeName,
        String category,
        BigDecimal dayValue,
        UUID schoolId,
        String schoolName,
        String setByKind,
        UUID setByUserId,
        String setByName,
        Instant setAt,
        String note,
        Long version,
        UUID leaveRequestId,
        boolean lossOfPay,
        PrincipalConfirmation principalConfirmation) {

    /** The optional Principal confirmation of the day (amendment A2); null in the view when none is recorded. */
    public record PrincipalConfirmation(
            boolean confirmed, String principalName, String note, UUID recordedBy, String recordedByName, Instant recordedAt) {}
}
