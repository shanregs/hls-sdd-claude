package com.hls.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.attendance.internal.CalendarService;
import com.hls.attendance.internal.MarkService;
import com.hls.attendance.internal.SetByKind;
import com.hls.attendance.internal.StatusCodeService;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionMatrixService;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.leave.internal.LeaveRequestService;
import com.hls.support.AttendanceTestBase;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The reference-data caches: reads come from memory, a change made through the application shows up at once, and a
 * change that is rolled back never stays visible.
 */
class ReferenceDataCacheTest extends AttendanceTestBase {

    @Autowired
    private SnapshotCaches caches;

    @Autowired
    private PermissionMatrixService matrix;

    @Autowired
    private StatusCodeService statusCodes;

    @Autowired
    private MarkService marks;

    @Autowired
    private CalendarService calendar;

    @Autowired
    private LeaveRequestService leave;

    @Autowired
    private PlatformTransactionManager transactions;

    private UUID admin() {
        return signInAs(Role.ADMIN).userId();
    }

    @Test
    void cachingIsOnForTheseTests() {
        assertThat(caches.enabled()).isTrue();
    }

    // ---- permission matrix

    @Test
    void manyPermissionChecksLoadTheMatrixAtMostOnce() {
        matrix.isGranted(Role.TEACHER, PermissionModule.DASHBOARD, PermissionAction.VIEW); // make sure it is loaded
        long before = caches.loads("permissionMatrix");

        for (int i = 0; i < 200; i++) {
            matrix.isGranted(Role.TEACHER, PermissionModule.DASHBOARD, PermissionAction.VIEW);
            matrix.isGranted(Role.MANAGER, PermissionModule.LEAVE_MANAGEMENT, PermissionAction.VIEW);
        }

        assertThat(caches.loads("permissionMatrix")).isEqualTo(before);
    }

    @Test
    void aChangedGrantIsSeenAtOnce() {
        UUID actor = admin();
        assertThat(matrix.isGranted(Role.TEACHER, PermissionModule.NOTIFICATIONS, PermissionAction.DELETE)).isTrue();
        try {
            assertThat(matrix.updateGrant(
                            Role.TEACHER, PermissionModule.NOTIFICATIONS, PermissionAction.DELETE, false, actor)
                    .success())
                    .isTrue();

            assertThat(matrix.isGranted(Role.TEACHER, PermissionModule.NOTIFICATIONS, PermissionAction.DELETE))
                    .isFalse();
        } finally {
            matrix.updateGrant(Role.TEACHER, PermissionModule.NOTIFICATIONS, PermissionAction.DELETE, true, actor);
        }
        assertThat(matrix.isGranted(Role.TEACHER, PermissionModule.NOTIFICATIONS, PermissionAction.DELETE)).isTrue();
    }

    @Test
    void aGrantChangeThatIsRolledBackNeverStaysVisible() {
        UUID actor = admin();
        TransactionTemplate tx = new TransactionTemplate(transactions);

        tx.executeWithoutResult(status -> {
            matrix.updateGrant(Role.TEACHER, PermissionModule.NOTIFICATIONS, PermissionAction.DELETE, false, actor);
            // A reader inside the transaction sees the uncommitted change and caches it ...
            assertThat(matrix.isGranted(Role.TEACHER, PermissionModule.NOTIFICATIONS, PermissionAction.DELETE))
                    .isFalse();
            status.setRollbackOnly();
        });

        // ... but once the transaction rolled back, the cache was dropped again.
        assertThat(matrix.isGranted(Role.TEACHER, PermissionModule.NOTIFICATIONS, PermissionAction.DELETE)).isTrue();
    }

    // ---- attendance status codes

    @Test
    void aNewStatusCodeCanBeUsedAtOnceAndADeactivatedOneIsRefusedAtOnce() {
        World w = newWorld();
        UUID actor = admin();
        String shortCode = "Q" + ThreadLocalRandom.current().nextInt(1000, 9999);
        var created = statusCodes.create(actor, shortCode, "Cache test", "WORKED", new BigDecimal("1.00"));
        UUID manager = w.managerA().signed().userId();
        UUID teacher = w.teacherA().teacherId();

        marks.setMark(
                manager, teacher, today().minusDays(2), shortCode, BigDecimal.ONE, null, null, SetByKind.SUPERVISOR);

        statusCodes.update(actor, created.id(), "Cache test", new BigDecimal("1.00"), false, created.version());
        assertThatThrownBy(() -> marks.setMark(
                        manager,
                        teacher,
                        today().minusDays(3),
                        shortCode,
                        BigDecimal.ONE,
                        null,
                        null,
                        SetByKind.SUPERVISOR))
                .hasMessageContaining("no longer in use");
    }

    // ---- calendar

    @Test
    void aHolidayAddedAndRemovedIsSeenAtOnceAndReadsComeFromMemory() {
        UUID actor = admin();
        LocalDate day = LocalDate.of(2031, 3, 14);
        calendar.nonWorkingDates(day, day);
        long before = caches.loads("attendanceCalendar");
        for (int i = 0; i < 50; i++) {
            calendar.nonWorkingDates(day.minusDays(10), day.plusDays(10));
            calendar.rules();
        }
        assertThat(caches.loads("attendanceCalendar")).isEqualTo(before);

        calendar.addNonWorkingDate(actor, day, "Cache test holiday");
        assertThat(calendar.nonWorkingDates(day.minusDays(1), day.plusDays(1))).containsExactly(day);

        calendar.removeNonWorkingDate(actor, day);
        assertThat(calendar.nonWorkingDates(day.minusDays(1), day.plusDays(1))).isEmpty();
    }

    @Test
    void aSchoolWeeklyOffOverrideIsSeenAtOnce() {
        World w = newWorld();
        UUID actor = admin();
        assertThat(calendar.rules().forSchool(w.schoolA())).doesNotContain(DayOfWeek.SATURDAY);

        calendar.putSchoolOverride(actor, w.schoolA(), List.of("SUN", "SAT"));
        assertThat(calendar.rules().forSchool(w.schoolA())).containsExactlyInAnyOrder(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY);

        calendar.removeSchoolOverride(actor, w.schoolA());
        assertThat(calendar.rules().forSchool(w.schoolA())).doesNotContain(DayOfWeek.SATURDAY);
    }

    @Test
    void anEmptyOrBackwardsRangeOfHolidaysIsEmpty() {
        assertThat(calendar.nonWorkingDates(LocalDate.of(2031, 5, 10), LocalDate.of(2031, 5, 1))).isEmpty();
    }

    // ---- leave types

    @Test
    void leaveTypesAreLoadedOnceNoMatterHowOftenTheyAreListed() {
        leave.activeTypes();
        long before = caches.loads("leaveTypes");

        for (int i = 0; i < 50; i++) {
            assertThat(leave.activeTypes()).isNotEmpty();
        }

        assertThat(caches.loads("leaveTypes")).isEqualTo(before);
    }

    // ---- the registry

    @Test
    void invalidateAllMakesEveryCacheLoadAgain() {
        matrix.isGranted(Role.TEACHER, PermissionModule.DASHBOARD, PermissionAction.VIEW);
        long before = caches.loads("permissionMatrix");

        caches.invalidateAll();
        matrix.isGranted(Role.TEACHER, PermissionModule.DASHBOARD, PermissionAction.VIEW);

        assertThat(caches.loads("permissionMatrix")).isEqualTo(before + 1);
    }
}
