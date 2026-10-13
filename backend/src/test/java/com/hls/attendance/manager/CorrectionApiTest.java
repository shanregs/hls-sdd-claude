package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 032 US5: an Admin or Director corrects a Manager's day with a reason; every version stays in the history. */
class CorrectionApiTest extends ManagerAttendanceTestBase {

    private String uri(UUID managerId, LocalDate date) {
        return ATT + "/managers/" + managerId + "/days/" + date;
    }

    private static Map<String, Object> body(String reason, Object... pairs) {
        Map<String, Object> m = new HashMap<>();
        m.put("reason", reason);
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], pairs[i + 1]);
        }
        return m;
    }

    @Test
    void anAdminAndADirectorCorrectTheTypeThePurposeAndTheTimes() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate d1 = today().minusDays(3);
        LocalDate d2 = today().minusDays(4);
        UUID day1 = seedDay(manager.managerId(), d1, instant(d1, 9, 0), instant(d1, 17, 0));
        seedDay(manager.managerId(), d2, instant(d2, 9, 0), null);

        Resp byAdmin = put(
                uri(manager.managerId(), d1),
                admin,
                body("It was a field day", "dayType", "FIELD", "purpose", "School visit", "checkInTime", "08:45", "checkOutTime", "18:10"));
        Resp byDirector = put(uri(manager.managerId(), d2), director, body("Left a note", "checkOutTime", "17:30"));

        assertThat(byAdmin.status()).as(byAdmin.body()).isEqualTo(200);
        assertThat(byAdmin.map()).containsEntry("dayType", "FIELD").containsEntry("purpose", "School visit");
        assertThat((List<Object>) byAdmin.map().get("flags")).contains("CORRECTED");
        assertThat(byDirector.status()).as(byDirector.body()).isEqualTo(200);
        assertThat(byDirector.map().get("checkOutAt")).isNotNull();
        assertThat(jdbc.queryForObject("select corrected from manager_day where id = ?", Boolean.class, day1)).isTrue();
        assertThat(jdbc.queryForObject("select day_type from manager_day where id = ?", String.class, day1)).isEqualTo("FIELD");
    }

    @Test
    void aMissingDayIsAddedWithATypeAndATimeAndAMistakenDayIsCleared() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate none = today().minusDays(5);
        LocalDate wrong = today().minusDays(6);
        seedDay(manager.managerId(), wrong, instant(wrong, 9, 0), instant(wrong, 17, 0));

        Resp added = put(uri(manager.managerId(), none), admin, body("Forgot to tap in", "dayType", "OFFICE", "checkInTime", "09:10"));
        Resp cleared = put(uri(manager.managerId(), wrong), admin, body("Tapped by mistake", "clear", true));

        assertThat(added.status()).as(added.body()).isEqualTo(200);
        assertThat(added.map().get("checkInAt")).isNotNull();
        assertThat(cleared.status()).as(cleared.body()).isEqualTo(200);
        assertThat(cleared.map().get("checkInAt")).isNull();
        assertThat(dayRows(manager.managerId())).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "select check_in_at is null from manager_day where manager_id = ? and day_date = ?",
                        Boolean.class,
                        manager.managerId(),
                        wrong))
                .isTrue();
    }

    @Test
    void theReasonIsRequiredAndTheInputIsChecked() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate day = today().minusDays(2);
        seedDay(manager.managerId(), day, instant(day, 9, 0), null);
        String uri = uri(manager.managerId(), day);

        assertThat(put(uri, admin, Map.of("checkOutTime", "17:00")).status()).isEqualTo(400);
        assertThat(put(uri, admin, body(" ", "checkOutTime", "17:00")).status()).isEqualTo(400);
        assertThat(put(uri, admin, body("x".repeat(501), "checkOutTime", "17:00")).status()).isEqualTo(400);
        assertThat(put(uri, admin, body("Nothing else given")).status()).isEqualTo(400);
        assertThat(put(uri, admin, body("Field without basis", "dayType", "FIELD")).status()).isEqualTo(400);
        assertThat(put(uri, admin, body("Before the check-in", "checkOutTime", "08:00")).status()).isEqualTo(400);
        assertThat(put(uri, admin, body("Bad time", "checkOutTime", "late")).status()).isEqualTo(400);
        assertThat(put(uri(manager.managerId(), today().plusDays(1)), admin, body("Future", "dayType", "OFFICE", "checkInTime", "09:00"))
                        .status())
                .isEqualTo(400);
        assertThat(put(uri(UUID.randomUUID(), day), admin, body("No such Manager", "checkOutTime", "17:00")).status())
                .isEqualTo(404);
        assertThat(historyRows(manager.managerId(), "CORRECT")).isZero();
    }

    @Test
    void theOriginalAndEachCorrectionStayInTheHistoryWithWhoAndWhen() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate day = today().minusDays(3);
        YearMonth month = YearMonth.from(day);
        UUID id = seedDay(manager.managerId(), day, instant(day, 9, 0), null);
        // a seeded day has no history row, so the first correction also keeps the original
        put(uri(manager.managerId(), day), admin, body("First", "checkOutTime", "17:00"));
        put(uri(manager.managerId(), day), admin, body("Second", "checkOutTime", "18:00"));

        Resp detail = get(ATT + "/managers/" + manager.managerId() + "/months/" + month, admin);

        assertThat(detail.status()).as(detail.body()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) dayOf(detail, day).get("history");
        assertThat(history).hasSize(2);
        Map<String, Object> last = history.get(history.size() - 1);
        assertThat(last).containsEntry("reason", "Second").containsEntry("action", "CORRECT");
        assertThat(last.get("changedAt")).isNotNull();
        assertThat(last.get("changedByName")).isNotNull();
        assertThat(historyRows(manager.managerId(), "CORRECT")).isEqualTo(2);
        String original = jdbc.queryForObject(
                "select before_json from manager_day_history where day_id = ? order by changed_at asc limit 1", String.class, id);
        assertThat(original).contains("checkInAt");
    }

    @Test
    void aCheckedInDaysFirstHistoryRowHoldsTheOriginalAndACorrectionFollowsIt() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = joinedManager(admin);
        checkIn(manager.token(), "OFFICE");
        UUID id = jdbc.queryForObject("select id from manager_day where manager_id = ?", UUID.class, manager.managerId());

        Resp resp = put(
                uri(manager.managerId(), today()),
                admin,
                body("It was a field day", "dayType", "FIELD", "purpose", "School visit"));

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        List<String> actions = jdbc.queryForList(
                "select action from manager_day_history where day_id = ? order by changed_at asc", String.class, id);
        assertThat(actions).containsExactly("CHECK_IN", "CORRECT");
    }

    @Test
    void eachCorrectionAndCompletionWritesAnAuditEntryWithThePriorAndNewValues() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate d1 = today().minusDays(3);
        LocalDate d2 = today().minusDays(4);
        UUID day1 = seedDay(manager.managerId(), d1, instant(d1, 9, 0), instant(d1, 17, 0));
        UUID day2 = seedDay(manager.managerId(), d2, instant(d2, 9, 0), null);

        put(uri(manager.managerId(), d1), admin, body("Wrong type", "dayType", "FIELD", "purpose", "Audit purpose"));
        post(uri(manager.managerId(), d2) + "/complete-check-out", admin, Map.of("time", "18:00", "reason", "Left a note"));

        assertChangeRecorded(admin, "MANAGER_DAY", day1, "dayType");
        assertChangeRecorded(admin, "MANAGER_DAY", day1, "correctionReason");
        String audit1 = auditBody(admin, "MANAGER_DAY", day1);
        assertThat(audit1).contains("OFFICE").contains("FIELD").contains("Audit purpose").contains("Wrong type");
        assertChangeRecorded(admin, "MANAGER_DAY", day2, "completeCheckOut");
    }

    @Test
    void theManagerIsNotifiedOfACorrection() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate day = today().minusDays(2);
        seedDay(manager.managerId(), day, instant(day, 9, 0), null);

        put(uri(manager.managerId(), day), admin, body("Added", "checkOutTime", "17:00"));

        assertThat(notifications(manager.signed().userId(), "MANAGER_DAY_CORRECTED")).isEqualTo(1);
    }

    @Test
    void aCorrectionInALockedMonthIs409AndChangesNothing() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate day = today().minusDays(2);
        UUID id = seedDay(manager.managerId(), day, instant(day, 9, 0), null);
        lockMonth(manager.managerId(), YearMonth.from(day));

        Resp resp = put(uri(manager.managerId(), day), admin, body("Late fix", "checkOutTime", "17:00"));

        assertThat(resp.status()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select check_out_at is null from manager_day where id = ?", Boolean.class, id))
                .isTrue();
        assertThat(historyRows(manager.managerId(), "CORRECT")).isZero();
    }

    @Test
    void aManagerAndATeacherCannotCorrect() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate day = today().minusDays(2);
        seedDay(manager.managerId(), day, instant(day, 9, 0), null);

        assertThat(put(uri(manager.managerId(), day), manager.token(), body("Self", "checkOutTime", "17:00")).status())
                .isEqualTo(403);
        assertThat(put(uri(manager.managerId(), day), teacher().token(), body("T", "checkOutTime", "17:00")).status())
                .isEqualTo(403);
        assertThat(historyRows(manager.managerId(), "CORRECT")).isZero();
    }
}
