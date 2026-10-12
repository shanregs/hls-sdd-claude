package com.hls.tasks.internal;

import com.hls.recruitment.api.FollowUpScheduled;
import com.hls.tasks.api.TaskCreation;
import com.hls.tasks.api.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns the follow-up date recorded on a marketing visit (spec 023, contract C2) into a Follow-up task for the prospect's
 * owner, once per activity. It runs after the visit is saved, in its own transaction, so a problem here (for example an
 * owner who is not a Manager) is logged and can never stop the visit from being completed.
 */
@Component
class FollowUpListener {

    static final String SOURCE = "MARKETING_FOLLOW_UP";

    private static final Logger log = LoggerFactory.getLogger(FollowUpListener.class);

    private final TaskService tasks;
    private final TransactionTemplate separate;

    FollowUpListener(TaskService tasks, PlatformTransactionManager transactions) {
        this.tasks = tasks;
        this.separate = new TransactionTemplate(transactions);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onFollowUp(FollowUpScheduled event) {
        if (event.ownerUserId() == null) {
            return;
        }
        try {
            separate.executeWithoutResult(status -> tasks.createFollowUp(new TaskCreation.FollowUpRequest(
                    TaskType.FOLLOW_UP,
                    event.title() == null || event.title().trim().length() < 3 ? "Follow up with the prospect" : event.title(),
                    null,
                    event.ownerUserId(),
                    event.dueOn(),
                    null,
                    "PROSPECT",
                    event.prospectId(),
                    null,
                    null,
                    SOURCE,
                    event.activityId().toString(),
                    null)));
        } catch (RuntimeException e) {
            log.warn("No follow-up task for activity {}: {}", event.activityId(), e.getMessage());
        }
    }
}
