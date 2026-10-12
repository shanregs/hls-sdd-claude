package com.hls.tasks.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.identity.user.Role;
import com.hls.identity.user.RoleAssignmentRepository;
import com.hls.organization.api.ManagerQueries;
import com.hls.tasks.api.TaskCreation.FollowUpRequest;
import com.hls.tasks.api.TaskType;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}, idempotent through the source key): for the first
 * seeded Manager, one task due today, one upcoming and one already overdue, so every list has something to show.
 */
@Component
@Order(46)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
class TaskDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TaskDevSeeder.class);

    private final TaskService tasks;
    private final RoleAssignmentRepository roles;
    private final ManagerQueries managers;
    private final BusinessDate businessDate;

    TaskDevSeeder(TaskService tasks, RoleAssignmentRepository roles, ManagerQueries managers, BusinessDate businessDate) {
        this.tasks = tasks;
        this.roles = roles;
        this.managers = managers;
        this.businessDate = businessDate;
    }

    @Override
    public void run(ApplicationArguments args) {
        UUID manager = roles.userIdsWithRole(Role.MANAGER).stream()
                .filter(id -> managers.managerByUserId(id).filter(ManagerQueries.ManagerRef::active).isPresent())
                .findFirst()
                .orElse(null);
        if (manager == null) {
            return;
        }
        var today = businessDate.today();
        Object[][] demo = {
            {"demo-today", TaskType.SCHOOL_VISIT, "Visit the school and meet the Principal", today},
            {"demo-upcoming", TaskType.CONTENT_SUBMISSION, "Submit this month's content", today.plusDays(5)},
            {"demo-overdue", TaskType.PRINCIPAL_FEEDBACK, "Collect Principal feedback on the new Teacher", today.minusDays(2)},
        };
        int added = 0;
        for (Object[] row : demo) {
            var ref = tasks.createFollowUp(new FollowUpRequest(
                    (TaskType) row[1], (String) row[2], null, manager, (java.time.LocalDate) row[3], null, null, null, null, null, "DEMO", (String) row[0], null));
            if (ref.created()) {
                added++;
            }
        }
        if (added > 0) {
            log.info("[DEV SEED] Manager tasks demo data ready: {} added.", added);
        }
    }
}
