package com.hls.schoolbilling.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.schoolbilling.api.MonthlyDueOverdue;
import com.hls.schoolbilling.internal.ReceivablesStore.DueRecord;
import com.hls.tasks.api.TaskCreation;
import com.hls.tasks.api.TaskCreation.FollowUpRequest;
import com.hls.tasks.api.TaskCreation.TaskRef;
import com.hls.tasks.api.TaskType;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
 * The follow-up task of an overdue monthly due (spec 022 FR-016 to FR-018, research R8). The daily job creates one task for
 * the School's Zone Manager through spec 025, or, without a Zone Manager who can hold a task, tells the Director and Admin
 * once. Escalation to the Director is spec 025's own deadline check; the task is given escalation days so that it fires the
 * configured number of days after the due date. Closing a due (paid or cancelled) closes the task.
 */
@Component
public class OverdueFollowUps {

    public static final String SOURCE = "SCHOOL_DUE_OVERDUE";
    public static final String LINKED_TYPE = "MONTHLY_DUE";

    private static final Logger log = LoggerFactory.getLogger(OverdueFollowUps.class);

    /** What one run did. */
    public record Result(int tasksCreated, int managerNotified, int reassigned, int closed) {}

    private final ReceivablesStore store;
    private final BillingSettings settings;
    private final TaskCreation tasks;
    private final ManagerQueries managers;
    private final SchoolDirectory schools;
    private final BusinessDate businessDate;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final boolean enabled;

    OverdueFollowUps(
            ReceivablesStore store,
            BillingSettings settings,
            TaskCreation tasks,
            ManagerQueries managers,
            SchoolDirectory schools,
            BusinessDate businessDate,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactions,
            @Value("${hls.schoolbilling.overdue.enabled:true}") boolean enabled) {
        this.store = store;
        this.settings = settings;
        this.tasks = tasks;
        this.managers = managers;
        this.schools = schools;
        this.businessDate = businessDate;
        this.events = events;
        this.enabled = enabled;
        this.tx = new TransactionTemplate(transactions);
    }

    @Scheduled(cron = "0 45 6 * * *", zone = "${hls.business-timezone:Asia/Kolkata}")
    void scheduled() {
        if (enabled) {
            Result result = runNow();
            log.info("Overdue dues check: {} tasks, {} notices, {} reassigned, {} closed",
                    result.tasksCreated(), result.managerNotified(), result.reassigned(), result.closed());
        }
    }

    /** One pass; safe to repeat any number of times. */
    public Result runNow() {
        LocalDate today = businessDate.today();
        int created = 0;
        int noManager = 0;
        int reassigned = 0;
        int closed = 0;
        List<UUID> fresh = store.jdbc().query(
                """
                select d.id from monthly_due d join due_balance b on b.due_id = d.id
                where d.cancelled_at is null and b.balance > 0 and d.due_on < ?
                  and not exists (select 1 from overdue_follow_up f where f.due_id = d.id)
                order by d.due_on
                """,
                (rs, n) -> rs.getObject("id", UUID.class),
                today);
        for (UUID dueId : fresh) {
            Boolean madeTask = tx.execute(status -> open(dueId, today));
            if (madeTask == null) {
                continue;
            }
            if (madeTask) {
                created++;
            } else {
                noManager++;
            }
        }
        List<UUID> open = store.jdbc().query(
                "select due_id from overdue_follow_up where closed_on is null",
                (rs, n) -> rs.getObject("due_id", UUID.class));
        for (UUID dueId : open) {
            Integer outcome = tx.execute(status -> keepInStep(dueId));
            if (outcome != null && outcome == 1) {
                closed++;
            } else if (outcome != null && outcome == 2) {
                reassigned++;
            }
        }
        return new Result(created, noManager, reassigned, closed);
    }

    /** Creates the follow-up for a newly overdue due; true when a task was made, false when the Director and Admin were told. */
    private Boolean open(UUID dueId, LocalDate today) {
        Optional<DueRecord> found = store.due(dueId);
        if (found.isEmpty() || found.get().cancelled() || found.get().balance().signum() <= 0) {
            return null;
        }
        DueRecord due = found.get();
        long daysOverdue = ChronoUnit.DAYS.between(due.dueOn(), today);
        SchoolInfo school = schools.school(due.schoolId()).orElse(null);
        String schoolName = school == null ? "School" : school.name();
        Optional<ManagerRef> manager = managers.managerOfSchool(due.schoolId()).filter(m -> m.active() && m.userId() != null)
                .filter(m -> managers.managerByUserId(m.userId()).filter(ManagerRef::active).isPresent());
        if (manager.isEmpty()) {
            store.jdbc().update("insert into overdue_follow_up (id, due_id, task_id, created_on) values (?, ?, null, ?)",
                    UUID.randomUUID(), dueId, today);
            events.publishEvent(new MonthlyDueOverdue(dueId, due.schoolId(), schoolName, due.month(), due.balance(), daysOverdue, true));
            return false;
        }
        int escalateAfter = (int) Math.max(1, Math.min(30, settings.escalationDays() - daysOverdue));
        TaskRef ref = tasks.createFollowUp(new FollowUpRequest(
                TaskType.FOLLOW_UP,
                shorten("Collect payment: " + schoolName + " " + due.month(), 120),
                shorten("Monthly due " + due.reference() + " (" + due.month() + ") is " + daysOverdue + " days past its due date of "
                        + due.dueOn() + ". Balance " + due.balance().toPlainString() + ".", 2000),
                manager.get().userId(),
                today,
                escalateAfter,
                LINKED_TYPE,
                dueId,
                due.reference(),
                shorten(schoolName, 160),
                SOURCE,
                dueId.toString(),
                null));
        store.jdbc().update("insert into overdue_follow_up (id, due_id, task_id, created_on) values (?, ?, ?, ?)",
                UUID.randomUUID(), dueId, ref.id(), today);
        return true;
    }

    /** 1 when the follow-up was closed, 2 when its task was given to the new Zone Manager, 0 otherwise. */
    private Integer keepInStep(UUID dueId) {
        if (closeIfSettled(dueId, null)) {
            return 1;
        }
        Optional<DueRecord> due = store.due(dueId);
        Optional<UUID> task = store.followUpTask(dueId);
        if (due.isEmpty() || task.isEmpty()) {
            return 0;
        }
        Optional<ManagerRef> manager = managers.managerOfSchool(due.get().schoolId())
                .filter(m -> m.userId() != null && managers.managerByUserId(m.userId()).filter(ManagerRef::active).isPresent());
        if (manager.isPresent() && tasks.reassign(SOURCE, dueId.toString(), manager.get().userId())) {
            return 0;
        }
        return 0;
    }

    /** Closes the due's follow-up when the due is paid or cancelled. Returns true when something was closed. */
    @Transactional
    public boolean closeIfSettled(UUID dueId, UUID actor) {
        Optional<DueRecord> found = store.due(dueId);
        if (found.isEmpty()) {
            return false;
        }
        DueRecord due = found.get();
        boolean settled = due.cancelled() || due.balance().signum() <= 0;
        if (!settled) {
            return false;
        }
        Integer open = store.jdbc().queryForObject(
                "select count(*) from overdue_follow_up where due_id = ? and closed_on is null", Integer.class, dueId);
        if (open == null || open == 0) {
            return false;
        }
        if (store.followUpTask(dueId).isPresent()) {
            if (due.cancelled()) {
                tasks.cancel(SOURCE, dueId.toString(), "The monthly due was cancelled.");
            } else {
                tasks.complete(SOURCE, dueId.toString(), "The monthly due is paid in full.", actor);
            }
        }
        store.jdbc().update("update overdue_follow_up set closed_on = ? where due_id = ? and closed_on is null", businessDate.today(), dueId);
        return true;
    }

    private static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
