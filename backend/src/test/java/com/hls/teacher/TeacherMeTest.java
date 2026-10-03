package com.hls.teacher;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.MasterDataTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** User Story 7 (FR-024): a Teacher sees only their own profile, with no salary. */
class TeacherMeTest extends MasterDataTestBase {

    @Test
    void aLinkedTeacherSeesTheirOwnDetailsAndCurrentSchoolButNoSalaryOrManagerHistory() {
        String admin = signInAs(Role.ADMIN).token();
        Signed teacherUser = signInAs(Role.TEACHER);
        UUID school = schoolInNewZone(admin)[2];
        UUID teacher = post(
                        "/api/v1/teachers",
                        admin,
                        Map.of(
                                "name",
                                uniqueName("Tara"),
                                "phone",
                                "9123456789",
                                "email",
                                "tara@example.com",
                                "status",
                                "ACTIVE",
                                "userId",
                                teacherUser.userId()))
                .id();
        placeTeacher(admin, teacher, school);
        post("/api/v1/teachers/" + teacher + "/salary", admin, Map.of("amount", 25000, "effectiveOn", "2026-01-01"));

        Resp mine = get("/api/v1/teachers/me", teacherUser.token());

        assertThat(mine.status()).isEqualTo(200);
        assertThat(mine.map())
                .containsEntry("id", teacher.toString())
                .containsEntry("email", "tara@example.com")
                .containsEntry("status", "ACTIVE");
        assertThat(mine.body()).contains(school.toString());
        assertThat(mine.body()).doesNotContainIgnoringCase("salary").doesNotContain("25000");
        assertThat(mine.map()).containsEntry("placements", null).containsEntry("manager", null);
    }

    @Test
    void aTeacherUserWithNoRecordGetsAClearMessage() {
        String token = signInAs(Role.TEACHER).token();

        Resp resp = get("/api/v1/teachers/me", token);

        assertThat(resp.status()).isEqualTo(404);
        assertThat(resp.body()).contains("Your profile has not been set up yet.");
    }

    @Test
    void aTeacherCannotReachManagementEndpointsOrAnotherTeachersRecord() {
        String admin = signInAs(Role.ADMIN).token();
        Signed teacherUser = signInAs(Role.TEACHER);
        UUID other = teacher(admin);
        post("/api/v1/teachers", admin, Map.of("name", uniqueName("Mine"), "userId", teacherUser.userId()));

        assertThat(get("/api/v1/teachers", teacherUser.token()).status()).isEqualTo(403);
        assertThat(get("/api/v1/teachers/" + other, teacherUser.token()).status()).isEqualTo(403);
        assertThat(get("/api/v1/schools", teacherUser.token()).status()).isEqualTo(403);
        assertThat(get("/api/v1/zones", teacherUser.token()).status()).isEqualTo(403);
        assertThat(get("/api/v1/teachers/" + other + "/salary", teacherUser.token()).status()).isEqualTo(403);
        // /me always resolves to the caller, never to the id of another Teacher
        assertThat(get("/api/v1/teachers/me", teacherUser.token()).body()).doesNotContain(other.toString());
        assertThat(get("/api/v1/teachers/me", null).status()).isEqualTo(401);
    }
}
