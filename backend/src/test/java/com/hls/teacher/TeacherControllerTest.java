package com.hls.teacher;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.MasterDataTestBase;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** User Story 4 (FR-011..FR-016): Teacher CRUD, status, account link, Manager scope, per-role authorization. */
class TeacherControllerTest extends MasterDataTestBase {

    private Map<String, Object> contact(Map<String, Object> current, String key, String value) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", current.get("name"));
        body.put("phone", current.get("phone"));
        body.put("email", current.get("email"));
        body.put("address", current.get("address"));
        body.put("version", current.get("version"));
        body.put(key, value);
        return body;
    }

    @Test
    void adminAndDirectorCreateEditAndListTeachersWithNoSalaryField() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        String name = uniqueName("Tara");

        Resp created = post(
                "/api/v1/teachers",
                director,
                Map.of("name", name, "phone", "9555555555", "email", "t@example.com", "status", "IN_TRAINING"));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.map()).containsEntry("status", "IN_TRAINING").containsEntry("name", name);
        assertThat(created.body()).doesNotContainIgnoringCase("salary");
        UUID id = created.id();

        Resp edit = put("/api/v1/teachers/" + id, admin, contact(created.map(), "phone", "9666666666"));
        assertThat(edit.status()).isEqualTo(200);
        assertThat(edit.map()).containsEntry("phone", "9666666666");
        assertThat(get("/api/v1/teachers?query=" + name, admin).body()).contains(id.toString());
        assertThat(get("/api/v1/teachers?status=ACTIVE&query=" + name, admin).body()).doesNotContain(id.toString());
        assertChangeRecorded(admin, "TEACHER", id, "phone");
        assertThat(post("/api/v1/teachers", admin, Map.of("name", " ")).status()).isEqualTo(400);
        assertThat(post("/api/v1/teachers", admin, Map.of("name", "X", "status", "EXITED")).status())
                .isEqualTo(400);
    }

    @Test
    void statusFollowsTheMachineAndRejectsEverythingElse() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = post("/api/v1/teachers", admin, Map.of("name", uniqueName("Stat"), "status", "IN_TRAINING"))
                .id();
        String path = "/api/v1/teachers/" + teacher + "/status";

        assertThat(post(path, admin, Map.of("status", "ON_LEAVE")).status()).isEqualTo(409);
        assertThat(post(path, admin, Map.of("status", "ACTIVE")).status()).isEqualTo(200);
        assertThat(post(path, admin, Map.of("status", "IN_TRAINING")).status()).isEqualTo(409);
        assertThat(post(path, admin, Map.of("status", "ON_LEAVE")).status()).isEqualTo(200);
        assertThat(post(path, admin, Map.of("status", "ACTIVE")).status()).isEqualTo(200);
        Resp exit = post(path, admin, Map.of("status", "EXITED"));
        assertThat(exit.status()).isEqualTo(200);
        Resp back = post(path, admin, Map.of("status", "ACTIVE"));
        assertThat(back.status()).isEqualTo(409);
        assertThat(back.body()).contains("cannot move from EXITED");
        assertThat(post(path, admin, Map.of("status", "nonsense")).status()).isEqualTo(400);
        assertChangeRecorded(admin, "TEACHER", teacher, "status");
    }

    @Test
    void linkingAnAccountRequiresTheTeacherRoleIsUniqueAndIsReleasedOnExit() {
        String admin = signInAs(Role.ADMIN).token();
        Signed teacherUser = signInAs(Role.TEACHER);
        Signed managerUser = signInAs(Role.MANAGER);
        UUID first = post("/api/v1/teachers", admin, Map.of("name", uniqueName("Linked"), "status", "ACTIVE")).id();
        UUID second = teacher(admin);

        assertThat(put("/api/v1/teachers/" + first + "/user", admin, Map.of("userId", managerUser.userId()))
                        .status())
                .isEqualTo(400);
        Resp linked = put("/api/v1/teachers/" + first + "/user", admin, Map.of("userId", teacherUser.userId()));
        assertThat(linked.status()).isEqualTo(200);
        assertThat(linked.body()).contains(teacherUser.userId().toString());
        Resp duplicate = put("/api/v1/teachers/" + second + "/user", admin, Map.of("userId", teacherUser.userId()));
        assertThat(duplicate.status()).isEqualTo(409);

        post("/api/v1/teachers/" + first + "/status", admin, Map.of("status", "EXITED"));
        assertThat(get("/api/v1/teachers/" + first, admin).map()).containsEntry("userId", null);
        // the released account can now be linked to a new record
        assertThat(put("/api/v1/teachers/" + second + "/user", admin, Map.of("userId", teacherUser.userId()))
                        .status())
                .isEqualTo(200);
        assertThat(put("/api/v1/teachers/" + first + "/user", admin, Map.of("userId", teacherUser.userId()))
                        .status())
                .isEqualTo(409);
    }

    @Test
    void accountCandidatesAreActiveTeacherUsersNotYetLinkedAndOnlyForAdminAndDirector() {
        String admin = signInAs(Role.ADMIN).token();
        Signed free = signInAs(Role.TEACHER);
        Signed linked = signInAs(Role.TEACHER);
        Signed notATeacher = signInAs(Role.MANAGER);
        post("/api/v1/teachers", admin, Map.of("name", uniqueName("L"), "userId", linked.userId()));

        Resp resp = get("/api/v1/teachers/candidates", signInAs(Role.DIRECTOR).token());

        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.body())
                .contains(free.userId().toString())
                .doesNotContain(linked.userId().toString(), notATeacher.userId().toString());
        assertThat(get("/api/v1/teachers/candidates", signInAs(Role.MANAGER).token()).status())
                .isEqualTo(403);
        assertThat(get("/api/v1/teachers/candidates", signInAs(Role.TEACHER).token()).status())
                .isEqualTo(403);
    }

    @Test
    void creatingWithAUserIdLinksTheAccount() {
        String admin = signInAs(Role.ADMIN).token();
        Signed teacherUser = signInAs(Role.TEACHER);

        Resp created = post(
                "/api/v1/teachers",
                admin,
                Map.of("name", uniqueName("WithUser"), "status", "ACTIVE", "userId", teacherUser.userId()));

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body()).contains(teacherUser.userId().toString());
    }

    @Test
    void aManagerSeesAndEditsOnlyTeachersPlacedInTheirSchoolsAndCannotDoAnythingElse() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] mine = schoolInNewZone(admin);
        UUID[] theirs = schoolInNewZone(admin);
        ManagerCtx manager = newManager(admin, mine[0]);
        assignSchoolManager(admin, mine[2], manager.managerId());
        UUID inMine = teacher(admin);
        UUID elsewhere = teacher(admin);
        UUID unplaced = teacher(admin);
        placeTeacher(admin, inMine, mine[2]);
        placeTeacher(admin, elsewhere, theirs[2]);

        Resp list = get("/api/v1/teachers?size=100", manager.token());
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body()).contains(inMine.toString()).doesNotContain(elsewhere.toString(), unplaced.toString());
        assertThat(get("/api/v1/teachers/" + elsewhere, manager.token()).status()).isEqualTo(404);
        assertThat(get("/api/v1/teachers/" + unplaced, manager.token()).status()).isEqualTo(404);
        assertThat(get("/api/v1/teachers/" + unplaced, admin).status()).isEqualTo(200);

        Map<String, Object> current = get("/api/v1/teachers/" + inMine, manager.token()).map();
        assertThat(put("/api/v1/teachers/" + inMine, manager.token(), contact(current, "phone", "9777777777")).status())
                .isEqualTo(200);
        Resp rename = put(
                "/api/v1/teachers/" + inMine,
                manager.token(),
                contact(get("/api/v1/teachers/" + inMine, manager.token()).map(), "name", "Hijacked"));
        assertThat(rename.status()).isEqualTo(403);
        assertThat(put("/api/v1/teachers/" + elsewhere, manager.token(), contact(current, "phone", "9"))
                        .status())
                .isEqualTo(404);

        assertThat(post("/api/v1/teachers", manager.token(), Map.of("name", "X")).status()).isEqualTo(403);
        assertThat(post("/api/v1/teachers/" + inMine + "/status", manager.token(), Map.of("status", "ON_LEAVE"))
                        .status())
                .isEqualTo(403);
        // spec 012: a Zone Manager maps Teachers within their scope (own School; a Teacher not placed yet or placed
        // at their School), but never moves a Teacher out of, or into, another Manager's School
        assertThat(placeTeacherRaw(manager.token(), inMine, mine[2], null).status()).isEqualTo(409);
        assertThat(placeTeacherRaw(manager.token(), inMine, theirs[2], null).status()).isEqualTo(404);
        assertThat(placeTeacherRaw(manager.token(), elsewhere, mine[2], null).status()).isEqualTo(404);
        assertThat(put("/api/v1/teachers/" + inMine + "/user", manager.token(), Map.of("userId", UUID.randomUUID()))
                        .status())
                .isEqualTo(403);
        assertThat(delete("/api/v1/teachers/" + inMine + "/placements/pending", manager.token()).status())
                .isEqualTo(404);
        assertThat(placeTeacherRaw(manager.token(), unplaced, mine[2], null).status()).isEqualTo(200);
    }

    @Test
    void aTeacherPlacedAtASchoolWithoutAManagerIsVisibleToAdminButNotToAnyManager() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] ids = schoolInNewZone(admin);
        ManagerCtx other = newManager(admin, zone(admin));
        UUID teacher = teacher(admin);
        placeTeacher(admin, teacher, ids[2]);

        assertThat(get("/api/v1/teachers/" + teacher, admin).status()).isEqualTo(200);
        assertThat(get("/api/v1/teachers/" + teacher, other.token()).status()).isEqualTo(404);
        assertThat(get("/api/v1/teachers/" + teacher, admin).map()).containsEntry("manager", null);
    }

    @Test
    void theAccountableManagerFollowsTheSchoolsManagerAutomatically() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] ids = schoolInNewZone(admin);
        ManagerCtx first = newManager(admin, ids[0]);
        ManagerCtx second = newManager(admin, ids[0]);
        UUID teacher = teacher(admin);
        placeTeacher(admin, teacher, ids[2]);
        assignSchoolManager(admin, ids[2], first.managerId());
        assertThat(get("/api/v1/teachers/" + teacher, first.token()).status()).isEqualTo(200);
        assertThat(get("/api/v1/teachers/" + teacher, admin).body()).contains(first.managerId().toString());

        assignSchoolManager(admin, ids[2], second.managerId());

        assertThat(get("/api/v1/teachers/" + teacher, first.token()).status()).isEqualTo(404);
        assertThat(get("/api/v1/teachers/" + teacher, second.token()).status()).isEqualTo(200);
        assertThat(get("/api/v1/teachers/" + teacher, admin).body()).contains(second.managerId().toString());
    }

    @Test
    void teacherAndSystemAreRefusedAndUnauthenticatedIs401() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = teacher(admin);
        for (Role role : new Role[] {Role.TEACHER, Role.SYSTEM}) {
            String token = signInAs(role).token();
            assertThat(get("/api/v1/teachers", token).status()).isEqualTo(403);
            assertThat(get("/api/v1/teachers/" + teacher, token).status()).isEqualTo(403);
            assertThat(post("/api/v1/teachers", token, Map.of("name", "X")).status()).isEqualTo(403);
            assertThat(post("/api/v1/teachers/" + teacher + "/status", token, Map.of("status", "EXITED")).status())
                    .isEqualTo(403);
        }
        assertThat(get("/api/v1/teachers", null).status()).isEqualTo(401);
    }

    @Test
    void listingPaginatesCapsThePageSizeAndAnEmptyResultIsNotAnError() {
        String admin = signInAs(Role.ADMIN).token();
        String marker = uniqueName("Pg");
        for (int i = 0; i < 3; i++) {
            post("/api/v1/teachers", admin, Map.of("name", marker + " " + i, "status", "ACTIVE"));
        }

        Resp second = get("/api/v1/teachers?query=" + marker + "&size=2&page=1", admin);
        assertThat(content(second)).hasSize(1);
        assertThat(total(second)).isEqualTo(3);
        assertThat(get("/api/v1/teachers?size=500", admin).map()).containsEntry("size", 100);
        Resp none = get("/api/v1/teachers?query=no-such-teacher-xyz", admin);
        assertThat(none.status()).isEqualTo(200);
        assertThat(content(none)).isEmpty();
    }
}
