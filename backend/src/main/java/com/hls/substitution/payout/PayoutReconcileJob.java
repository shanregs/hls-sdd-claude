package com.hls.substitution.payout;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.substitution.internal.SubstitutionRepository;
import com.hls.substitution.internal.SubstitutionRequirement;
import com.hls.substitution.payout.DayEvent.Cause;
import java.time.LocalDate;
import java.time.YearMonth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Repairs what a missed event or a late run would leave undone (spec 017b FR-021): a live credit without its day record is
 * recreated, a live day whose allocation ended is cancelled, and the status of every day is worked out again from what the
 * runs hold. Every repair writes an event with the cause JOB. Idempotent, so running it twice or beside an event changes
 * nothing more; one run at a time through a database advisory lock. Daily at 04:10 unless the property below is false.
 */
@Component
public class PayoutReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(PayoutReconcileJob.class);
    private static final long LOCK_KEY = 0x017B_0001L;

    /** What one run did, for tests, the log and the endpoint. */
    public record Result(boolean ran, int created, int cancelled, int restated) {}

    private final SubstitutionRepository requirements;
    private final SubstituteDayRepository days;
    private final DayService service;
    private final WorkingCalendar calendar;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final boolean enabled;

    PayoutReconcileJob(
            SubstitutionRepository requirements,
            SubstituteDayRepository days,
            DayService service,
            WorkingCalendar calendar,
            JdbcTemplate jdbc,
            PlatformTransactionManager transactions,
            @Value("${hls.substitution.payout.reconcile.enabled:true}") boolean enabled) {
        this.requirements = requirements;
        this.days = days;
        this.service = service;
        this.calendar = calendar;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
        this.enabled = enabled;
    }

    @Scheduled(cron = "0 10 4 * * *", zone = "${hls.business-timezone:Asia/Kolkata}")
    void scheduled() {
        if (!enabled) {
            return;
        }
        YearMonth thisMonth = YearMonth.from(calendar.businessToday());
        Result result = runFor(thisMonth.minusMonths(2).atDay(1), thisMonth.atEndOfMonth());
        log.info("Substitute payout reconcile: ran {}, created {}, cancelled {}, restated {}", result.ran(), result.created(),
                result.cancelled(), result.restated());
    }

    /** Repairs one month (operators, tests). */
    public Result runForMonth(YearMonth month) {
        return runFor(month.atDay(1), month.atEndOfMonth());
    }

    /** Runs regardless of the schedule switch; skips quietly when another run holds the lock. */
    public Result runFor(LocalDate from, LocalDate to) {
        Result result = tx.execute(status -> {
            Boolean got = jdbc.queryForObject("select pg_try_advisory_xact_lock(?)", Boolean.class, LOCK_KEY);
            if (!Boolean.TRUE.equals(got)) {
                return new Result(false, 0, 0, 0);
            }
            int created = 0;
            for (SubstitutionRequirement credit : requirements.allCredits()) {
                if (!credit.getDutyDate().isBefore(from) && !credit.getDutyDate().isAfter(to) && service.createIfMissing(credit)) {
                    created++;
                }
            }
            int cancelled = 0;
            for (SubstituteDay day : days.between(from, to)) {
                if (service.cancelIfStale(day)) {
                    cancelled++;
                }
            }
            int restated = 0;
            for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)); m = m.plusMonths(1)) {
                restated += service.refreshMonth(m, Cause.JOB);
            }
            return new Result(true, created, cancelled, restated);
        });
        return result == null ? new Result(false, 0, 0, 0) : result;
    }
}
