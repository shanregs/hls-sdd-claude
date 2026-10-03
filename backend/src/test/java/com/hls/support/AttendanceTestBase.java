package com.hls.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.attendance.internal.BusinessCalendar;
import com.hls.identity.user.Role;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Helpers shared by the spec 008 integration tests. "Today" always comes from {@link BusinessCalendar}
 * (Asia/Kolkata); Teachers are placed well in the past so a placement is never "scheduled" because the
 * UTC date and the Indian date differ near midnight.
 */
public abstract class AttendanceTestBase extends MasterDataTestBase {

    @Autowired
    protected BusinessCalendar businessCalendar;

    /** A Teacher record linked to a signed-in Teacher user. */
    public record TeacherCtx(Signed signed, UUID teacherId) {
        public String token() {
            return signed.token();
        }
    }

    /** An Admin, a Director, two Managers with disjoint Schools, and a Teacher placed at each School. */
    public record World(
            String admin,
            String director,
            ManagerCtx managerA,
            ManagerCtx managerB,
            UUID schoolA,
            UUID schoolB,
            UUID zoneA,
            UUID zoneB,
            TeacherCtx teacherA,
            TeacherCtx teacherB) {}

    protected LocalDate today() {
        return businessCalendar.today();
    }

    /** Creates a Teacher linked to a Teacher user and, when a School is given, placed there since {@code daysAgo}. */
    protected TeacherCtx newTeacher(String adminToken, UUID schoolId, int daysAgo) {
        Signed signed = signInAs(Role.TEACHER);
        UUID teacherId = teacher(adminToken);
        Resp linked = put("/api/v1/teachers/" + teacherId + "/user", adminToken, Map.of("userId", signed.userId()));
        assertThat(linked.status()).as(linked.body()).isEqualTo(200);
        if (schoolId != null) {
            Resp placed = placeTeacherRaw(adminToken, teacherId, schoolId, LocalDate.now().minusDays(daysAgo));
            assertThat(placed.status()).as(placed.body()).isEqualTo(200);
        }
        return new TeacherCtx(signed, teacherId);
    }

    protected World newWorld() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        UUID[] a = schoolInNewZone(admin);
        UUID[] b = schoolInNewZone(admin);
        ManagerCtx managerA = newManager(admin, a[0]);
        ManagerCtx managerB = newManager(admin, b[0]);
        assignSchoolManager(admin, a[2], managerA.managerId());
        assignSchoolManager(admin, b[2], managerB.managerId());
        return new World(
                admin,
                director,
                managerA,
                managerB,
                a[2],
                b[2],
                a[0],
                b[0],
                newTeacher(admin, a[2], 60),
                newTeacher(admin, b[2], 60));
    }

    protected Resp exitTeacher(String adminToken, UUID teacherId, LocalDate effectiveOn) {
        return post(
                "/api/v1/teachers/" + teacherId + "/status",
                adminToken,
                Map.of("status", "EXITED", "effectiveOn", effectiveOn.toString()));
    }
}
