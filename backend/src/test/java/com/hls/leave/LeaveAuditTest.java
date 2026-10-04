package com.hls.leave;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 009 US5 / FR-011: every transition and every leave-made mark is in Change History, scoped by permission. */
class LeaveAuditTest extends LeaveTestBase {

    private static final String HISTORY = "/api/v1/audit/change-history?size=200&entityType=";

    /** Change History is filled after the transaction commits, so give it a moment. */
    private String history(String token, String entityType, String mustContain) throws InterruptedException {
        String body = "";
        for (int i = 0; i < 50; i++) {
            body = get(HISTORY + entityType, token).body();
            if (mustContain == null || body.contains(mustContain)) {
                return body;
            }
            Thread.sleep(200);
        }
        return body;
    }

    @Test
    void everyTransitionIsRecordedWithItsReasonForAdmin() throws Exception {
        World w = newWorld();
        LocalDate mon = monday();
        String approved = apply(w.teacherA(), mon, mon.plusDays(1));
        String rejected = apply(w.teacherA(), mon.plusDays(7), mon.plusDays(8));
        String cancelled = apply(w.teacherA(), mon.plusDays(14), mon.plusDays(15));
        String revoked = apply(w.teacherA(), mon.plusDays(3), mon.plusDays(4));

        post(SUP + "/" + approved + "/approve", w.managerA().token(), Map.of("note", "Enjoy", "version", 0));
        post(SUP + "/" + rejected + "/reject", w.managerA().token(), Map.of("reason", "Exams that week", "version", 0));
        post(ME + "/" + cancelled + "/cancel", w.teacherA().token(), Map.of());
        post(SUP + "/" + revoked + "/approve", w.managerA().token(), Map.of("version", 0));
        post(SUP + "/" + revoked + "/revoke", w.managerA().token(), Map.of("reason", "Needed at school", "version", 1));

        String log = history(w.admin(), "LEAVE_REQUEST", "Needed at school");

        for (String id : new String[] {approved, rejected, cancelled, revoked}) {
            assertThat(log).as("created " + id).contains(id);
        }
        assertThat(log)
                .contains("created")
                .contains("APPROVED - Enjoy")
                .contains("REJECTED - Exams that week")
                .contains("CANCELLED - cancelled by the Teacher")
                .contains("CANCELLED - revoked: Needed at school");
        assertThat(log).contains(w.teacherA().teacherId().toString());
    }

    @Test
    void marksMadeByLeaveNameTheRequestInTheAttendanceHistory() throws Exception {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));
        post(SUP + "/" + id + "/approve", w.managerA().token(), Map.of("version", 0));

        String log = history(w.admin(), "ATTENDANCE_MARK", "leave request " + id);

        assertThat(log).contains("leave request " + id);
    }

    @Test
    void systemAndTheOtherRolesDoNotSeeLeaveEntries() throws Exception {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));
        history(w.admin(), "LEAVE_REQUEST", id);

        String system = signInAs(Role.SYSTEM).token();
        assertThat(get(HISTORY + "LEAVE_REQUEST", system).body()).doesNotContain(id);
        for (String token : new String[] {w.teacherA().token(), w.managerA().token()}) {
            Resp resp = get(HISTORY + "LEAVE_REQUEST", token);
            assertThat(resp.status() == 403 || !resp.body().contains(id)).isTrue();
        }
    }
}
