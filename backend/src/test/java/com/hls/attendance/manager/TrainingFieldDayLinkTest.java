package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.attendance.api.ManagerTrainingDays;
import com.hls.identity.user.Role;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 024 T063: a Manager's assigned training session is a choice on the Field day, and attended days are readable. */
class TrainingFieldDayLinkTest extends ManagerAttendanceTestBase {

    @Autowired
    ManagerTrainingDays trainingDays;

    private UUID sessionToday(String admin) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "MONTHLY");
        body.put("topic", uniqueName("Field training"));
        body.put("date", today().toString());
        body.put("startsAt", "10:00");
        body.put("endsAt", "13:00");
        body.put("venueKind", "PHYSICAL");
        body.put("venue", "Head office");
        body.put("publish", true);
        Resp created = post("/api/v1/training/sessions", admin, body);
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        return created.id();
    }

    private void assign(UUID session, UUID manager) {
        jdbc.update(
                "insert into training_assignment (id, session_id, person_kind, person_id, source, status, assigned_by, assigned_at, version)"
                        + " values (?, ?, 'MANAGER', ?, 'INDIVIDUAL', 'REQUIRED', ?, now(), 0)",
                UUID.randomUUID(), session, manager, UUID.randomUUID());
    }

    @Test
    void anAssignedSessionIsAChoiceAndAnotherManagersIsRefused() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zone = zone(admin);
        ManagerCtx mine = newManager(admin, zone);
        ManagerCtx other = newManager(admin, zone);
        jdbc.update("update manager set joining_date = ? where id = ?", today().minusDays(60), mine.managerId());
        UUID session = sessionToday(admin);
        assign(session, mine.managerId());
        assertThat(trainingDays.assignedSessionsOn(mine.signed().userId(), today())).extracting(ManagerTrainingDays.TrainingSessionRef::sessionId)
                .containsExactly(session);
        assertThat(trainingDays.assignedSessionsOn(other.signed().userId(), today())).isEmpty();

        Map<String, Object> body = here("FIELD");
        body.put("activities", List.of(Map.of("kind", "TRAINING", "id", session.toString())));
        Resp ok = post(ME + "/check-in", mine.token(), body);
        assertThat(ok.status()).as(ok.body()).isEqualTo(201);

        jdbc.update("update manager set joining_date = ? where id = ?", today().minusDays(60), other.managerId());
        Resp refused = post(ME + "/check-in", other.token(), body);
        assertThat(refused.status()).as(refused.body()).isEqualTo(404);
    }

    @Test
    void attendedDaysAreWholeOrHalfPerPresentDay() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx m = newManager(admin, zone(admin));
        UUID session = sessionToday(admin);
        assign(session, m.managerId());
        Resp saved = put(
                "/api/v1/training/sessions/" + session + "/attendance", admin,
                Map.of("rows", List.of(Map.of("personKind", "MANAGER", "personId", m.managerId().toString(), "outcome", "PRESENT", "dayValue", 0.5))));
        assertThat(saved.status()).as(saved.body()).isEqualTo(200);
        Map<LocalDate, BigDecimal> days = trainingDays.attendedDays(m.managerId(), today().minusDays(1), today().plusDays(1));
        assertThat(days).containsOnlyKeys(today());
        assertThat(days.get(today())).isEqualByComparingTo("0.5");
        assertThat(trainingDays.attendedDays(m.managerId(), today().plusDays(1), today().plusDays(5))).isEmpty();
    }
}
