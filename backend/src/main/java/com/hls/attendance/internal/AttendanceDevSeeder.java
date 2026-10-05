package com.hls.attendance.internal;

import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.school.api.ConflictException;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.PlacementSpan;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}, idempotent): the 2026 Tamil Nadu
 * public holidays and a few months of attendance marks for every placed Teacher, so the grids,
 * history, rollups and month lock can be tried straight away. Today and yesterday are left unmarked
 * on purpose, and previous months are complete so they can be locked.
 */
@Component
@Order(50)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
public class AttendanceDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AttendanceDevSeeder.class);

    /**
     * Tamil Nadu Government public holidays, 2026. Dates of lunar and religious festivals follow the
     * published list and should be checked against the year's official G.O. before real use.
     */
    static final Map<LocalDate, String> TAMIL_NADU_HOLIDAYS_2026 = Map.ofEntries(
            Map.entry(LocalDate.of(2026, 1, 1), "New Year's Day"),
            Map.entry(LocalDate.of(2026, 1, 14), "Pongal"),
            Map.entry(LocalDate.of(2026, 1, 15), "Thiruvalluvar Day / Mattu Pongal"),
            Map.entry(LocalDate.of(2026, 1, 16), "Uzhavar Thirunal"),
            Map.entry(LocalDate.of(2026, 1, 26), "Republic Day"),
            Map.entry(LocalDate.of(2026, 2, 1), "Thai Poosam"),
            Map.entry(LocalDate.of(2026, 3, 19), "Telugu New Year's Day (Ugadi)"),
            Map.entry(LocalDate.of(2026, 3, 21), "Ramzan (Idul Fitr)"),
            Map.entry(LocalDate.of(2026, 3, 31), "Mahavir Jayanti"),
            Map.entry(LocalDate.of(2026, 4, 3), "Good Friday"),
            Map.entry(LocalDate.of(2026, 4, 14), "Tamil New Year's Day / Dr. B.R. Ambedkar Jayanti"),
            Map.entry(LocalDate.of(2026, 5, 1), "May Day"),
            Map.entry(LocalDate.of(2026, 5, 28), "Bakrid (Idul Azha)"),
            Map.entry(LocalDate.of(2026, 6, 26), "Muharram"),
            Map.entry(LocalDate.of(2026, 8, 15), "Independence Day"),
            Map.entry(LocalDate.of(2026, 8, 26), "Milad-un-Nabi"),
            Map.entry(LocalDate.of(2026, 9, 4), "Krishna Jayanthi"),
            Map.entry(LocalDate.of(2026, 9, 14), "Vinayagar Chathurthi"),
            Map.entry(LocalDate.of(2026, 10, 2), "Gandhi Jayanthi"),
            Map.entry(LocalDate.of(2026, 10, 19), "Ayudha Pooja"),
            Map.entry(LocalDate.of(2026, 10, 20), "Vijayadasami"),
            Map.entry(LocalDate.of(2026, 11, 8), "Deepavali"),
            Map.entry(LocalDate.of(2026, 12, 25), "Christmas"));

    private static final int HISTORY_DAYS = 90;

    private final AppUserRepository users;
    private final CalendarService calendar;
    private final NonWorkingDateRepository holidays;
    private final MarkService marks;
    private final TeacherDirectory teachers;
    private final BusinessCalendar business;

    public AttendanceDevSeeder(
            AppUserRepository users,
            CalendarService calendar,
            NonWorkingDateRepository holidays,
            MarkService marks,
            TeacherDirectory teachers,
            BusinessCalendar business) {
        this.users = users;
        this.calendar = calendar;
        this.holidays = holidays;
        this.marks = marks;
        this.teachers = teachers;
        this.business = business;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppUser admin = users.findByPhone("9800000001").orElse(null);
        if (admin == null) {
            return;
        }
        int addedHolidays = seedHolidays(admin.getId());
        UUID supervisor = users.findByPhone("9800000003").map(AppUser::getId).orElse(admin.getId());
        int addedMarks = seedMarks(supervisor);
        log.info("[DEV SEED] Attendance demo data ready: {} holidays and {} marks added.", addedHolidays, addedMarks);
    }

    private int seedHolidays(UUID actor) {
        int added = 0;
        for (var holiday : TAMIL_NADU_HOLIDAYS_2026.entrySet()) {
            if (holidays.findByOnDate(holiday.getKey()).isEmpty()) {
                calendar.addNonWorkingDate(actor, holiday.getKey(), holiday.getValue());
                added++;
            }
        }
        return added;
    }

    private int seedMarks(UUID supervisor) {
        LocalDate today = business.today();
        LocalDate from = today.minusDays(HISTORY_DAYS);
        Set<UUID> placed = teachers.teachersPlacedDuring(from, today);
        Map<UUID, TeacherInfo> infos = teachers.teacherInfo(placed);
        Set<LocalDate> offDays = TAMIL_NADU_HOLIDAYS_2026.keySet();
        List<PlacementSpan> spans = teachers.placementsOverlapping(placed, from, today);
        int added = 0;
        for (PlacementSpan span : spans) {
            TeacherInfo info = infos.get(span.teacherId());
            LocalDate start = span.startsOn().isAfter(from) ? span.startsOn() : from;
            LocalDate end = span.endsOn() == null || span.endsOn().isAfter(today) ? today : span.endsOn();
            for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                if (day.getDayOfWeek() == DayOfWeek.SUNDAY || offDays.contains(day)) {
                    continue;
                }
                // Leave today and yesterday open so "Mark today" and the unmarked count have something to show.
                if (!day.isBefore(today.minusDays(1))) {
                    continue;
                }
                if (marks.hasMark(span.teacherId(), day)) {
                    continue;
                }
                added += markOne(supervisor, info, span, day, today) ? 1 : 0;
            }
        }
        return added;
    }

    private boolean markOne(UUID supervisor, TeacherInfo info, PlacementSpan span, LocalDate day, LocalDate today) {
        int roll = Math.floorMod(span.teacherId().hashCode() * 31 + (int) day.toEpochDay() * 17, 100);
        String code = "P";
        BigDecimal value = BigDecimal.ONE;
        String note = null;
        if (roll < 6) {
            code = "L";
            note = "Casual leave";
        } else if (roll < 12) {
            value = new BigDecimal("0.5");
            note = "Half day";
        } else if (roll < 15) {
            code = "T";
            note = "Training";
        }
        // The Teacher's own account marks her last days herself; everything older was set by the Manager.
        boolean self = info.userId() != null && day.isAfter(today.minusDays(4));
        try {
            marks.setMarkWithoutNotifying(
                    self ? info.userId() : supervisor,
                    span.teacherId(),
                    day,
                    code,
                    value,
                    note,
                    null,
                    self ? SetByKind.SELF : SetByKind.SUPERVISOR);
            return true;
        } catch (ConflictException e) {
            log.debug("[DEV SEED] Skipped {} on {}: {}", info.name(), day, e.getMessage());
            return false;
        }
    }
}
