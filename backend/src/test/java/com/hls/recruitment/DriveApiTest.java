package com.hls.recruitment;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The per-role and per-scope matrix of contracts/recruitment-api.md for colleges, drives, candidates and outcomes. */
class DriveApiTest extends RecruitmentTestBase {

    @Test
    void aDuplicateCollegeIsRefusedAndTheListShowsBothContacts() {
        String admin = admin();
        String name = uniqueName("DupCollege");
        Resp first = post(BASE + "/colleges", admin, Map.of("name", name, "city", "Chennai",
                "placementOfficer", Map.of("name", "P. Officer", "email", "po@college.test"),
                "principal", Map.of("name", "Dr. Principal")));
        assertThat(first.status()).as(first.body()).isEqualTo(201);

        Resp list = get(BASE + "/colleges?query=" + name, admin);
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body()).contains(first.id().toString(), "P. Officer", "Dr. Principal", "po@college.test");

        Resp duplicate = post(BASE + "/colleges", admin, Map.of("name", name.toUpperCase(), "city", "chennai"));
        assertThat(duplicate.status()).isEqualTo(409);
        Resp noName = post(BASE + "/colleges", admin, Map.of("name", " ", "city", "Chennai"));
        assertThat(noName.status()).isEqualTo(400);
    }

    @Test
    void aDriveNeedsACollegeAndADateAndCancellingNeedsAReason() {
        String admin = admin();
        UUID college = college(admin);

        assertThat(post(BASE + "/drives", admin, Map.of("collegeId", college, "dates", List.of())).status()).isEqualTo(400);
        assertThat(post(BASE + "/drives", admin, Map.of("dates", List.of(today.toString()))).status()).isEqualTo(400);

        UUID drive = drive(admin, college, List.of(), today.plusDays(2));
        Resp noReason = post(BASE + "/drives/" + drive + "/status", admin, Map.of("status", "CANCELLED"));
        assertThat(noReason.status()).isEqualTo(400);
        Resp cancelled = post(BASE + "/drives/" + drive + "/status", admin, Map.of("status", "CANCELLED", "reason", "Exams"));
        assertThat(cancelled.status()).as(cancelled.body()).isEqualTo(200);
        assertThat(cancelled.map()).containsEntry("status", "CANCELLED");
        assertThat(addCandidate(admin, drive, "Late Joiner", phone()).status()).isEqualTo(409);
    }

    @Test
    void aPastDriveWithNoCandidatesIsShownAsHeldWithNoCandidates() {
        String admin = admin();
        UUID drive = drive(admin, college(admin), List.of(), today.minusDays(3));

        Map<String, Object> view = get(BASE + "/drives/" + drive, admin).map();

        assertThat(view).containsEntry("status", "PLANNED").containsEntry("heldNoCandidates", true);
    }

    @Test
    void aZoneManagerReadsEveryDriveButChangesOnlyOwnOrAttended() {
        String admin = admin();
        Signed manager = signInAs(Role.MANAGER);
        Signed other = signInAs(Role.MANAGER);
        UUID college = college(admin);
        UUID adminsDrive = drive(admin, college, List.of(), today.plusDays(5));
        UUID attended = drive(admin, college, List.of(manager.userId()), today.plusDays(6));
        Resp own = post(BASE + "/drives", manager.token(), driveBody(college, List.of(), today.plusDays(7)));
        assertThat(own.status()).as(own.body()).isEqualTo(201);

        assertThat(get(BASE + "/drives/" + adminsDrive, manager.token()).status()).isEqualTo(200);
        Resp refused = post(BASE + "/drives/" + adminsDrive + "/status", manager.token(), Map.of("status", "HELD"));
        assertThat(refused.status()).isEqualTo(403);
        assertThat(refused.body()).contains("Not your drive");
        assertThat(addCandidate(manager.token(), adminsDrive, "Not Mine", phone()).status()).isEqualTo(403);
        assertThat(post(BASE + "/drives/" + attended + "/status", manager.token(), Map.of("status", "HELD")).status()).isEqualTo(200);
        assertThat(post(BASE + "/drives/" + own.id() + "/status", manager.token(), Map.of("status", "HELD")).status()).isEqualTo(200);
        assertThat(post(BASE + "/drives/" + own.id() + "/status", other.token(), Map.of("status", "HELD")).status()).isEqualTo(403);

        Resp mine = get(BASE + "/drives?mine=true", manager.token());
        assertThat(mine.body()).contains(attended.toString(), own.id().toString()).doesNotContain(adminsDrive.toString());
    }

    @Test
    void teacherAndSystemAreRefusedAndNoTokenIs401() {
        String admin = admin();
        UUID drive = drive(admin);
        UUID candidate = candidate(admin, drive);

        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            String token = signInAs(role).token();
            assertThat(get(BASE + "/colleges", token).status()).as(role + " colleges").isEqualTo(403);
            assertThat(get(BASE + "/drives", token).status()).as(role + " drives").isEqualTo(403);
            assertThat(get(BASE + "/candidates", token).status()).as(role + " candidates").isEqualTo(403);
            assertThat(post(BASE + "/drives", token, Map.of("dates", List.of(today.toString()))).status()).isEqualTo(403);
            assertThat(outcome(token, candidate, "SELECTED").status()).isEqualTo(403);
            assertThat(get(BASE + "/candidates/" + candidate + "/history", token).status()).isEqualTo(403);
        }
        assertThat(get(BASE + "/drives", null).status()).isEqualTo(401);
    }

    @Test
    void directorAndAdminChangeAnyDriveAndCandidate() {
        String admin = admin();
        String director = directorToken();
        UUID drive = drive(director);
        UUID candidate = candidate(admin, drive);

        assertThat(outcome(director, candidate, "SELECTED").status()).isEqualTo(200);
        assertThat(post(BASE + "/drives/" + drive + "/status", admin, Map.of("status", "HELD")).status()).isEqualTo(200);
    }

    @Test
    void theInterviewerChoicesListStaffUsersAndNobodyElse() {
        String admin = admin();
        Signed manager = signInAs(Role.MANAGER);
        Signed teacher = signInAs(Role.TEACHER);

        Resp resp = get(BASE + "/interviewers", manager.token());

        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.body()).contains(manager.userId().toString()).doesNotContain(teacher.userId().toString());
        assertThat(get(BASE + "/interviewers", signInAs(Role.TEACHER).token()).status()).isEqualTo(403);
        assertThat(admin).isNotBlank();
    }
}
