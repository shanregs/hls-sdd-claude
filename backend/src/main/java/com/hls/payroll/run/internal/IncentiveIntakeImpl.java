package com.hls.payroll.run.internal;

import com.hls.payroll.api.IncentiveIntake;
import com.hls.payroll.api.PayRules.Kind;
import com.hls.payroll.api.ReimbursementIntake.OfferResult;
import com.hls.payroll.internal.PersonDirectory;
import com.hls.school.api.ChangeRecorder;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates, withdraws and reports {@code INCENTIVE} adjustments for approved Manager incentives (spec 030, amendment A to
 * 013b). The adjustment rules stay where they were: a draft run takes a waiting adjustment at compute time, cancelling
 * frees it, approval applies it. Nothing here touches an approved run.
 */
@Service
class IncentiveIntakeImpl implements IncentiveIntake {

    private static final int MAX_REASON = 500;
    private static final int MAX_SOURCE = 100;
    private static final PayAdjustment.AdjustmentKind KIND = PayAdjustment.AdjustmentKind.INCENTIVE;

    private final PayAdjustmentRepository adjustments;
    private final PayRunRepository runs;
    private final AdjustmentService adjustmentService;
    private final PersonDirectory people;
    private final ChangeRecorder changes;
    private final Clock clock;

    IncentiveIntakeImpl(
            PayAdjustmentRepository adjustments,
            PayRunRepository runs,
            AdjustmentService adjustmentService,
            PersonDirectory people,
            ChangeRecorder changes,
            Clock clock) {
        this.adjustments = adjustments;
        this.runs = runs;
        this.adjustmentService = adjustmentService;
        this.people = people;
        this.changes = changes;
        this.clock = clock;
    }

    @Override
    @Transactional
    public OfferResult offer(UUID actor, UUID managerId, BigDecimal amount, String reason, String sourceRef) {
        if (sourceRef == null || sourceRef.isBlank() || sourceRef.length() > MAX_SOURCE) {
            return OfferResult.refused("The source reference is required and at most " + MAX_SOURCE + " characters.");
        }
        if (amount == null || amount.signum() <= 0) {
            return OfferResult.refused("The incentive must be more than zero.");
        }
        String name = managerId == null
                ? null
                : people.allManagers().stream()
                        .filter(m -> m.id().equals(managerId))
                        .map(PersonDirectory.ManagerSummary::name)
                        .findFirst()
                        .orElse(null);
        if (name == null) {
            return OfferResult.refused("This Manager is not on the payroll, so the incentive cannot be paid with salary.");
        }
        List<PayAdjustment> existing = adjustments.findBySourceRefAndKindOrderByCreatedAtDescIdDesc(sourceRef, KIND);
        if (!existing.isEmpty() && existing.get(0).getStatus() != PayAdjustment.Status.WITHDRAWN) {
            return OfferResult.ok();
        }
        String why = reason == null || reason.isBlank() ? "Manager incentive: closed School" : reason.trim();
        if (why.length() > MAX_REASON) {
            why = why.substring(0, MAX_REASON);
        }
        PayAdjustment adjustment = new PayAdjustment(
                Kind.MANAGER, managerId, name, KIND, amount.setScale(2), why, actor, clock.instant());
        adjustment.sourcedFrom(sourceRef);
        PayAdjustment saved = adjustments.save(adjustment);
        changes.recordLifecycle(actor, AdjustmentService.ENTITY, saved.getId(), "created", "INCENTIVE for " + name + ": " + why);
        return OfferResult.ok();
    }

    @Override
    @Transactional
    public boolean withdraw(UUID actor, String sourceRef, String reason) {
        List<PayAdjustment> existing = adjustments.findBySourceRefAndKindOrderByCreatedAtDescIdDesc(sourceRef, KIND);
        if (existing.isEmpty() || existing.get(0).getStatus() == PayAdjustment.Status.WITHDRAWN) {
            return true;
        }
        PayAdjustment adjustment = existing.get(0);
        OfferStatus status = statusOf(adjustment);
        if (status == OfferStatus.APPLIED || status == OfferStatus.HELD_AWAITING_APPROVAL) {
            return false;
        }
        adjustmentService.withdraw(
                actor, adjustment.getId(), reason == null || reason.isBlank() ? "Incentive no longer payable" : reason);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public OfferStatus status(String sourceRef) {
        List<PayAdjustment> existing = adjustments.findBySourceRefAndKindOrderByCreatedAtDescIdDesc(sourceRef, KIND);
        return existing.isEmpty() ? OfferStatus.NONE : statusOf(existing.get(0));
    }

    private OfferStatus statusOf(PayAdjustment adjustment) {
        return IntakeStatus.of(adjustment, runs, OfferStatus.class);
    }
}
