package com.hls.notification.internal;

import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.notification.api.NotificationType;
import com.hls.teacher.api.TeacherDirectory;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}): notifications that match the demo leave
 * requests, so the bell is not empty on first sign-in. Tara has a read "leave approved", an unread "leave
 * rejected" with the seeded reason and an unread "attendance updated"; Manoj has the two requests from
 * Meena and Karthik; Asha has the one from Lakshmi, whose School has no Manager.
 *
 * <p>Idempotent per user and per notification type: a type the user already has is not added again. The
 * attendance seeder marks days as a supervisor, so Tara usually already has an attendance notice made by the
 * real event path and none is added here.
 */
@Component
@Order(70)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
public class NotificationDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NotificationDevSeeder.class);

    private static final String REJECTION_REASON = "Annual exams are on that day. Please reschedule the appointment.";

    private final AppUserRepository users;
    private final TeacherDirectory teachers;
    private final NotificationService notifications;
    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationDevSeeder(
            AppUserRepository users,
            TeacherDirectory teachers,
            NotificationService notifications,
            NotificationRepository repository,
            Clock clock) {
        this.users = users;
        this.teachers = teachers;
        this.notifications = notifications;
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        int added = 0;

        Optional<AppUser> tara = users.findByPhone("9800000004");
        Optional<AppUser> manoj = users.findByPhone("9800000003");
        Optional<AppUser> asha = users.findByPhone("9800000001");

        if (tara.isPresent()) {
            UUID taraId = tara.get().getId();
            if (!repository.existsByRecipientUserIdAndType(taraId, NotificationType.LEAVE_DECIDED)) {
                var approved = notifications.create(
                        taraId,
                        NotificationType.LEAVE_DECIDED,
                        MessageFactory.leaveApproved(today.plusDays(21), today.plusDays(22)));
                notifications.markRead(taraId, approved.getId());
                notifications.create(
                        taraId,
                        NotificationType.LEAVE_DECIDED,
                        MessageFactory.leaveRejected(today.minusDays(6), today.minusDays(6), REJECTION_REASON));
                added += 2;
            }
            if (!repository.existsByRecipientUserIdAndType(taraId, NotificationType.ATTENDANCE_CHANGED)) {
                Optional<UUID> teacherId = teachers.teacherOfUser(taraId).map(t -> t.id());
                UUID actor = manoj.map(AppUser::getId).orElse(taraId);
                if (teacherId.isPresent()) {
                    notifications.attendanceChanged(taraId, actor, "Manoj Manager", teacherId.get(), today.minusDays(1));
                    added++;
                }
            }
        }
        if (manoj.isPresent()
                && !repository.existsByRecipientUserIdAndType(manoj.get().getId(), NotificationType.LEAVE_REQUESTED)) {
            added += requested(manoj.get().getId(), "Meena Selvi", today.plusDays(8), today.plusDays(10));
            added += requested(manoj.get().getId(), "Karthik Raja", today.plusDays(9), today.plusDays(10));
        }
        if (asha.isPresent()
                && !repository.existsByRecipientUserIdAndType(asha.get().getId(), NotificationType.LEAVE_REQUESTED)) {
            added += requested(asha.get().getId(), "Lakshmi Priya", today.plusDays(10), today.plusDays(10));
        }
        log.info("[DEV SEED] Notification demo data ready: {} notifications added.", added);
    }

    private int requested(UUID recipient, String teacherName, LocalDate first, LocalDate last) {
        notifications.create(
                recipient, NotificationType.LEAVE_REQUESTED, MessageFactory.leaveRequested(teacherName, first, last));
        return 1;
    }
}
