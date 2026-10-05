package com.hls.leave.internal;

import com.hls.attendance.api.LeaveAttendance;
import com.hls.attendance.api.LeaveAttendance.LeaveDay;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}, idempotent per Teacher): sample leave
 * requests so Leave Management has something to decide straight away. Tara has one Pending, one
 * Approved (its Leave marks written through the attendance contract) and one Rejected request; the
 * other demo Teachers each have a Pending one. Demo Teachers in Demo School One report to Manoj
 * Manager, the one in Demo School Two has no Manager, so only Admin and Director see hers.
 */
@Component
@Order(60)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
public class LeaveDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LeaveDevSeeder.class);

    private final AppUserRepository users;
    private final TeacherDirectory teachers;
    private final LeaveAttendance attendance;
    private final LeaveRequestRepository requests;
    private final LeaveTypeRepository types;
    private final Clock clock;

    public LeaveDevSeeder(
            AppUserRepository users,
            TeacherDirectory teachers,
            LeaveAttendance attendance,
            LeaveRequestRepository requests,
            LeaveTypeRepository types,
            Clock clock) {
        this.users = users;
        this.teachers = teachers;
        this.attendance = attendance;
        this.requests = requests;
        this.types = types;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppUser admin = users.findByPhone("9800000001").orElse(null);
        if (admin == null) {
            return;
        }
        UUID approver = users.findByPhone("9800000003").map(AppUser::getId).orElse(admin.getId());
        Map<String, UUID> typeIds = new java.util.HashMap<>();
        types.findAll().forEach(t -> typeIds.put(t.getCode(), t.getId()));

        int added = 0;
        Optional<TeacherInfo> tara = users.findByPhone("9800000004").flatMap(u -> teachers.teacherOfUser(u.getId()));
        if (tara.isPresent() && !hasAny(tara.get().id())) {
            UUID teacher = tara.get().id();
            UUID userId = tara.get().userId();
            added += pending(teacher, userId, typeIds.get("CASUAL"), 7, 2, "Pongal travel to our native place in Thanjavur");
            added += approved(teacher, userId, approver, typeIds.get("PERSONAL"), 21, 2, "Family function at Madurai");
            added += rejected(teacher, userId, approver, typeIds.get("SICK"), -6, 1, "Medical appointment at the district hospital",
                    "Annual exams are on that day. Please reschedule the appointment.");
        }
        added += pendingFor(admin.getId(), "Meena", typeIds.get("CASUAL"), 8, 3, "Temple festival at our village near Kancheepuram");
        added += pendingFor(admin.getId(), "Karthik", typeIds.get("SICK"), 9, 2, "Fever, doctor advised rest");
        added += pendingFor(admin.getId(), "Lakshmi", typeIds.get("PERSONAL"), 10, 1, "Sister's wedding rituals");
        log.info("[DEV SEED] Leave demo data ready: {} requests added.", added);
    }

    private boolean hasAny(UUID teacherId) {
        return requests.findOwn(teacherId, java.util.EnumSet.allOf(LeaveStatus.class), PageRequest.of(0, 1))
                .hasContent();
    }

    private int pendingFor(UUID fallbackUser, String firstName, UUID type, int startInDays, int workingDays, String reason) {
        var placed = teachers.teachersPlacedDuring(attendance.businessToday(), attendance.businessToday());
        return teachers.teacherInfo(placed).values().stream()
                .filter(t -> t.name().startsWith(firstName))
                .findFirst()
                .filter(t -> !hasAny(t.id()))
                .map(t -> pending(t.id(), t.userId() != null ? t.userId() : fallbackUser, type, startInDays, workingDays, reason))
                .orElse(0);
    }

    private int pending(UUID teacher, UUID userId, UUID type, int startInDays, int workingDays, String reason) {
        return create(teacher, userId, type, startInDays, workingDays, reason) == null ? 0 : 1;
    }

    private int approved(UUID teacher, UUID userId, UUID approver, UUID type, int startInDays, int workingDays, String reason) {
        LeaveRequest request = create(teacher, userId, type, startInDays, workingDays, reason);
        if (request == null) {
            return 0;
        }
        var counted = LeaveCounter.count(
                attendance.workingDays(teacher, request.getFirstDate(), request.getLastDate()), false, false);
        attendance.apply(request.getId(), approver, teacher, counted.days());
        request.approve(approver, "Approved. Enjoy the function.", counted.total(), clock.instant());
        requests.save(request);
        return 1;
    }

    private int rejected(
            UUID teacher, UUID userId, UUID approver, UUID type, int startInDays, int workingDays, String reason, String why) {
        LeaveRequest request = create(teacher, userId, type, startInDays, workingDays, reason);
        if (request == null) {
            return 0;
        }
        request.reject(approver, why, clock.instant());
        requests.save(request);
        return 1;
    }

    /** Saves a Pending request covering {@code workingDays} working days from about {@code startInDays} away. */
    private LeaveRequest create(UUID teacher, UUID userId, UUID type, int startInDays, int workingDays, String reason) {
        if (type == null || userId == null) {
            return null;
        }
        LocalDate from = attendance.businessToday().plusDays(startInDays);
        LocalDate to = from.plusDays(14);
        List<LeaveDay> days = attendance.workingDays(teacher, from, to).stream().limit(workingDays).toList();
        if (days.isEmpty()) {
            return null;
        }
        var counted = LeaveCounter.count(days, false, false);
        LeaveRequest request = new LeaveRequest(
                teacher,
                days.get(0).schoolId(),
                type,
                days.get(0).date(),
                days.get(days.size() - 1).date(),
                false,
                false,
                counted.total(),
                reason,
                userId,
                clock.instant());
        return requests.saveAndFlush(request);
    }
}
