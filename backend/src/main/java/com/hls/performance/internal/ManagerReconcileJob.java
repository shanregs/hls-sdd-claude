package com.hls.performance.internal;

import com.hls.teacher.api.TeacherDirectory;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The daily check that stands in for events the other modules do not publish (spec 026 research.md section 7): open
 * Observation tasks follow the Teacher to their current Manager, and a Teacher who has exited has their open issues closed
 * as Resolved - exited and their open plan cancelled. Running it twice changes nothing the second time.
 */
@Component
public class ManagerReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(ManagerReconcileJob.class);

    /** What one run did. */
    public record Result(int plansMoved, int issuesClosed) {}

    private final PlanService plans;
    private final IssueService issues;
    private final TeacherDirectory teachers;
    private final boolean enabled;
    private final TransactionTemplate tx;

    ManagerReconcileJob(
            PlanService plans,
            IssueService issues,
            TeacherDirectory teachers,
            PlatformTransactionManager transactions,
            @Value("${hls.performance.reconcile.enabled:true}") boolean enabled) {
        this.plans = plans;
        this.issues = issues;
        this.teachers = teachers;
        this.enabled = enabled;
        this.tx = new TransactionTemplate(transactions);
    }

    @Scheduled(cron = "0 40 6 * * *", zone = "${hls.business-timezone:Asia/Kolkata}")
    void scheduled() {
        if (enabled) {
            Result result = runNow();
            log.info("Teacher performance check: {} plans moved, {} issues closed for exited Teachers", result.plansMoved(), result.issuesClosed());
        }
    }

    /** Runs the check now (also used by the tests). */
    public Result runNow() {
        int moved = tx.execute(status -> plans.reconcile());
        int closed = 0;
        List<UUID> candidates = tx.execute(status -> issues.teachersWithOpenWork());
        var info = teachers.teacherInfo(candidates);
        for (UUID teacherId : candidates) {
            var teacher = info.get(teacherId);
            if (teacher != null && "EXITED".equals(teacher.status())) {
                Integer count = tx.execute(status -> issues.closeForExited(teacherId));
                closed += count == null ? 0 : count;
            }
        }
        return new Result(moved, closed);
    }
}
