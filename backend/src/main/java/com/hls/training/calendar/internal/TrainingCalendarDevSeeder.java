package com.hls.training.calendar.internal;

import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import com.hls.training.calendar.internal.AssignmentService.AssignBody;
import com.hls.training.calendar.internal.AttendanceRecordService.RowInput;
import com.hls.training.calendar.internal.AttendanceRecordService.SaveBody;
import com.hls.training.calendar.internal.PatternService.PatternBody;
import com.hls.training.calendar.internal.SessionService.SessionBody;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
 * Dev-only demo data for the Training Calendar (inert unless {@code hls.seed.demo-data=true}, idempotent): one standing
 * pattern with its coming months, a published weekend refresher with two registrations, and a held session with one
 * Present and one Absent outcome, which raises one exception. It uses the Director demo user and the first Teachers found.
 */
@Component("trainingCalendarDevSeeder")
@Order(70)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
public class TrainingCalendarDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TrainingCalendarDevSeeder.class);
    static final String PATTERN_TOPIC = "Demo Monthly Training";
    static final String WEEKEND_TOPIC = "Demo Weekend Refresher";
    static final String HELD_TOPIC = "Demo Held Session";

    private final AppUserRepository users;
    private final TeacherDirectory teachers;
    private final PatternService patterns;
    private final SessionService sessionService;
    private final RegistrationService registrations;
    private final AssignmentService assignmentService;
    private final AttendanceRecordService attendance;
    private final TrainingSessionRepository sessions;
    private final PatternRepository patternRepository;
    private final TrainingClock clock;

    public TrainingCalendarDevSeeder(
            AppUserRepository users,
            TeacherDirectory teachers,
            PatternService patterns,
            SessionService sessionService,
            RegistrationService registrations,
            AssignmentService assignmentService,
            AttendanceRecordService attendance,
            TrainingSessionRepository sessions,
            PatternRepository patternRepository,
            TrainingClock clock) {
        this.users = users;
        this.teachers = teachers;
        this.patterns = patterns;
        this.sessionService = sessionService;
        this.registrations = registrations;
        this.assignmentService = assignmentService;
        this.attendance = attendance;
        this.sessions = sessions;
        this.patternRepository = patternRepository;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seed();
        } catch (RuntimeException e) {
            log.warn("[DEV SEED] The training calendar demo data could not be seeded: {}", e.getMessage());
        }
    }

    private void seed() {
        AppUser director = users.findByPhone("9800000002").orElse(null);
        // Tara Teacher (9800000004) is the demo user whose bell the seeders keep quiet, so she is not used here.
        UUID taraUserId = users.findByPhone("9800000004").map(AppUser::getId).orElse(null);
        List<TeacherInfo> active = teachers.activeTeachers().stream()
                .filter(t -> taraUserId == null || !taraUserId.equals(t.userId()))
                .toList();
        if (director == null || active.size() < 2) {
            return;
        }
        Caller caller = new Caller(director.getId(), Set.of(Role.DIRECTOR));
        LocalDate today = clock.today();
        boolean seeded = sessions.findBySessionDateBetweenOrderBySessionDateAscStartsAtAsc(today.minusMonths(2), today.plusMonths(6)).stream()
                .anyMatch(s -> WEEKEND_TOPIC.equals(s.topic));
        if (seeded) {
            return;
        }
        if (patternRepository.findByEndMonthIsNull().stream().noneMatch(p -> PATTERN_TOPIC.equals(p.topic))) {
            patterns.create(
                    caller,
                    new PatternBody(
                            PatternRules.NTH_WEEKDAY, 2, 6, null, LocalTime.of(10, 0), LocalTime.of(13, 0), PATTERN_TOPIC, "Trainer Tina",
                            null, "PHYSICAL", "Head office", null, null, today.withDayOfMonth(1), null, null));
        }
        UUID weekend = sessionService.create(
                caller,
                new SessionBody(
                        TrainingSession.WEEKEND, WEEKEND_TOPIC, today.plusDays(14), LocalTime.of(10, 0), LocalTime.of(13, 0), "Trainer Tina", null,
                        "PHYSICAL", "Head office", 3, "Practice session for the demo", true, null, null));
        for (TeacherInfo t : active.subList(0, 2)) {
            registrations.register(caller, weekend, t.id());
        }

        UUID held = sessionService.create(
                caller,
                new SessionBody(
                        TrainingSession.MONTHLY, HELD_TOPIC, today, LocalTime.of(10, 0), LocalTime.of(13, 0), "Trainer Tina", null, "PHYSICAL",
                        "Head office", null, null, true, null, null));
        UUID present = active.get(0).id();
        UUID absent = active.get(1).id();
        assignmentService.assign(caller, held, new AssignBody("TEACHER", List.of(present, absent), null, null, true));
        attendance.save(
                caller,
                held,
                new SaveBody(List.of(
                        new RowInput(Assignment.TEACHER, present, AttendanceEntry.PRESENT, new BigDecimal("1.00"), null, null, null, null),
                        new RowInput(Assignment.TEACHER, absent, AttendanceEntry.ABSENT, null, "Demo absence", null, "Demo remark", null))));
        log.info("[DEV SEED] Training calendar demo data ready: pattern, weekend refresher, held session with one exception.");
    }
}
