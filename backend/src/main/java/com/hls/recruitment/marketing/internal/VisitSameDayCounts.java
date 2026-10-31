package com.hls.recruitment.marketing.internal;

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

/** Same-day visits of a month by the Manager who logged them: Admin and Director see all, a Manager sees their own. */
@Component
class VisitSameDayCounts implements SameDayCountSource {

    private final JdbcTemplate jdbc;

    VisitSameDayCounts(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String key() {
        return "visits";
    }

    @Override
    public PermissionModule permission() {
        return PermissionModule.MARKETING;
    }

    @Override
    @Transactional(readOnly = true)
    public Counts counts(YearMonth month, UUID callerUserId, Set<Role> callerRoles) {
        boolean all = CallerContext.isOrgWide(callerRoles);
        Map<UUID, BigDecimal> byManager = new HashMap<>();
        Map<UUID, BigDecimal> bySchool = new HashMap<>();
        jdbc.query(
                "select created_by, school_id, count(*) as n from marketing_activity where same_day"
                        + " and activity_date between ? and ? and (? or created_by = ?) group by created_by, school_id",
                rs -> {
                    BigDecimal n = BigDecimal.valueOf(rs.getLong("n"));
                    byManager.merge(rs.getObject("created_by", UUID.class), n, BigDecimal::add);
                    UUID school = rs.getObject("school_id", UUID.class);
                    if (school != null) {
                        bySchool.merge(school, n, BigDecimal::add);
                    }
                },
                java.sql.Date.valueOf(month.atDay(1)),
                java.sql.Date.valueOf(month.atEndOfMonth()),
                all,
                callerUserId);
        return new Counts(bySchool, byManager);
    }
}
