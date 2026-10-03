package com.hls.organization;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.MasterDataTestBase;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * User Story 5 (FR-020/FR-022, Constitution Principle III/IX): two Managers in different Zones
 * never see each other's Schools or Teachers through any list, search, filter, count or detail view.
 */
class MasterDataScopeBoundaryTest extends MasterDataTestBase {

    /** Two Managers, each with a Zone, a School and a Teacher placed in it. */
    private record World(
            String admin,
            ManagerCtx a,
            ManagerCtx b,
            UUID[] zoneSchoolA,
            UUID[] zoneSchoolB,
            UUID teacherA,
            UUID teacherB) {}

    private World world() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] schoolA = schoolInNewZone(admin);
        UUID[] schoolB = schoolInNewZone(admin);
        ManagerCtx a = newManager(admin, schoolA[0]);
        ManagerCtx b = newManager(admin, schoolB[0]);
        assignSchoolManager(admin, schoolA[2], a.managerId());
        assignSchoolManager(admin, schoolB[2], b.managerId());
        UUID teacherA = teacher(admin);
        UUID teacherB = teacher(admin);
        placeTeacher(admin, teacherA, schoolA[2]);
        placeTeacher(admin, teacherB, schoolB[2]);
        return new World(admin, a, b, schoolA, schoolB, teacherA, teacherB);
    }

    @Test
    void eachManagerSeesOnlyTheirOwnSchoolsAndTeachersThroughEveryListAndFilter() {
        World w = world();
        String[] aUrls = {
            "/api/v1/schools",
            "/api/v1/schools?size=1",
            "/api/v1/schools?active=true",
            "/api/v1/schools?zoneId=" + w.zoneSchoolB()[0], // the other Manager's Zone: still nothing of theirs
            "/api/v1/schools?placeId=" + w.zoneSchoolB()[1],
            "/api/v1/teachers",
            "/api/v1/teachers?status=ACTIVE",
            "/api/v1/teachers?schoolId=" + w.zoneSchoolB()[2]
        };
        for (String url : aUrls) {
            Resp resp = get(url, w.a().token());
            assertThat(resp.status()).as(url).isEqualTo(200);
            assertThat(resp.body())
                    .as(url)
                    .doesNotContain(w.zoneSchoolB()[2].toString(), w.teacherB().toString());
        }
        Resp schoolsA = get("/api/v1/schools", w.a().token());
        assertThat(total(schoolsA)).isEqualTo(1);
        assertThat(schoolsA.body()).contains(w.zoneSchoolA()[2].toString());
        Resp teachersA = get("/api/v1/teachers", w.a().token());
        assertThat(total(teachersA)).isEqualTo(1);
        assertThat(teachersA.body()).contains(w.teacherA().toString());

        Resp teachersB = get("/api/v1/teachers", w.b().token());
        assertThat(total(teachersB)).isEqualTo(1);
        assertThat(teachersB.body()).contains(w.teacherB().toString()).doesNotContain(w.teacherA().toString());
        // a text search that would match the other Manager's data still returns nothing of theirs
        assertThat(get("/api/v1/teachers?query=Teacher&size=100", w.a().token()).body())
                .doesNotContain(w.teacherB().toString());
        assertThat(get("/api/v1/schools?query=School&size=100", w.a().token()).body())
                .doesNotContain(w.zoneSchoolB()[2].toString());
    }

    @Test
    void foreignIdsAreIndistinguishableFromMissingOnes() {
        World w = world();
        UUID missing = UUID.randomUUID();

        for (String path : new String[] {"/api/v1/schools/", "/api/v1/teachers/"}) {
            Resp foreign = get(path + (path.contains("schools") ? w.zoneSchoolB()[2] : w.teacherB()), w.a().token());
            Resp absent = get(path + missing, w.a().token());
            assertThat(foreign.status()).isEqualTo(404);
            assertThat(absent.status()).isEqualTo(404);
            assertThat(foreign.body()).isEqualTo(absent.body());
        }
    }

    @Test
    void adminAndDirectorSeeEverythingAndAManagerWhoIsAlsoDirectorSeesEverything() {
        World w = world();
        String director = signInAs(Role.DIRECTOR).token();
        String both = signInAs(Role.MANAGER, Role.DIRECTOR).token();
        for (String token : new String[] {w.admin(), director, both}) {
            String schools = get("/api/v1/schools?size=100", token).body();
            assertThat(schools).contains(w.zoneSchoolA()[2].toString(), w.zoneSchoolB()[2].toString());
            String teachers = get("/api/v1/teachers?size=100", token).body();
            assertThat(teachers).contains(w.teacherA().toString(), w.teacherB().toString());
        }
    }

    @Test
    void teacherAndSystemUsersCannotUseTheManagementLists() {
        World w = world();
        for (Role role : new Role[] {Role.TEACHER, Role.SYSTEM}) {
            String token = signInAs(role).token();
            assertThat(get("/api/v1/schools", token).status()).isEqualTo(403);
            assertThat(get("/api/v1/teachers", token).status()).isEqualTo(403);
            assertThat(get("/api/v1/teachers/" + w.teacherA(), token).status()).isEqualTo(403);
        }
    }

    @Test
    void reassigningASchoolChangesBothManagersResultsOnTheNextRequestWithoutSigningInAgain() {
        World w = world();
        // Manager B also covers A's Zone so the School can move to them
        assignZones(w.admin(), w.b().managerId(), w.zoneSchoolA()[0], w.zoneSchoolB()[0]);
        assertThat(get("/api/v1/schools/" + w.zoneSchoolA()[2], w.a().token()).status()).isEqualTo(200);
        assertThat(get("/api/v1/schools/" + w.zoneSchoolA()[2], w.b().token()).status()).isEqualTo(404);

        assignSchoolManager(w.admin(), w.zoneSchoolA()[2], w.b().managerId());

        assertThat(get("/api/v1/schools/" + w.zoneSchoolA()[2], w.a().token()).status()).isEqualTo(404);
        assertThat(get("/api/v1/schools/" + w.zoneSchoolA()[2], w.b().token()).status()).isEqualTo(200);
        assertThat(get("/api/v1/teachers/" + w.teacherA(), w.a().token()).status()).isEqualTo(404);
        assertThat(get("/api/v1/teachers/" + w.teacherA(), w.b().token()).status()).isEqualTo(200);
        assertThat(total(get("/api/v1/teachers", w.b().token()))).isEqualTo(2);
    }

    @Test
    void movingATeacherMovesWhichManagerSeesThemAndTheirHistoryIsLimitedToOwnSchools() {
        World w = world();
        assertThat(placeTeacherRaw(w.admin(), w.teacherA(), w.zoneSchoolB()[2], LocalDate.now()).status())
                .isEqualTo(200);

        assertThat(get("/api/v1/teachers/" + w.teacherA(), w.a().token()).status()).isEqualTo(404);
        Resp seenByB = get("/api/v1/teachers/" + w.teacherA(), w.b().token());
        assertThat(seenByB.status()).isEqualTo(200);
        // B sees only placements at B's Schools, never the earlier one at A's School
        assertThat(seenByB.body()).contains(w.zoneSchoolB()[2].toString());
        assertThat(seenByB.body()).doesNotContain(w.zoneSchoolA()[2].toString());
        // Admin sees the full history
        assertThat(get("/api/v1/teachers/" + w.teacherA(), w.admin()).body())
                .contains(w.zoneSchoolA()[2].toString(), w.zoneSchoolB()[2].toString());
    }

    @Test
    void theScopeSummaryReportsOnlyTheCallersOwnZonesAndSchools() {
        World w = world();

        Resp mine = get("/api/v1/me/scope", w.a().token());
        assertThat(mine.status()).isEqualTo(200);
        assertThat(mine.map()).containsEntry("orgWide", false).containsEntry("schoolCount", 1).containsEntry("zoneCount", 1);
        assertThat(mine.body()).contains(w.zoneSchoolA()[0].toString()).doesNotContain(w.zoneSchoolB()[0].toString());
        assertThat(get("/api/v1/me/scope", w.admin()).map()).containsEntry("orgWide", true);
        Resp teacher = get("/api/v1/me/scope", signInAs(Role.TEACHER).token());
        assertThat(teacher.map()).containsEntry("schoolCount", 0).containsEntry("zoneCount", 0);
        assertThat(get("/api/v1/me/scope", null).status()).isEqualTo(401);
        assertThat(List.of("orgWide", "zoneCount", "schoolCount", "zones")).isSubsetOf(mine.map().keySet());
    }
}
