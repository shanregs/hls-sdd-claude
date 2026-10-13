package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 032 US5: the Admin and Director endpoints (MANAGER_ATTENDANCE and MANAGER_LEAVE) refuse everyone else. */
class ManagerAttendanceAuthorizationTest extends ManagerAttendanceTestBase {

    private static final String[] ENDPOINTS = {
        "grid", "manager-month", "correct", "complete-check-out", "regularization-list", "regularization-approve", "regularization-reject"
    };

    private Resp call(String endpoint, String token, UUID managerId, LocalDate day, UUID regularization) {
        String base = ATT + "/managers/" + managerId;
        return switch (endpoint) {
            case "grid" -> get(ATT + "/grid?month=" + lastMonth(), token);
            case "manager-month" -> get(base + "/months/" + YearMonth.from(day), token);
            case "correct" -> put(base + "/days/" + day, token, Map.of("reason", "Corrected", "checkOutTime", "17:30"));
            case "complete-check-out" -> post(base + "/days/" + day.minusDays(1) + "/complete-check-out", token, Map.of("time", "17:30", "reason", "x"));
            case "regularization-list" -> get(ATT + "/regularizations", token);
            case "regularization-approve" -> post(ATT + "/regularizations/" + regularization + "/approve", token, Map.of());
            case "regularization-reject" -> post(ATT + "/regularizations/" + regularization + "/reject", token, Map.of("reason", "No"));
            default -> throw new IllegalArgumentException(endpoint);
        };
    }

    private record Fixture(ManagerCtx manager, LocalDate day, UUID regularization) {}

    private Fixture fixture(String admin, int offset) {
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate day = today().minusDays(10 + offset);
        seedDay(manager.managerId(), day, instant(day, 9, 0), instant(day, 17, 0));
        LocalDate previous = day.minusDays(1);
        seedDay(manager.managerId(), previous, instant(previous, 9, 0), null);
        Map<String, Object> body = new HashMap<>();
        body.put("date", today().minusDays(2).toString());
        body.put("proposedTime", "09:15");
        body.put("dayType", "OFFICE");
        body.put("reason", "Phone was off");
        UUID regularization = post(ME + "/regularizations", manager.token(), body).id();
        return new Fixture(manager, day, regularization);
    }

    @Test
    void anAdminAndADirectorSucceedOnEveryEndpoint() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        for (String endpoint : ENDPOINTS) {
            for (String token : new String[] {admin, director}) {
                Fixture f = fixture(admin, Math.floorMod(endpoint.hashCode(), 7) + (token.equals(admin) ? 0 : 20));
                Resp resp = call(endpoint, token, f.manager().managerId(), f.day(), f.regularization());
                assertThat(resp.status()).as(endpoint + ": " + resp.body()).isEqualTo(200);
            }
        }
    }

    @Test
    void aManagerATeacherAndASystemUserGet403OnEveryEndpoint() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin, 1);
        Map<String, String> tokens = Map.of(
                "manager", f.manager().token(),
                "teacher", teacher().token(),
                "system", signInAs(Role.SYSTEM).token());
        for (String endpoint : ENDPOINTS) {
            tokens.forEach((who, token) -> assertThat(call(endpoint, token, f.manager().managerId(), f.day(), f.regularization()).status())
                    .as(who + " " + endpoint)
                    .isEqualTo(403));
        }
        assertThat(jdbc.queryForObject("select status from manager_regularization where id = ?", String.class, f.regularization()))
                .isEqualTo("PENDING");
    }

    @Test
    void theManagersOwnEndpointsRefuseAnAdminAndATeacher() {
        String admin = signInAs(Role.ADMIN).token();
        String teacher = teacher().token();
        String[] uris = {ME + "/months/" + lastMonth(), ME + "/regularizations"};
        for (String uri : uris) {
            assertThat(get(uri, admin).status()).as("admin " + uri).isEqualTo(403);
            assertThat(get(uri, teacher).status()).as("teacher " + uri).isEqualTo(403);
        }
    }
}
