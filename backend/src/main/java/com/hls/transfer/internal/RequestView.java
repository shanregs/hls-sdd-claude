package com.hls.transfer.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A request as the API returns it (contracts/transfer-api.md "Request object"). */
public record RequestView(
        UUID id,
        String kind,
        String status,
        String statusNote,
        Ref teacher,
        Ref fromSchool,
        Ref toSchool,
        PositionRef position,
        Ref replacementTeacher,
        Ref outgoingDestinationSchool,
        LocalDate effectiveOn,
        String reasonCategory,
        String note,
        Ref requestedBy,
        Instant requestedAt,
        Ref decidedBy,
        Instant decidedAt,
        String decisionReason,
        long version,
        List<String> actions,
        List<CheckView> checks,
        List<HistoryView> history) {

    public record Ref(UUID id, String name) {}

    public record PositionRef(UUID id, Integer number) {}

    public record CheckView(boolean ok, String text) {}

    public record HistoryView(String action, String fromStatus, String toStatus, String actor, String note, Instant at) {}
}
