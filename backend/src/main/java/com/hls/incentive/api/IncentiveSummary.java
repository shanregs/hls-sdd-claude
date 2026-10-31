package com.hls.incentive.api;

import com.hls.identity.user.Role;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What the dashboard (spec 028) and later reports (spec 014) read about incentives, always within the caller's scope: the
 * Director and Admin see everything, a Zone Manager their Zone's Schools plus their own, any other Manager their own.
 */
public interface IncentiveSummary {

    /**
     * @param pending waiting for a decision
     * @param approvedAwaitingPayroll approved or held, not yet paid in a run
     * @param paidInMonth rupees paid in runs of the month asked for
     * @param noEarner Schools with an approved MoU and nobody to pay (Admin and Director only, else 0)
     */
    record Counts(long pending, long approvedAwaitingPayroll, BigDecimal paidInMonth, long noEarner) {}

    /** One incentive the caller may decide now. */
    record PendingItem(
            UUID incentiveId,
            UUID schoolId,
            String schoolName,
            UUID earnerUserId,
            String earnerName,
            BigDecimal amount,
            LocalDate closureDate) {}

    Counts counts(UUID userId, Set<Role> roles, YearMonth month);

    /** Pending incentives the caller may approve or reject (the Director's approvals inbox, a Zone Manager's own Schools). */
    List<PendingItem> pendingFor(UUID userId, Set<Role> roles);
}
