package com.hls.substitution;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.SubstitutionTestBase;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 017a FR-013, SC-004: permission then scope on every endpoint, and My Substitutions is the Teacher's own. */
class SubstitutionAuthorizationTest extends SubstitutionTestBase {

    @Test
    void everyEndpointIsRefusedWithoutASignIn() {
        UUID id = UUID.randomUUID();
        assertThat(get(SUBS, null).status()).isEqualTo(401);
        assertThat(get(SUBS + "/" + id, null).status()).isEqualTo(401);
        assertThat(get("/api/v1/me/substitutions", null).status()).isEqualTo(401);
    }

    @Test
    void teacherAndSystemAreRefusedOnEveryStaffEndpoint() {
        World w = newWorld();
        LocalDate day = monday();
        absent(w.managerA().token(), w.teacherA().teacherId(), day);
        UUID id = requirementId(w.teacherA().teacherId(), day);
        String system = signInAs(Role.SYSTEM).token();
        for (String token : new String[] {w.teacherA().token(), system}) {
            assertThat(get(SUBS, token).status()).isEqualTo(403);
            assertThat(get(SUBS + "/" + id, token).status()).isEqualTo(403);
            assertThat(get(SUBS + "/" + id + "/candidates", token).status()).isEqualTo(403);
            for (String action : new String[] {"assign", "release", "cover", "inform"}) {
                assertThat(post(SUBS + "/" + id + "/" + action, token, Map.of("version", 0)).status())
                        .as(action)
                        .isEqualTo(403);
            }
        }
        assertThat(get("/api/v1/me/substitutions", system).status()).isEqualTo(403);
        assertThat(get("/api/v1/me/substitutions", w.managerA().token()).status()).isEqualTo(403);
        assertThat(get("/api/v1/me/substitutions", w.admin()).status()).isEqualTo(403);
    }

    @Test
    void aManagerOfAnotherSchoolSeesNothingOfIt() {
        World w = newWorld();
        TeacherCtx bench = benchTeacher(w);
        LocalDate day = monday();
        absent(w.managerA().token(), w.teacherA().teacherId(), day);
        UUID id = requirementId(w.teacherA().teacherId(), day);
        String other = w.managerB().token();

        assertThat(get(SUBS + "/" + id, other).status()).isEqualTo(404);
        assertThat(get(SUBS + "/" + id + "/candidates", other).status()).isEqualTo(404);
        assertThat(assign(other, id, bench.teacherId()).status()).isEqualTo(404);
        assertThat(release(other, id, "x").status()).isEqualTo(404);
        assertThat(cover(other, id).status()).isEqualTo(404);
        assertThat(inform(other, id).status()).isEqualTo(404);
        String mine = "?status=ALL&schoolId=" + w.schoolA();
        assertThat(get(SUBS + "?status=ALL", other).body()).doesNotContain(id.toString());
        assertThat(get(SUBS + mine, other).body()).doesNotContain(id.toString());
        assertThat(get(SUBS + mine, w.managerA().token()).body()).contains(id.toString());
        assertThat(get(SUBS + mine, w.admin()).body()).contains(id.toString());
        assertThat(get(SUBS + mine, w.director()).body()).contains(id.toString());
        assertThat(get(SUBS + "/" + id, w.director()).status()).isEqualTo(200);
        assertThat(get(SUBS + "?status=BOGUS", w.admin()).status()).isEqualTo(400);
    }

    @Test
    void theDefaultListIsOpenAndAssignedOnly() {
        World w = newWorld();
        LocalDate day = monday();
        absent(w.managerA().token(), w.teacherA().teacherId(), day);
        UUID id = requirementId(w.teacherA().teacherId(), day);
        markAs(w.managerA().token(), w.teacherA().teacherId(), day, "P", 1);

        assertThat(get(SUBS, w.managerA().token()).body()).doesNotContain(id.toString());
        assertThat(get(SUBS + "?status=CANCELLED", w.managerA().token()).body()).contains(id.toString());
    }

    @Test
    void aTeacherSeesOnlyTheirOwnAssignmentAndNeverTheAbsentTeachersName() {
        World w = newWorld();
        TeacherCtx bench = benchTeacher(w);
        TeacherCtx other = benchTeacher(w);
        LocalDate day = monday();
        absent(w.managerA().token(), w.teacherA().teacherId(), day);
        UUID id = requirementId(w.teacherA().teacherId(), day);
        assign(w.managerA().token(), id, bench.teacherId());
        String absentName = jdbc.queryForObject("select name from teacher where id = ?", String.class, w.teacherA().teacherId());

        Resp mine = get("/api/v1/me/substitutions?from=" + day, bench.token());

        assertThat(mine.status()).as(mine.body()).isEqualTo(200);
        assertThat(mine.body()).contains(id.toString()).contains(w.schoolA().toString());
        assertThat(mine.body()).doesNotContain(absentName).doesNotContain(w.teacherA().teacherId().toString());
        assertThat(get("/api/v1/me/substitutions?from=" + day, other.token()).body()).isEqualTo("[]");
    }

    @Test
    void theMenuAndDataScopeComeFromTheAccessModel() {
        World w = newWorld();
        assertThat(get("/api/v1/me/access-model", w.managerA().token()).body())
                .contains("/operations/substitutions")
                .contains("\"SUBSTITUTION\":\"ASSIGNED\"");
        assertThat(get("/api/v1/me/access-model", w.admin()).body()).contains("\"SUBSTITUTION\":\"ORG_WIDE\"");
        String teacher = get("/api/v1/me/access-model", w.teacherA().token()).body();
        assertThat(teacher).contains("/my-substitutions").doesNotContain("/operations/substitutions");
        assertThat(get("/api/v1/me/access-model", signInAs(Role.SYSTEM).token()).body()).doesNotContain("substitutions");
    }
}
