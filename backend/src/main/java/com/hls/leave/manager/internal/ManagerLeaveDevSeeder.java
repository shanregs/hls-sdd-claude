package com.hls.leave.manager.internal;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.leave.internal.LeaveType;
import com.hls.leave.internal.LeaveTypeCatalog;
import com.hls.leave.manager.internal.ManagerLeaveService.RequestInput;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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

/**
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}, idempotent): for the demo Manager (Manoj) one
 * approved leave day inside the window the attendance seeder leaves free, and one pending request in the future, so the
 * Director's Manager Leave screen has something to decide. Goes through the real service, so the days are written and
 * audited as they would be in use.
 */
@Component
@Order(66)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
public class ManagerLeaveDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ManagerLeaveDevSeeder.class);

    private final AppUserRepository users;
    private final ManagerQueries managers;
    private final ManagerLeaveRequestRepository requests;
    private final ManagerLeaveService leave;
    private final LeaveTypeCatalog types;
    private final WorkingCalendar workingCalendar;
    private final Clock clock;

    public ManagerLeaveDevSeeder(
            AppUserRepository users,
            ManagerQueries managers,
            ManagerLeaveRequestRepository requests,
            ManagerLeaveService leave,
            LeaveTypeCatalog types,
            WorkingCalendar workingCalendar,
            Clock clock) {
        this.users = users;
        this.managers = managers;
        this.requests = requests;
        this.leave = leave;
        this.types = types;
        this.workingCalendar = workingCalendar;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        Optional<AppUser> manoj = users.findByPhone("9800000003");
        Optional<AppUser> director = users.findByPhone("9800000002");
        Optional<ManagerRef> ref = manoj.map(AppUser::getId).flatMap(managers::managerByUserId);
        if (ref.isEmpty() || director.isEmpty() || !requests.findByManagerIdOrderByRequestedAtDesc(ref.get().id()).isEmpty()) {
            return;
        }
        UUID casual = types.all().stream()
                .filter(t -> "CASUAL".equals(t.getCode()))
                .map(LeaveType::getId)
                .findFirst()
                .orElse(null);
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")));
        List<LocalDate> window = workingDays(today);
        if (casual == null || window.size() < 8) {
            return;
        }
        UUID userId = manoj.get().getId();
        LocalDate day = window.get(3);
        UUID approved = leave.request(userId, input(casual, day, day, "Family function")).id();
        leave.approve(director.get().getId(), approved);

        LocalDate next = workingCalendar.workingDays(null, today.plusDays(7), today.plusDays(21)).stream()
                .findFirst()
                .orElse(null);
        if (next != null) {
            leave.request(userId, input(casual, next, next, "Temple festival in our village"));
        }
        log.info("[DEV SEED] Manager leave demo data ready: one approved day and one pending request for {}.", ref.get().displayName());
    }

    /** The same window as {@code ManagerAttendanceDevSeeder.demoWindow}: the last eight working days before today. */
    private List<LocalDate> workingDays(LocalDate today) {
        List<LocalDate> working = workingCalendar.workingDays(null, today.minusDays(30), today.minusDays(1));
        return working.subList(Math.max(0, working.size() - 8), working.size());
    }

    private static RequestInput input(UUID type, LocalDate first, LocalDate last, String reason) {
        return new RequestInput(type.toString(), first.toString(), last.toString(), false, false, reason);
    }
}
