package com.hls.substitution.internal;

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

/** Manager cover days recorded on the duty day in a month (half day counts 0.5), by School and by the covering Manager. */
@Component
class CoverSameDayCounts implements SameDayCountSource {

    private final JdbcTemplate jdbc;
    private final SubstitutionScope scope;

    CoverSameDayCounts(JdbcTemplate jdbc, SubstitutionScope scope) {
        this.jdbc = jdbc;
        this.scope = scope;
    }

    @Override
    public String key() {
        return "covers";
    }

    @Override
    public PermissionModule permission() {
        return PermissionModule.SUBSTITUTION;
    }

    @Override
    @Transactional(readOnly = true)
    public Counts counts(YearMonth month, UUID callerUserId, Set<Role> callerRoles) {
        SubstitutionScope.Scope s = scope.of(callerUserId, callerRoles);
        Map<UUID, BigDecimal> bySchool = new HashMap<>();
        Map<UUID, BigDecimal> byManager = new HashMap<>();
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(java.sql.Date.valueOf(month.atDay(1)));
        args.add(java.sql.Date.valueOf(month.atEndOfMonth()));
        args.add(s.all());
        java.util.List<UUID> schoolIds = s.schoolIds().isEmpty() ? java.util.List.of(new UUID(0L, 0L)) : java.util.List.copyOf(s.schoolIds());
        args.addAll(schoolIds);
        String marks = String.join(",", java.util.Collections.nCopies(schoolIds.size(), "?"));
        jdbc.query(
                "select school_id, cover_user_id, sum(day_value) as days from substitution_requirement"
                        + " where same_day and status = 'MANAGER_COVER' and duty_date between ? and ?"
                        + " and (? or school_id in (" + marks + ")) group by school_id, cover_user_id",
                rs -> {
                    BigDecimal days = rs.getBigDecimal("days");
                    bySchool.merge(rs.getObject("school_id", UUID.class), days, BigDecimal::add);
                    UUID manager = rs.getObject("cover_user_id", UUID.class);
                    if (manager != null) {
                        byManager.merge(manager, days, BigDecimal::add);
                    }
                },
                args.toArray());
        return new Counts(bySchool, byManager);
    }
}
