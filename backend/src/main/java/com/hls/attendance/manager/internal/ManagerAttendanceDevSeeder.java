package com.hls.attendance.manager.internal;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.attendance.internal.BusinessCalendar;
import com.hls.attendance.manager.internal.ManagerDay.DayType;
import com.hls.attendance.manager.internal.ManagerDay.Location;
import com.hls.attendance.manager.internal.ManagerDayHistory.Action;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}, idempotent): a few past days for the demo Manager
 * (Manoj) so his month and the Admin's grid show values. Over the last eight working days: a normal day, a day with no
 * check-out, a Field day with a purpose, a gap for the leave day that {@code ManagerLeaveDevSeeder} adds, an absence
 * (no record), a late day, and two more normal days. Nothing of any other module is seeded.
 */
@Component
@Order(65)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
public class ManagerAttendanceDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ManagerAttendanceDevSeeder.class);

    /** Chennai, for the demo locations. */
    private static final Location WHERE =
            new Location(new BigDecimal("13.082700"), new BigDecimal("80.270700"), new BigDecimal("18.0"));

    private final AppUserRepository users;
    private final ManagerQueries managers;
    private final ManagerDayRepository days;
    private final ManagerDayHistoryRepository history;
    private final WorkingCalendar workingCalendar;
    private final BusinessCalendar business;
    private final Clock clock;

    public ManagerAttendanceDevSeeder(
            AppUserRepository users,
            ManagerQueries managers,
            ManagerDayRepository days,
            ManagerDayHistoryRepository history,
            WorkingCalendar workingCalendar,
            BusinessCalendar business,
            Clock clock) {
        this.users = users;
        this.managers = managers;
        this.days = days;
        this.history = history;
        this.workingCalendar = workingCalendar;
        this.business = business;
        this.clock = clock;
    }

    /** The last eight working days before today, oldest first; the leave seeder uses the same window. */
    private static List<LocalDate> demoWindow(WorkingCalendar calendar, LocalDate today) {
        List<LocalDate> working = calendar.workingDays(null, today.minusDays(30), today.minusDays(1));
        return working.subList(Math.max(0, working.size() - 8), working.size());
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Optional<ManagerRef> manoj = users.findByPhone("9800000003").map(AppUser::getId).flatMap(managers::managerByUserId);
        if (manoj.isEmpty() || !days.findByManagerIdAndDayDateBetweenOrderByDayDate(
                        manoj.get().id(), LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1))
                .isEmpty()) {
            return;
        }
        List<LocalDate> window = demoWindow(workingCalendar, business.today());
        if (window.size() < 8) {
            return;
        }
        ManagerRef ref = manoj.get();
        record Plan(int index, DayType type, String purpose, LocalTime in, LocalTime out) {}
        List<Plan> plans = List.of(
                new Plan(0, DayType.OFFICE, null, LocalTime.of(9, 5), LocalTime.of(18, 10)),
                new Plan(1, DayType.OFFICE, null, LocalTime.of(9, 12), null),
                new Plan(2, DayType.FIELD, "Review visit to Demo School One with the Principal", LocalTime.of(9, 40), LocalTime.of(17, 30)),
                // index 3 is left for the leave day, index 4 is an absence
                new Plan(5, DayType.OFFICE, null, LocalTime.of(10, 5), LocalTime.of(18, 0)),
                new Plan(6, DayType.OFFICE, null, LocalTime.of(9, 0), LocalTime.of(17, 45)),
                new Plan(7, DayType.OFFICE, null, LocalTime.of(9, 20), LocalTime.of(18, 30)));
        for (Plan plan : plans) {
            LocalDate date = window.get(plan.index());
            ManagerDay row = new ManagerDay(ref.id(), date);
            boolean late = plan.in().isAfter(LocalTime.of(9, 30));
            row.checkIn(plan.type(), plan.purpose(), false, at(date, plan.in()), WHERE, late);
            if (plan.out() != null) {
                row.checkOut(at(date, plan.out()), WHERE);
            }
            ManagerDay saved = days.save(row);
            history.save(new ManagerDayHistory(
                    saved.getId(), ref.userId(), clock.instant(), Action.CHECK_IN, null, null, DayJson.snapshot(saved)));
        }
        log.info("[DEV SEED] Manager attendance demo data ready: {} past days for {}.", plans.size(), ref.displayName());
    }

    private Instant at(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(business.zone()).toInstant();
    }
}
