package com.hls.payroll.run.internal;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.hls.payroll.api.PayRules.Kind;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** What the payroll run endpoints return (contracts/payroll-run-api.md). Money is a two-place decimal string. */
public final class RunViews {

    private RunViews() {}

    public record Person(UUID id, String name) {}

    public record Totals(String earned, String deduction, String adjustments, String net) {}

    public record EventView(Instant at, Person actor, String kind, String reason, @JsonRawValue String detail) {}

    /** The run header. {@code events} and {@code recomputeSummary} are only filled on the single-run read. */
    public record RunView(
            UUID id,
            String month,
            PayRun.Kind kind,
            int sequence,
            PayRun.Status status,
            PayRun.ComputeState computeState,
            /** True while the run says it is computing but its heartbeat is older than ten minutes: Compute again is offered. */
            boolean stalled,
            int progressDone,
            int progressTotal,
            Totals totals,
            int payableCount,
            int cannotPayCount,
            int paidCount,
            int unpaidCount,
            String paidAmount,
            String unpaidAmount,
            Person startedBy,
            Instant startedAt,
            Person submittedBy,
            Instant submittedAt,
            Person approvedBy,
            Instant approvedAt,
            Person cancelledBy,
            Instant cancelledAt,
            String cancelReason,
            @JsonRawValue String policy,
            long version,
            List<EventView> events,
            @JsonRawValue String recomputeSummary,
            /** Waiting adjustments recorded after this draft was computed; they join when it is computed again. */
            long adjustmentsWaitingSinceComputed) {}

    public record AdjustmentLine(UUID adjustmentId, String kind, String amount, String reason) {}

    /** One line. A person who cannot be paid has a reason and {@code null} amounts, never zero. */
    public record LineView(
            UUID id,
            Kind personKind,
            UUID personId,
            String name,
            String label,
            PersonResult.State state,
            String reason,
            Integer payableDays,
            String workingDays,
            String unpaidDays,
            String salary,
            String earned,
            String deduction,
            String adjustments,
            String net,
            String warning,
            Instant computedAt,
            @JsonRawValue String working,
            List<AdjustmentLine> adjustmentLines,
            /** The newest payment entry of the line, or null when none was recorded. */
            PaymentService.EntryView payment) {}
}
