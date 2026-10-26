package com.hls.schoolbilling.internal;

import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.schoolbilling.internal.ReceivablesDtos.AdjustmentView;
import com.hls.schoolbilling.internal.ReceivablesDtos.BillingCaller;
import com.hls.schoolbilling.internal.ReceivablesStore.DueRecord;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Adjustments (credit notes) for agreed deductions (spec 022 US4): append-only, corrected by a reversing entry. */
@Service
public class DueAdjustmentService {

    static final String ENTITY = "DUE_ADJUSTMENT";

    private final ReceivablesStore store;
    private final BillingScope scope;
    private final OverdueFollowUps followUps;
    private final ChangeRecorder changes;
    private final Clock clock;

    DueAdjustmentService(ReceivablesStore store, BillingScope scope, OverdueFollowUps followUps, ChangeRecorder changes, Clock clock) {
        this.store = store;
        this.scope = scope;
        this.followUps = followUps;
        this.changes = changes;
        this.clock = clock;
    }

    static String reason(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.length() < 3 || text.length() > DueService.REASON_MAX) {
            throw new InvalidInputException("A reason of 3 to " + DueService.REASON_MAX + " characters is required.");
        }
        return text;
    }

    static BigDecimal money(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidInputException("The amount must be more than zero.");
        }
        if (amount.scale() > 2 && amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidInputException("The amount can have at most two decimals.");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public AdjustmentView add(BillingCaller caller, UUID dueId, BigDecimal rawAmount, String rawReason) {
        DueRecord due = store.due(dueId).orElseThrow(() -> new NotFoundException("Monthly due not found."));
        scope.requireSchool(caller, due.schoolId());
        BigDecimal amount = money(rawAmount);
        String reason = reason(rawReason);
        store.lockSchool(due.schoolId());
        due = store.due(dueId).orElseThrow();
        if (due.cancelled()) {
            throw new ConflictException("This monthly due is cancelled.");
        }
        if (amount.compareTo(due.balance()) > 0) {
            throw new InvalidInputException("The adjustment is more than the balance of " + due.balance().toPlainString() + ".");
        }
        UUID id = UUID.randomUUID();
        store.insertAdjustment(id, dueId, amount, reason, null, caller.userId(), clock.instant());
        changes.recordLifecycle(caller.userId(), ENTITY, id, "created", due.reference() + " " + amount.toPlainString() + ": " + reason);
        followUps.closeIfSettled(dueId, caller.userId());
        return store.adjustment(id).orElseThrow();
    }

    @Transactional
    public AdjustmentView reverse(BillingCaller caller, UUID adjustmentId, String rawReason) {
        AdjustmentView original = store.adjustment(adjustmentId).orElseThrow(() -> new NotFoundException("Adjustment not found."));
        DueRecord due = store.due(original.dueId()).orElseThrow();
        scope.requireSchool(caller, due.schoolId());
        String reason = reason(rawReason);
        store.lockSchool(due.schoolId());
        original = store.adjustment(adjustmentId).orElseThrow();
        if (original.reversesId() != null) {
            throw new ConflictException("A reversal cannot be reversed. Add a new adjustment instead.");
        }
        if (original.reversed()) {
            throw new ConflictException("This adjustment is already reversed.");
        }
        if (due.cancelled()) {
            throw new ConflictException("This monthly due is cancelled.");
        }
        UUID id = UUID.randomUUID();
        store.insertAdjustment(id, original.dueId(), original.amount(), reason, original.id(), caller.userId(), clock.instant());
        changes.recordLifecycle(caller.userId(), ENTITY, id, "reversed", due.reference() + " reverses " + original.id() + ": " + reason);
        return store.adjustment(id).orElseThrow();
    }
}
