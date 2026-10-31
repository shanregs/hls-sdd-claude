package com.hls.notification.internal;

import com.hls.notification.internal.MessageFactory.Text;
import java.util.UUID;

/** The texts of the incentive notifications (spec 030). Pure text, kept apart from {@link MessageFactory} so the two specs that edit notifications do not collide. */
final class IncentiveMessages {

    private IncentiveMessages() {}

    static Text pending(String school, String earner, UUID incentiveId) {
        return new Text(
                MessageFactory.shorten("Incentive to approve: " + school, MessageFactory.MAX_TITLE),
                MessageFactory.shorten(earner + " earned an incentive for closing " + school + ". It waits for your decision.", MessageFactory.MAX_MESSAGE),
                "/operations/incentives/" + incentiveId);
    }

    static Text decided(String school, String outcome, String reason) {
        boolean approved = "APPROVED".equals(outcome);
        String why = !approved && reason != null && !reason.isBlank()
                ? ": " + MessageFactory.shorten(reason.trim(), MessageFactory.MAX_REASON)
                : "";
        return new Text(
                MessageFactory.shorten((approved ? "Incentive approved: " : "Incentive rejected: ") + school, MessageFactory.MAX_TITLE),
                MessageFactory.shorten(
                        "Your incentive for " + school + (approved ? " was approved and goes into payroll." : " was rejected" + why + "."),
                        MessageFactory.MAX_MESSAGE),
                "/operations/incentives");
    }

    static Text mouChanged(String school, String earner, UUID incentiveId) {
        return new Text(
                MessageFactory.shorten("MoU changed after incentive approval: " + school, MessageFactory.MAX_TITLE),
                MessageFactory.shorten(
                        "The MoU of " + school + " no longer counts as approved, but the incentive of " + earner
                                + " is in a pay run awaiting approval. Return the run to hold it back, or approve it knowingly.",
                        MessageFactory.MAX_MESSAGE),
                "/operations/incentives/" + incentiveId);
    }
}
