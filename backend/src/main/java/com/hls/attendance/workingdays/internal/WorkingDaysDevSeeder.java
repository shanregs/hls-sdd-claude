package com.hls.attendance.workingdays.internal;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}, idempotent): this year's monthly working days for the
 * first few Schools, so the School Working Days screen has rows to filter and edit straight away. The first School counts
 * every day of each month, the second four fewer, and the third counts none in May.
 */
@Component
@Order(60)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
public class WorkingDaysDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkingDaysDevSeeder.class);

    private final AppUserRepository users;
    private final SchoolDirectory schools;
    private final WorkingCalendar calendar;
    private final SchoolWorkingDaysRepository rows;
    private final Clock clock;

    public WorkingDaysDevSeeder(
            AppUserRepository users,
            SchoolDirectory schools,
            WorkingCalendar calendar,
            SchoolWorkingDaysRepository rows,
            Clock clock) {
        this.users = users;
        this.schools = schools;
        this.calendar = calendar;
        this.rows = rows;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppUser admin = users.findByPhone("9800000001").orElse(null);
        if (admin == null) {
            return;
        }
        List<SchoolInfo> demo = schools.allSchools().stream().limit(3).toList();
        int year = calendar.businessToday().getYear();
        int added = 0;
        for (int index = 0; index < demo.size(); index++) {
            UUID schoolId = demo.get(index).id();
            for (int month = 1; month <= 12; month++) {
                if (rows.findBySchoolIdAndYearAndMonth(schoolId, year, month).isPresent()) {
                    continue;
                }
                int days = YearMonth.of(year, month).lengthOfMonth();
                int counted = switch (index) {
                    case 0 -> days;
                    case 1 -> days - 4;
                    default -> month == 5 ? 0 : days - 2;
                };
                rows.save(new SchoolWorkingDaysRow(
                        schoolId, year, month, BigDecimal.valueOf(counted).setScale(1), admin.getId(), clock.instant()));
                added++;
            }
        }
        if (added > 0) {
            log.info("Demo data: added {} School working days rows for {}", added, year);
        }
    }
}
