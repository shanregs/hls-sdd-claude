package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 032 US1: the Manager's own endpoints are for a Manager with a Manager record, on their own data only. */
class MyManagerAttendanceAuthorizationTest extends ManagerAttendanceTestBase {

    private static final String[] ENDPOINTS = {"today", "check-in", "check-out", "complete", "photo-upload", "photo-open"};

    private Resp call(String endpoint, String token) {
        String day = today().minusDays(1).toString();
        return switch (endpoint) {
            case "today" -> get(ME + "/today", token);
            case "check-in" -> post(ME + "/check-in", token, here("OFFICE"));
            case "check-out" -> post(ME + "/check-out", token, place());
            case "complete" -> post(ME + "/days/" + day + "/complete-check-out", token, Map.of("time", "18:00", "reason", "x"));
            case "photo-upload" -> upload(ME + "/days/" + today() + "/photo", token, "a.png", PNG);
            case "photo-open" -> get(ME + "/days/" + today() + "/photo", token);
            default -> throw new IllegalArgumentException(endpoint);
        };
    }

    @Test
    void aManagerSucceedsOnTheirOwnDataOnly() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx a = joinedManager(admin);
        ManagerCtx b = joinedManager(admin);

        assertThat(call("today", a.token()).status()).isEqualTo(200);
        assertThat(call("check-in", a.token()).status()).isEqualTo(201);
        assertThat(call("check-out", a.token()).status()).isEqualTo(200);
        // B has no row: A's calls did not touch B's data
        assertThat(dayRows(b.managerId())).isZero();
        assertThat(call("check-out", b.token()).status()).isEqualTo(400);
        assertThat(call("today", b.token()).map().get("day")).isNull();
    }

    @Test
    void aTeacherGetsForbiddenOnEveryEndpoint() {
        String token = teacher().token();
        for (String endpoint : ENDPOINTS) {
            assertThat(call(endpoint, token).status()).as("teacher " + endpoint).isEqualTo(403);
        }
    }

    @Test
    void aSystemUserGetsForbiddenOnEveryEndpoint() {
        String token = signInAs(Role.SYSTEM).token();
        for (String endpoint : ENDPOINTS) {
            assertThat(call(endpoint, token).status()).as("system " + endpoint).isEqualTo(403);
        }
    }

    @Test
    void aManagerUserWithNoManagerRecordGetsForbiddenOnEveryEndpoint() {
        String token = signInAs(Role.MANAGER).token();
        for (String endpoint : ENDPOINTS) {
            assertThat(call(endpoint, token).status()).as("unlinked manager " + endpoint).isEqualTo(403);
        }
    }

    @Test
    void anAdminAndADirectorHaveNoAttendanceOfTheirOwn() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        for (String endpoint : ENDPOINTS) {
            assertThat(call(endpoint, admin).status()).as("admin " + endpoint).isEqualTo(403);
            assertThat(call(endpoint, director).status()).as("director " + endpoint).isEqualTo(403);
        }
    }

    @Test
    void anUnauthenticatedCallerIsRefused() {
        assertThat(get(ME + "/today", null).status()).isEqualTo(401);
    }
}
