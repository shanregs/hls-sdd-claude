package com.hls.incentive.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The shapes the incentive endpoints return and accept (spec 030 contracts/incentive-api.md). */
public final class IncentiveDtos {

    private IncentiveDtos() {}

    /** One incentive as a list row or at the top of the detail. {@code actions} holds approve, reject and reopen only when allowed. */
    public record Row(
            UUID id,
            UUID schoolId,
            String schoolName,
            String zoneName,
            UUID earnerManagerId,
            String earnerName,
            UUID contractId,
            LocalDate closureDate,
            BigDecimal amount,
            String status,
            boolean mouChanged,
            String holdReason,
            UUID decidedById,
            String decidedBy,
            Instant decidedAt,
            boolean approvedByEarner,
            String rejectReason,
            LocalDate runMonth,
            String payrollStatus,
            Long version,
            List<String> actions) {}

    public record HistoryEntry(String kind, String actor, String detail, Instant at) {}

    public record Detail(Row incentive, List<HistoryEntry> history) {}

    /** Body of approve, reject and reopen. */
    public record DecisionRequest(Long version, String reason) {}

    public record StatusTotal(String status, long count, BigDecimal amount) {}

    public record ManagerMonthTotal(UUID managerId, String managerName, String month, long count, BigDecimal amount) {}

    public record Summary(List<StatusTotal> byStatus, List<ManagerMonthTotal> byManagerMonth, BigDecimal total) {}

    public record NoEarnerRow(
            UUID id,
            UUID schoolId,
            String schoolName,
            String zoneName,
            UUID contractId,
            String reason,
            String reasonText,
            String detail,
            boolean resolved,
            Instant createdAt) {}
}
