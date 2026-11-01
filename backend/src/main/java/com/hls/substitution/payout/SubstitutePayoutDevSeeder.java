package com.hls.substitution.payout;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.substitution.api.SubstitutionAssigned;
import com.hls.substitution.internal.SubstitutionRepository;
import com.hls.substitution.internal.SubstitutionRequirement;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}, idempotent), spec 017b: two Substitute Teachers with
 * days in several states. Sunita covered three days last month, all Present, and that month is locked, so her days wait for
 * the pay run. Suresh has a Present day, an Absent day and one still to come this month. Each day belongs to a real
 * requirement, so the repair job leaves it alone.
 */
@Component
@Order(75)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
class SubstitutePayoutDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SubstitutePayoutDevSeeder.class);

    private final AppUserRepository users;
    private final TeacherDirectory teachers;
    private final SchoolDirectory schools;
    private final SubstitutionRepository requirements;
    private final SubstituteDayRepository days;
    private final DayService dayService;
    private final WorkingCalendar calendar;
    private final Clock clock;

    SubstitutePayoutDevSeeder(
            AppUserRepository users,
            TeacherDirectory teachers,
            SchoolDirectory schools,
            SubstitutionRepository requirements,
            SubstituteDayRepository days,
            DayService dayService,
            WorkingCalendar calendar,
            Clock clock) {
        this.users = users;
        this.teachers = teachers;
        this.schools = schools;
        this.requirements = requirements;
        this.days = days;
        this.dayService = dayService;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppUser admin = users.findByPhone("9800000001").orElse(null);
        Optional<UUID> sunita = teacherNamed("Sunita Substitute");
        Optional<UUID> suresh = teacherNamed("Suresh Substitute");
        var zone = schools.zoneByName("Demo Zone");
        if (admin == null || sunita.isEmpty() || suresh.isEmpty() || zone.isEmpty()) {
            return;
        }
        List<SchoolInfo> zoneSchools = schools.schoolsInZone(zone.get().id());
        List<UUID> absent = List.of("Meena Selvi", "Karthik Raja", "Lakshmi Priya").stream()
                .map(this::teacherNamed)
                .flatMap(Optional::stream)
                .toList();
        if (zoneSchools.isEmpty() || absent.size() < 3) {
            return;
        }
        LocalDate today = calendar.businessToday();
        YearMonth last = YearMonth.from(today).minusMonths(1);
        if (!days.ofTeacher(sunita.get(), last.atDay(1), today.plusDays(7)).isEmpty()) {
            return;
        }
        SchoolInfo school = zoneSchools.get(0);

        // last month: three Present days for Sunita, then her month is locked
        allocate(admin, sunita.get(), absent.get(0), school, last.atDay(4), "1.00", DayAttendance.PRESENT);
        allocate(admin, sunita.get(), absent.get(1), school, last.atDay(5), "1.00", DayAttendance.PRESENT);
        allocate(admin, sunita.get(), absent.get(2), school, last.atDay(6), "0.50", DayAttendance.PRESENT);
        dayService.lock(admin.getId(), sunita.get(), last);

        // this month: Suresh has a Present day, an Absent day and one still to come
        allocate(admin, suresh.get(), absent.get(0), school, today.minusDays(3), "1.00", DayAttendance.PRESENT);
        allocate(admin, suresh.get(), absent.get(1), school, today.minusDays(2), "1.00", DayAttendance.ABSENT);
        allocate(admin, suresh.get(), absent.get(2), school, today.plusDays(1), "1.00", DayAttendance.NOT_MARKED);
        log.info("[DEV SEED] Substitute payout demo data ready: Sunita (last month locked) and Suresh (this month).");
    }

    private Optional<UUID> teacherNamed(String name) {
        return teachers.allTeachers().stream().filter(t -> name.equals(t.name())).map(TeacherInfo::id).findFirst();
    }

    private void allocate(
            AppUser admin, UUID substitute, UUID absentTeacher, SchoolInfo school, LocalDate date, String value, DayAttendance attendance) {
        Instant now = clock.instant();
        SubstitutionRequirement requirement =
                new SubstitutionRequirement(absentTeacher, school.id(), date, new BigDecimal(value), "ABSENT", now);
        requirement.assign(substitute, "Demo data", now);
        requirement = requirements.saveAndFlush(requirement);
        dayService.onAssigned(new SubstitutionAssigned(requirement.getId(), substitute, school.id(), school.name(), date));
        if (attendance != DayAttendance.NOT_MARKED) {
            SubstituteDay day = days.findByRequirementIdAndSubstituteTeacherId(requirement.getId(), substitute).orElseThrow();
            day.mark(attendance, admin.getId(), now);
            dayService.refresh(day, now, DayEvent.Cause.MARK);
            days.save(day);
        }
    }
}
