package com.hls.expenses.internal;

import com.hls.payroll.api.PayrollAdjustmentApplied;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Settles a claim when an approved pay run applies its reimbursement (spec 015 FR-018). Runs inside the pay run approval
 * transaction and is idempotent per claim, so a repeat of the event changes nothing.
 */
@Component
class PayrollSettlementListener {

    private final ExpensePaymentService payments;

    PayrollSettlementListener(ExpensePaymentService payments) {
        this.payments = payments;
    }

    @EventListener
    void onApplied(PayrollAdjustmentApplied event) {
        if ("REIMBURSEMENT".equals(event.kind()) && event.sourceRef() != null) {
            payments.settleWithPayroll(event.sourceRef(), event.runId(), event.month());
        }
    }
}
