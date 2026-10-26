package com.hls.activitycalendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.activitycalendar.internal.TrainingSource;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 024 T058 (SC-007): the real training source of the Activity Calendar, replacing the placeholder of spec 035. */
class TrainingSourceTest extends ActivityCalendarTestBase {

    @Autowired
    TrainingSource source;

    private static LocalDate feb(int day) {
        return LocalDate.of(2020, 2, day);
    }

    private UUID heldSessionOn(String adminToken, LocalDate day, UUID managerId, String status) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "MONTHLY");
        body.put("topic", uniqueName("Calendar training"));
        body.put("date", LocalDate.now().plusDays(40).toString());
        body.put("startsAt", "10:00");
        body.put("endsAt", "13:00");
        body.put("venueKind", "PHYSICAL");
        body.put("venue", "Head office");
        body.put("publish", true);
        Resp created = post("/api/v1/training/sessions", adminToken, body);
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        UUID session = created.id();
        jdbc.update("update training_session set session_date = ?, status = ? where id = ?", day, status, session);
        if (managerId != null) {
            jdbc.update(
                    "insert into training_assignment (id, session_id, person_kind, person_id, source, status, assigned_by, assigned_at, version)"
                            + " values (?, ?, 'MANAGER', ?, 'INDIVIDUAL', 'REQUIRED', ?, now(), 0)",
                    UUID.randomUUID(), session, managerId, UUID.randomUUID());
        }
        return session;
    }

    @Test
    void theRealSourceIsAvailable() {
        assertThat(source.available()).isTrue();
        assertThat(source.group()).isEqualTo("TRAINING");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aManagerSeesTheirSessionWithTheRightStatusAndNotAnotherManagers() {
        String admin = admin();
        UUID zone = zone(admin);
        ManagerCtx a = newManager(admin, zone);
        ManagerCtx b = newManager(admin, zone);
        UUID mine = heldSessionOn(admin, feb(10), a.managerId(), "HELD");
        UUID theirs = heldSessionOn(admin, feb(11), b.managerId(), "PUBLISHED");
        UUID cancelled = heldSessionOn(admin, feb(12), a.managerId(), "CANCELLED");

        Resp resp = calendar(a.token(), "2020-02");
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(item(resp, mine)).containsEntry("kind", "TRAINING").containsEntry("status", "DONE").containsEntry("date", "2020-02-10");
        assertThat(item(resp, cancelled)).containsEntry("status", "CANCELLED");
        assertThat(items(resp)).extracting(i -> i.get("activityId")).doesNotContain(theirs.toString());
        assertThat(resp.body()).contains("\"group\":\"TRAINING\"").doesNotContain("not included yet");

        Resp director = calendar(directorToken(), "2020-02");
        assertThat(item(director, theirs)).containsEntry("kind", "TRAINING");
        assertThat(item(director, theirs).get("link")).isEqualTo(Map.of("screen", "TRAINING", "path", "/operations/training"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theAdminOwnerNeverAppearsAmongTheManagers() {
        String admin = admin();
        UUID zone = zone(admin);
        ManagerCtx a = newManager(admin, zone);
        UUID session = heldSessionOn(admin, feb(17), a.managerId(), "HELD");
        UUID owner = jdbc.queryForObject("select created_by from training_session where id = ?", UUID.class, session);

        Resp director = calendar(directorToken(), "2020-02");
        List<Map<String, Object>> byManager = (List<Map<String, Object>>) summary(director).get("byManager");
        assertThat(byManager).extracting(m -> m.get("userId")).doesNotContain(owner.toString());
        assertThat(byManager).extracting(m -> m.get("userId")).contains(a.signed().userId().toString());
    }
}
