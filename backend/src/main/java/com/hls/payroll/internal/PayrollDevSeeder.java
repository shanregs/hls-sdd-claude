package com.hls.payroll.internal;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.designation.api.DesignationDirectory;
import com.hls.designation.api.DesignationDirectory.Kind;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}, idempotent): a salary for most of the seeded
 * designations so the Salary Structures list shows values, and none for "Senior Zone Manager" so the missing-salary flag
 * shows once a Manager holds it. It seeds no Teacher's own salary beyond what spec 005's seeder already does.
 */
@Component
@Order(26)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
class PayrollDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PayrollDevSeeder.class);

    private final DesignationDirectory designations;
    private final DesignationSalaryRepository salaries;
    private final AppUserRepository users;
    private final WorkingCalendar calendar;
    private final Clock clock;

    PayrollDevSeeder(
            DesignationDirectory designations,
            DesignationSalaryRepository salaries,
            AppUserRepository users,
            WorkingCalendar calendar,
            Clock clock) {
        this.designations = designations;
        this.salaries = salaries;
        this.users = users;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppUser admin = users.findByPhone("9800000001").orElse(null);
        if (admin == null) {
            return;
        }
        LocalDate from = calendar.businessToday().withDayOfMonth(1).minusMonths(6);
        Object[][] demo = {
            {"Primary Teacher", Kind.TEACHER, "28000"},
            {"Secondary Teacher", Kind.TEACHER, "32000"},
            {"Senior Teacher", Kind.TEACHER, "38000"},
            {"Zone Manager", Kind.MANAGER, "60000"},
        };
        int added = 0;
        for (Object[] row : demo) {
            var designation = designations.findByName((Kind) row[1], (String) row[0]);
            if (designation.isEmpty()
                    || !salaries.findByDesignationIdOrderByEffectiveOnDescRecordedAtDesc(designation.get().id())
                            .isEmpty()) {
                continue;
            }
            salaries.save(new DesignationSalary(
                    designation.get().id(),
                    new BigDecimal((String) row[2]).setScale(2),
                    from,
                    "Demo data",
                    admin.getId(),
                    clock.instant()));
            added++;
        }
        if (added > 0) {
            log.info("[DEV SEED] Salary structures demo data ready: {} added.", added);
        }
    }
}
