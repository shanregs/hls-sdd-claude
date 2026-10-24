package com.hls.notification.internal;

import com.hls.expenses.api.ExpenseDecided;
import com.hls.expenses.api.ExpenseSubmitted;
import com.hls.notification.api.NotificationType;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns the expense events of spec 015 into notifications: every active Admin and Director who may approve hears of a
 * submission (never the claimant), and the claimant hears of an approval, rejection or reversal. Runs inside the
 * publisher's transaction.
 */
@Component
public class ExpenseEventListener {

    private final NotificationService notifications;
    private final RecipientResolver recipients;

    public ExpenseEventListener(NotificationService notifications, RecipientResolver recipients) {
        this.notifications = notifications;
        this.recipients = recipients;
    }

    @EventListener
    void onSubmitted(ExpenseSubmitted event) {
        MessageFactory.Text text =
                MessageFactory.expenseSubmitted(event.claimId(), event.claimantName(), event.resubmitted());
        for (UUID user : recipients.expenseReviewers()) {
            if (!user.equals(event.claimantId())) {
                notifications.create(user, NotificationType.EXPENSE_SUBMITTED, text);
            }
        }
    }

    @EventListener
    void onDecided(ExpenseDecided event) {
        if ("REVERSED".equals(event.outcome())) {
            notifications.create(
                    event.claimantId(),
                    NotificationType.EXPENSE_REVERSED,
                    MessageFactory.expenseReversed(event.claimId(), event.reason()));
        } else {
            notifications.create(
                    event.claimantId(),
                    NotificationType.EXPENSE_DECIDED,
                    MessageFactory.expenseDecided(event.claimId(), event.outcome(), event.reason()));
        }
    }
}
