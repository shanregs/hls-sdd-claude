package com.hls.notification.internal;

import com.hls.notification.api.NotificationType;
import com.hls.payroll.api.PayrollRunApproved;
import com.hls.payroll.api.PayrollRunReturned;
import com.hls.payroll.api.PayrollRunSubmitted;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns the pay run events of spec 013b into notifications: everyone who may approve hears of a submission, and the
 * submitter hears of an approval or a return (with the reason). A message holds the month and the status, never an
 * amount. Runs inside the publisher's transaction.
 */
@Component
public class PayrollRunEventListener {

    private final NotificationService notifications;
    private final RecipientResolver recipients;

    public PayrollRunEventListener(NotificationService notifications, RecipientResolver recipients) {
        this.notifications = notifications;
        this.recipients = recipients;
    }

    @EventListener
    void onSubmitted(PayrollRunSubmitted event) {
        MessageFactory.Text text = MessageFactory.payrollRunSubmitted(event.runId(), event.month());
        for (UUID user : recipients.payrollApprovers()) {
            if (!user.equals(event.submittedBy())) {
                notifications.create(user, NotificationType.PAYROLL_RUN_SUBMITTED, text);
            }
        }
    }

    @EventListener
    void onApproved(PayrollRunApproved event) {
        if (event.submittedBy() != null) {
            notifications.create(
                    event.submittedBy(),
                    NotificationType.PAYROLL_RUN_APPROVED,
                    MessageFactory.payrollRunApproved(event.runId(), event.month()));
        }
    }

    @EventListener
    void onReturned(PayrollRunReturned event) {
        if (event.submittedBy() != null) {
            notifications.create(
                    event.submittedBy(),
                    NotificationType.PAYROLL_RUN_RETURNED,
                    MessageFactory.payrollRunReturned(event.runId(), event.month(), event.reason()));
        }
    }
}
