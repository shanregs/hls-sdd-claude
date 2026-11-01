package com.hls.payroll.run.internal;

import com.hls.payroll.api.CorrectionRaising;
import com.hls.payroll.api.PayRules.Kind;
import com.hls.payroll.internal.PersonDirectory;
import com.hls.school.api.ChangeRecorder;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates and reports {@code CORRECTION} adjustments for other modules (spec 017b). The adjustment rules stay where
 * they were: a draft run takes a waiting adjustment at compute time, cancelling frees it, approval applies it.
 */
@Service
class CorrectionRaisingImpl implements CorrectionRaising {

    private static final int MAX_REASON = 500;
    private static final int MAX_SOURCE = 100;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999.99");

    private final PayAdjustmentRepository adjustments;
    private final PayRunRepository runs;
    private final PersonDirectory people;
    private final ChangeRecorder changes;
    private final Clock clock;

    CorrectionRaisingImpl(
            PayAdjustmentRepository adjustments,
            PayRunRepository runs,
            PersonDirectory people,
            ChangeRecorder changes,
            Clock clock) {
        this.adjustments = adjustments;
        this.runs = runs;
        this.people = people;
        this.changes = changes;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result raiseForTeacher(UUID actor, UUID teacherId, BigDecimal amount, String reason, String sourceRef) {
        if (sourceRef == null || sourceRef.isBlank() || sourceRef.length() > MAX_SOURCE) {
            return Result.refused("The source reference is required and at most " + MAX_SOURCE + " characters.");
        }
        if (amount == null || amount.signum() == 0) {
            return Result.refused("The Correction cannot be zero.");
        }
        if (amount.stripTrailingZeros().scale() > 2 || amount.abs().compareTo(MAX_AMOUNT) > 0) {
            return Result.refused("The Correction can have at most two decimals and be at most " + MAX_AMOUNT.toPlainString() + ".");
        }
        String name = people.teacherNames(Set.of(teacherId)).get(teacherId);
        if (name == null) {
            return Result.refused("No such Teacher on the payroll.");
        }
        List<PayAdjustment> existing =
                adjustments.findBySourceRefAndKindOrderByCreatedAtDescIdDesc(sourceRef, PayAdjustment.AdjustmentKind.CORRECTION);
        if (!existing.isEmpty() && existing.get(0).getStatus() != PayAdjustment.Status.WITHDRAWN) {
            return Result.ok();
        }
        String why = reason == null || reason.isBlank() ? "Substitute pay correction" : reason.trim();
        if (why.length() > MAX_REASON) {
            why = why.substring(0, MAX_REASON);
        }
        PayAdjustment adjustment = new PayAdjustment(
                Kind.TEACHER,
                teacherId,
                name,
                PayAdjustment.AdjustmentKind.CORRECTION,
                amount.setScale(2),
                why,
                actor,
                clock.instant());
        adjustment.sourcedFrom(sourceRef);
        PayAdjustment saved = adjustments.save(adjustment);
        changes.recordLifecycle(actor, AdjustmentService.ENTITY, saved.getId(), "created", "CORRECTION for " + name + ": " + why);
        return Result.ok();
    }

    @Override
    @Transactional(readOnly = true)
    public Status status(String sourceRef) {
        List<PayAdjustment> existing =
                adjustments.findBySourceRefAndKindOrderByCreatedAtDescIdDesc(sourceRef, PayAdjustment.AdjustmentKind.CORRECTION);
        return existing.isEmpty() ? Status.NONE : IntakeStatus.of(existing.get(0), runs, Status.class);
    }
}
