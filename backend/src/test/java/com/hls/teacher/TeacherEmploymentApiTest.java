package com.hls.teacher;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.DesignationTestBase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 005a US3 (T027 and T028): a Teacher's designation and employee id, per role and per scope. */
class TeacherEmploymentApiTest extends DesignationTestBase {

    @Test
    void adminSetsChangesAndClearsTheDesignationAndEmployeeIdAndEachChangeIsAudited() {
        String admin = signInAs(Role.ADMIN).token();
        UUID primary = designation(admin, "TEACHER");
        UUID senior = designation(admin, "TEACHER");
        UUID teacher = teacher(admin);
        assertThat(get("/api/v1/teachers/" + teacher, admin).body()).contains("DESIGNATION");

        String employeeId = uniqueEmployeeId();
        Resp saved = teacherEmployment(admin, teacher, primary, employeeId);
        assertThat(saved.status()).as(saved.body()).isEqualTo(200);
        assertThat(saved.body()).contains(primary.toString()).contains(employeeId).doesNotContain("\"DESIGNATION\"");
        assertChangeRecorded(admin, "TEACHER", teacher, "designation");
        assertChangeRecorded(admin, "TEACHER", teacher, "employeeId");

        assertThat(teacherEmployment(admin, teacher, senior, employeeId).body()).contains(senior.toString());
        // the id may be cleared, and so may the designation
        Resp cleared = teacherEmployment(admin, teacher, null, null);
        assertThat(cleared.status()).isEqualTo(200);
        assertThat(cleared.body()).contains("DESIGNATION");
    }

    @Test
    void onlyActiveDesignationsOfTheTeacherKindAreAccepted() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = teacher(admin);
        UUID managerKind = designation(admin, "MANAGER");
        UUID retired = designation(admin, "TEACHER");
        assertThat(teacherEmployment(admin, teacher, managerKind, null).status()).isEqualTo(400);
        assertThat(teacherEmployment(admin, teacher, UUID.randomUUID(), null).status()).isEqualTo(400);

        // held by the Teacher, then retired: kept, and unchanged saves still work (an id correction)
        assertThat(teacherEmployment(admin, teacher, retired, null).status()).isEqualTo(200);
        long v = rows(get("/api/v1/designations", admin)).stream()
                .filter(r -> retired.toString().equals(r.get("id")))
                .map(r -> ((Number) r.get("version")).longValue())
                .findFirst()
                .orElseThrow();
        put("/api/v1/designations/" + retired, admin, Map.of("retired", true, "version", v));
        assertThat(teacherEmployment(admin, teacher, retired, uniqueEmployeeId()).status()).isEqualTo(200);
        UUID another = teacher(admin);
        assertThat(teacherEmployment(admin, another, retired, null).status()).isEqualTo(400);
        assertThat(get("/api/v1/teachers/" + teacher, admin).body()).contains("\"retired\":true");
    }

    @Test
    void anExitedTeacherCanStillBeCorrected() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "TEACHER");
        UUID teacher = teacher(admin);
        assertThat(post("/api/v1/teachers/" + teacher + "/status", admin, Map.of("status", "EXITED")).status())
                .isEqualTo(200);
        assertThat(teacherEmployment(admin, teacher, designation, uniqueEmployeeId()).status()).isEqualTo(200);
    }

    @Test
    void aZoneManagerReadsInScopeButCannotChangeEvenWithTeacherEditAndOutsideScopeIsNotFound() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "TEACHER");
        UUID[] mine = schoolInNewZone(admin);
        UUID[] theirs = schoolInNewZone(admin);
        ManagerCtx zoneManager = newManager(admin, mine[0]);
        ManagerCtx otherManager = newManager(admin, theirs[0]);
        assignSchoolManager(admin, mine[2], zoneManager.managerId());
        UUID teacher = teacher(admin);
        placeTeacher(admin, teacher, mine[2]);
        String employeeId = uniqueEmployeeId();
        assertThat(teacherEmployment(admin, teacher, designation, employeeId).status()).isEqualTo(200);

        Resp read = get("/api/v1/teachers/" + teacher, zoneManager.token());
        assertThat(read.status()).isEqualTo(200);
        assertThat(read.body()).contains(designation.toString()).contains(employeeId);

        // the Zone Manager holds TEACHERS EDIT by default, which does not cover these fields
        Map<String, Object> body = new HashMap<>();
        body.put("designationId", designation);
        body.put("employeeId", "ZZ-9");
        body.put("version", teacherVersion(admin, teacher));
        assertThat(put("/api/v1/teachers/" + teacher + "/employment", zoneManager.token(), body).status())
                .isEqualTo(403);
        assertThat(get("/api/v1/teachers/" + teacher, otherManager.token()).status()).isEqualTo(404);
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            assertThat(put("/api/v1/teachers/" + teacher + "/employment", signInAs(role).token(), body).status())
                    .as(role.name())
                    .isEqualTo(403);
        }
    }

    @Test
    void myProfileShowsNeitherFieldAndExistingTeachersWorkWithNeither() {
        String admin = signInAs(Role.ADMIN).token();
        Signed tara = signInAs(Role.TEACHER);
        UUID designation = designation(admin, "TEACHER");
        Resp created = post(
                "/api/v1/teachers",
                admin,
                Map.of("name", uniqueName("Tara"), "phone", "9444444441", "status", "ACTIVE", "userId", tara.userId()));
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        assertThat(created.body()).contains("DESIGNATION");
        assertThat(teacherEmployment(admin, created.id(), designation, uniqueEmployeeId()).status()).isEqualTo(200);

        Resp mine = get("/api/v1/teachers/me", tara.token());
        assertThat(mine.status()).isEqualTo(200);
        assertThat(mine.map()).containsEntry("employment", null);
    }
}
