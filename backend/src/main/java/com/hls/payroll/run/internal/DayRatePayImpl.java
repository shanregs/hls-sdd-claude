package com.hls.payroll.run.internal;

import com.hls.payroll.api.DayRatePay;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the pay runs hold for substitute days (spec 017b FR-017). The days of a Substitute's line are frozen in the
 * line's working under {@code substituteDays}, so this reads them back in one query; nothing is stored twice.
 */
@Service
@Transactional(readOnly = true)
class DayRatePayImpl implements DayRatePay {

    private final JdbcTemplate jdbc;

    DayRatePayImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PaidDay> daysInRuns(Collection<UUID> dayIds) {
        if (dayIds.isEmpty()) {
            return List.of();
        }
        String joined = dayIds.stream().map(UUID::toString).collect(Collectors.joining(","));
        return jdbc.query(
                """
                select r.id as run_id, r.month as run_month, r.status as status,
                       e.value ->> 'dayId' as day_id, e.value ->> 'date' as day_date,
                       e.value ->> 'rate' as rate, e.value ->> 'amount' as amount
                from payroll_run_line l
                join payroll_run r on r.id = l.run_id
                cross join lateral jsonb_array_elements(coalesce(l.working -> 'substituteDays', '[]'::jsonb)) as e(value)
                where l.state = 'PAYABLE'
                  and r.status <> 'CANCELLED'
                  and (e.value ->> 'dayId') = any (string_to_array(?, ','))
                order by r.month, r.sequence
                """,
                (rs, n) -> new PaidDay(
                        UUID.fromString(rs.getString("day_id")),
                        (UUID) rs.getObject("run_id"),
                        YearMonth.from(rs.getDate("run_month").toLocalDate()),
                        YearMonth.from(LocalDate.parse(rs.getString("day_date"))),
                        new BigDecimal(rs.getString("rate")),
                        new BigDecimal(rs.getString("amount")),
                        state(rs.getString("status"))),
                joined);
    }

    @Override
    public boolean paidForMonth(UUID teacherId, YearMonth month) {
        Boolean paid = jdbc.queryForObject(
                "select exists (select 1 from payroll_paid_month where month = ? and person_kind = 'TEACHER' and person_id = ?)",
                Boolean.class,
                Date.valueOf(month.atDay(1)),
                teacherId);
        return Boolean.TRUE.equals(paid);
    }

    @Override
    public boolean primaryRunApproved(YearMonth month) {
        Boolean approved = jdbc.queryForObject(
                "select exists (select 1 from payroll_run where month = ? and kind = 'PRIMARY' and status = 'APPROVED')",
                Boolean.class,
                Date.valueOf(month.atDay(1)));
        return Boolean.TRUE.equals(approved);
    }

    private static RunState state(String status) {
        return switch (status) {
            case "APPROVED" -> RunState.APPROVED;
            case "AWAITING_APPROVAL" -> RunState.AWAITING_APPROVAL;
            default -> RunState.DRAFT;
        };
    }
}
