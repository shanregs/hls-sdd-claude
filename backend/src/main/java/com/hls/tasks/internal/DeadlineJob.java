package com.hls.tasks.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.tasks.api.TaskEscalated;
import com.hls.tasks.api.TaskOverdue;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The daily check for open tasks past their due date (spec 025 FR-009). It tells the Manager once per task and due date,
 * and escalates once to the Directors when the task has stayed open for its escalation days. Both markers live on the
 * task, so running the check any number of times changes nothing the second time.
 */
@Component
public class DeadlineJob {

    private static final Logger log = LoggerFactory.getLogger(DeadlineJob.class);

    /** What one run did. */
    public record Result(int overdueNotices, int escalations) {}

    private final TaskRepository tasks;
    private final TaskRecorder recorder;
    private final ApplicationEventPublisher events;
    private final BusinessDate businessDate;
    private final Clock clock;
    private final boolean enabled;
    private final TransactionTemplate tx;

    DeadlineJob(
            TaskRepository tasks,
            TaskRecorder recorder,
            ApplicationEventPublisher events,
            BusinessDate businessDate,
            Clock clock,
            PlatformTransactionManager transactions,
            @Value("${hls.tasks.deadlines.enabled:true}") boolean enabled) {
        this.tasks = tasks;
        this.recorder = recorder;
        this.events = events;
        this.businessDate = businessDate;
        this.clock = clock;
        this.enabled = enabled;
        this.tx = new TransactionTemplate(transactions);
    }

    @Scheduled(cron = "0 30 6 * * *", zone = "${hls.business-timezone:Asia/Kolkata}")
    void scheduled() {
        if (enabled) {
            Result result = tx.execute(status -> runNow());
            log.info("Task deadline check: {} overdue notices, {} escalations", result.overdueNotices(), result.escalations());
        }
    }

    @Transactional
    public Result runNow() {
        LocalDate today = businessDate.today();
        int notices = 0;
        int escalations = 0;
        for (Task task : tasks.findByStatusInAndDueOnBefore(TaskStatus.OPEN, today)) {
            if (!task.getDueOn().equals(task.getOverdueNotifiedFor())) {
                task.markOverdueNotified(task.getDueOn());
                recorder.note(task, "OVERDUE_NOTICE", null, "Overdue since " + task.getDueOn());
                events.publishEvent(new TaskOverdue(task.getId(), task.getTitle(), task.getAssigneeUserId(), task.getDueOn()));
                notices++;
            }
            long days = ChronoUnit.DAYS.between(task.getDueOn(), today);
            if (task.getEscalatedAt() == null && days >= task.getEscalateAfterDays()) {
                task.markEscalated(clock.instant());
                recorder.note(task, "ESCALATED", null, "Escalated to the Director, " + days + " days overdue");
                events.publishEvent(new TaskEscalated(task.getId(), task.getTitle(), task.getAssigneeUserId(), task.getDueOn(), (int) days));
                escalations++;
            }
        }
        return new Result(notices, escalations);
    }
}
