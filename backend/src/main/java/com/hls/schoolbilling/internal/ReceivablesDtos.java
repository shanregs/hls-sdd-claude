package com.hls.schoolbilling.internal;

import com.hls.identity.user.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Request and response shapes of the School Billing API (contracts/receivables-api.md). */
public final class ReceivablesDtos {

    private ReceivablesDtos() {}

    /** The signed-in user and their roles. */
    public record BillingCaller(UUID userId, Set<Role> roles) {}

    /** One School-month line of the ledger with its derived status. */
    public record DueRow(
            UUID id,
            String reference,
            UUID schoolId,
            String schoolName,
            String month,
            LocalDate raisedOn,
            LocalDate dueOn,
            BigDecimal total,
            BigDecimal adjustments,
            BigDecimal collected,
            BigDecimal balance,
            String status,
            boolean overdue,
            long daysOverdue,
            String cancelReason,
            Instant cancelledAt) {}

    public record DueLineView(
            UUID id,
            UUID teacherId,
            String teacherName,
            Integer positionNumber,
            BigDecimal salary,
            LocalDate spanFrom,
            LocalDate spanTo,
            BigDecimal workedDays,
            BigDecimal monthDays,
            boolean fullMonth,
            BigDecimal amount,
            String working) {}

    public record AdjustmentView(
            UUID id, UUID dueId, BigDecimal amount, String reason, UUID reversesId, boolean reversed, Instant createdAt) {}

    public record AllocationView(UUID id, UUID collectionId, UUID dueId, String dueReference, BigDecimal amount, Instant createdAt) {}

    public record CollectionView(
            UUID id,
            UUID schoolId,
            BigDecimal amount,
            LocalDate receivedOn,
            String mode,
            String reference,
            String receiver,
            String comment,
            UUID reversesId,
            boolean reversed,
            String reason,
            BigDecimal allocated,
            BigDecimal unallocated,
            Instant createdAt) {}

    public record DueDetail(
            DueRow due,
            List<DueLineView> lines,
            List<AdjustmentView> adjustmentEntries,
            List<AllocationView> allocations,
            UUID followUpTaskId) {}

    public record NotBilledRow(UUID schoolId, String schoolName, UUID teacherId, String teacherName, String reason) {}

    public record RaiseResult(List<DueRow> raised, List<Skipped> skipped, List<NotBilledRow> notBilled) {}

    public record Skipped(UUID schoolId, String schoolName, String reason, UUID existingDueId) {}

    public record SchoolLedger(
            UUID schoolId,
            String schoolName,
            BigDecimal owed,
            BigDecimal collected,
            BigDecimal outstanding,
            BigDecimal overdue,
            BigDecimal advanceCredit,
            List<DueRow> dues,
            List<CollectionView> collections,
            List<AdjustmentView> adjustments) {}

    public record OverviewRow(
            UUID schoolId,
            String schoolName,
            UUID zoneId,
            String zoneName,
            UUID managerId,
            String managerName,
            BigDecimal owed,
            BigDecimal collected,
            BigDecimal outstanding,
            BigDecimal overdue,
            BigDecimal advanceCredit) {}

    public record Overview(
            String month,
            BigDecimal owed,
            BigDecimal collected,
            BigDecimal outstanding,
            BigDecimal overdue,
            BigDecimal advanceCredit,
            List<OverviewRow> schools) {}

    public record AgeingRow(
            UUID schoolId,
            String schoolName,
            UUID zoneId,
            String zoneName,
            UUID managerId,
            String managerName,
            BigDecimal notYetDue,
            BigDecimal b0to30,
            BigDecimal b31to60,
            BigDecimal b61to90,
            BigDecimal over90,
            BigDecimal total) {}

    public record Ageing(LocalDate asOf, AgeingRow totals, List<AgeingRow> schools, List<AgeingRow> zones, List<AgeingRow> managers) {}

    public record SettingsView(int paymentDueDays, int escalationDays) {}

    // request bodies
    public record RaiseBody(YearMonth month, List<UUID> schoolIds) {}

    public record CancelBody(String reason) {}

    public record AdjustmentBody(BigDecimal amount, String reason) {}

    public record ReasonBody(String reason) {}

    public record AllocationBody(UUID dueId, BigDecimal amount) {}

    public record CollectionBody(
            UUID schoolId,
            BigDecimal amount,
            LocalDate receivedOn,
            String mode,
            String reference,
            String receiver,
            String comment,
            List<AllocationBody> allocations,
            Boolean confirmDuplicate) {}

    public record AllocateBody(List<AllocationBody> allocations) {}

    public record SettingsBody(Integer paymentDueDays, Integer escalationDays) {}
}
