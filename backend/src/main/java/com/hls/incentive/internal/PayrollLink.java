package com.hls.incentive.internal;

import com.hls.payroll.api.IncentiveIntake;
import com.hls.payroll.api.IncentiveIntake.OfferStatus;
import com.hls.payroll.api.ReimbursementIntake.OfferResult;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The one place the incentive module talks to payroll (spec 030 US3, research D-8): offer an approved incentive for the
 * next pay run, take it back when its MoU stops counting, and say in words where it is. The offer is idempotent per source
 * reference, so retries and the daily job can never pay twice.
 */
@Component
class PayrollLink {

    private final IncentiveIntake intake;
    private final SchoolDirectory schools;
    private final IncentiveRecorder recorder;

    PayrollLink(IncentiveIntake intake, SchoolDirectory schools, IncentiveRecorder recorder) {
        this.intake = intake;
        this.schools = schools;
        this.recorder = recorder;
    }

    /** Offers the incentive to payroll. A refusal is recorded and leaves the incentive APPROVED; the job tries again. */
    boolean offer(UUID actor, Incentive incentive) {
        String school = schools.school(incentive.getSchoolId()).map(SchoolInfo::name).orElse("a School");
        OfferResult result = intake.offer(
                actor,
                incentive.getEarnerManagerId(),
                incentive.getAmount(),
                "Manager incentive: closed School " + school,
                incentive.sourceRef());
        if (result.accepted()) {
            recorder.record(actor, incentive.getId(), "OFFERED", "Offered to payroll");
        } else {
            recorder.record(actor, incentive.getId(), "OFFER_REFUSED", result.refusal());
        }
        return result.accepted();
    }

    /** true when nothing is left to pay; false when the run awaits approval or already applied it. */
    boolean withdraw(UUID actor, Incentive incentive, String reason) {
        return intake.withdraw(actor, incentive.sourceRef(), reason);
    }

    OfferStatus status(Incentive incentive) {
        return intake.status(incentive.sourceRef());
    }

    /** What a person reads beside an approved, held or paid incentive; null for the others. */
    String text(Incentive incentive) {
        switch (incentive.getStatus()) {
            case APPROVED, HELD, PAID_IN_RUN -> {
                return text(incentive.getStatus(), status(incentive));
            }
            default -> {
                return null;
            }
        }
    }

    static String text(IncentiveStatus incentive, OfferStatus payroll) {
        if (incentive == IncentiveStatus.PAID_IN_RUN) {
            return "Paid in a pay run";
        }
        if (incentive == IncentiveStatus.HELD) {
            return "Held back from payroll";
        }
        return switch (payroll) {
            case NONE -> "Not with payroll yet; it will be offered again";
            case WAITING -> "Waiting for the next pay run";
            case HELD_BY_DRAFT -> "In the draft pay run";
            case HELD_AWAITING_APPROVAL -> "In a pay run awaiting approval";
            case APPLIED -> "Paid in a pay run";
            case WITHDRAWN -> "Withdrawn from payroll";
        };
    }
}
