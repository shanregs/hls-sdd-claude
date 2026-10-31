package com.hls.tasks.internal;

import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.reporting.api.SameDayCountSource;
import com.hls.school.api.CallerContext;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Same-day tasks of a month by Manager: Admin and Director see every Manager, a Manager sees only their own. */
@Component
class TaskSameDayCounts implements SameDayCountSource {

    private final JdbcTemplate jdbc;

    TaskSameDayCounts(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String key() {
        return "tasks";
    }

    @Override
    public PermissionModule permission() {
        return PermissionModule.TASKS;
    }

    @Override
    @Transactional(readOnly = true)
    public Counts counts(YearMonth month, UUID callerUserId, Set<Role> callerRoles) {
        boolean all = CallerContext.isOrgWide(callerRoles);
        Map<UUID, BigDecimal> byManager = new HashMap<>();
        jdbc.query(
                "select assignee_user_id, count(*) as n from task where same_day and due_on between ? and ?"
                        + " and (? or assignee_user_id = ?) group by assignee_user_id",
                rs -> {
                    byManager.put(rs.getObject("assignee_user_id", UUID.class), BigDecimal.valueOf(rs.getLong("n")));
                },
                java.sql.Date.valueOf(month.atDay(1)),
                java.sql.Date.valueOf(month.atEndOfMonth()),
                all,
                callerUserId);
        return new Counts(Map.of(), byManager);
    }
}
