package com.hls.leave.internal;

import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.reporting.api.SameDayCountSource;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Same-day leave days in a month by School, in the caller's Teacher scope (spec 040 FR-014). */
@Component
class LeaveSameDayCounts implements SameDayCountSource {

    private final JdbcTemplate jdbc;
    private final LeaveScope scope;

    LeaveSameDayCounts(JdbcTemplate jdbc, LeaveScope scope) {
        this.jdbc = jdbc;
        this.scope = scope;
    }

    @Override
    public String key() {
        return "leave";
    }

    @Override
    public PermissionModule permission() {
        return PermissionModule.LEAVE_MANAGEMENT;
    }

    @Override
    @Transactional(readOnly = true)
    public Counts counts(YearMonth month, UUID callerUserId, Set<Role> callerRoles) {
        LeaveScope.Scope s = scope.of(callerUserId, callerRoles);
        Map<UUID, BigDecimal> bySchool = new HashMap<>();
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(java.sql.Date.valueOf(month.atDay(1)));
        args.add(java.sql.Date.valueOf(month.atEndOfMonth()));
        args.add(s.orgWide());
        args.addAll(s.teacherIds());
        String marks = String.join(",", java.util.Collections.nCopies(s.teacherIds().size(), "?"));
        jdbc.query(
                "select school_id, sum(working_days) as days from leave_request"
                        + " where same_day and status = 'APPROVED' and first_date between ? and ?"
                        + " and (? or teacher_id in (" + marks + ")) group by school_id",
                rs -> {
                    bySchool.put(rs.getObject("school_id", UUID.class), rs.getBigDecimal("days"));
                },
                args.toArray());
        return new Counts(bySchool, Map.of());
    }
}
