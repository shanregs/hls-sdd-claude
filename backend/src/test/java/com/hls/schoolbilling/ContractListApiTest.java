package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Spec 012 US4: the School Contracts list. Statuses, filled and vacant positions, Teachers not mapped,
 * filters, and the scope boundary between two Zone Managers.
 */
class ContractListApiTest extends SchoolContractsTestBase {

    private static Map<String, Object> rowOf(Resp list, UUID school) {
        return content(list).stream()
                .filter(r -> school.toString().equals(r.get("schoolId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("School missing from the list: " + list.body()));
    }

    /** Five Schools of one Zone Manager in one Zone: full contract, vacancies, pending, none, ending soon. */
    private record World(
            String admin, ManagerCtx manager, UUID withVacancy, UUID pending, UUID none, UUID endsSoon, UUID ended) {}

    private World world() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zone = zone(admin);
        UUID place = place(admin, zone);
        ManagerCtx manager = newManager(admin, zone);
        UUID[] schools = new UUID[5];
        for (int i = 0; i < 5; i++) {
            schools[i] = school(admin, place);
            assignSchoolManager(admin, schools[i], manager.managerId());
        }
        Fixture f0 = new Fixture(zone, schools[0], manager);
        UUID dir = director().userId();
        // 0: an active MoU for 3 Teachers with 2 filled and one Teacher left over from before (not mapped is 0 here)
        contractId(createContract(admin, schools[0], sameSalaryBody(f0, dir, 3, "15000")));
        for (int i = 0; i < 2; i++) {
            assertThat(assign(admin, teacher(admin), schools[0], null, today).status()).isEqualTo(200);
        }
        // 1: placements but no MoU yet -> pending
        assertThat(assign(admin, teacher(admin), schools[1], null, today).status()).isEqualTo(200);
        assertThat(assign(admin, teacher(admin), schools[1], null, today).status()).isEqualTo(200);
        // 2: nothing at all -> none
        // 3: ends within 30 days
        Fixture f3 = new Fixture(zone, schools[3], manager);
        Map<String, Object> soon = sameSalaryBody(f3, dir, 1, "9000");
        soon.put("endsOn", today.plusDays(12).toString());
        contractId(createContract(admin, schools[3], soon));
        // 4: ended
        Fixture f4 = new Fixture(zone, schools[4], manager);
        Map<String, Object> over = sameSalaryBody(f4, dir, 1, "9000");
        over.put("startsOn", today.minusDays(80).toString());
        over.put("endsOn", today.minusDays(20).toString());
        over.put("signedOn", today.minusDays(81).toString());
        contractId(createContract(admin, schools[4], over));
        return new World(admin, manager, schools[0], schools[1], schools[2], schools[3], schools[4]);
    }

    @Test
    void eachSchoolShowsItsStatusAndFilledAndVacantPositions() {
        World w = world();

        Resp list = get(BASE + "?size=100", w.manager().token());

        assertThat(list.status()).as(list.body()).isEqualTo(200);
        assertThat(content(list)).hasSize(5);
        Map<String, Object> full = rowOf(list, w.withVacancy());
        assertThat(full.get("status")).isEqualTo("ACTIVE");
        assertThat(full.get("teacherCount")).isEqualTo(3);
        assertThat(full.get("filled")).isEqualTo(2);
        assertThat(full.get("vacant")).isEqualTo(1);
        assertThat(full.get("unmapped")).isEqualTo(0);
        assertThat(full.get("zoneManagerName")).isNotNull();

        Map<String, Object> pending = rowOf(list, w.pending());
        assertThat(pending.get("status")).isEqualTo("MOU_PENDING");
        assertThat(pending.get("teacherCount")).isNull();
        assertThat(pending.get("unmapped")).isEqualTo(2);

        assertThat(rowOf(list, w.none()).get("status")).isEqualTo("NONE");
        assertThat(rowOf(list, w.none()).get("contractId")).isNull();
        assertThat(rowOf(list, w.endsSoon()).get("status")).isEqualTo("ENDS_SOON");
        assertThat(rowOf(list, w.ended()).get("status")).isEqualTo("ENDED");
    }

    @Test
    void theListFiltersByStatusAndByZoneManagerAndPages() {
        World w = world();
        String token = w.manager().token();

        Resp pending = get(BASE + "?status=MOU_PENDING", token);
        assertThat(content(pending)).hasSize(1);
        assertThat(content(pending).get(0).get("schoolId")).isEqualTo(w.pending().toString());

        assertThat(content(get(BASE + "?status=ENDED", token))).hasSize(1);
        assertThat(content(get(BASE + "?managerId=" + w.manager().managerId(), w.admin()))).hasSizeGreaterThanOrEqualTo(5);
        assertThat(content(get(BASE + "?managerId=" + UUID.randomUUID(), w.admin()))).isEmpty();

        Resp firstPage = get(BASE + "?size=2&page=0", token);
        assertThat(content(firstPage)).hasSize(2);
        assertThat(total(firstPage)).isEqualTo(5);
        assertThat(content(get(BASE + "?size=2&page=2", token))).hasSize(1);
    }

    @Test
    void aZoneManagerSeesOnlyTheirOwnSchoolsInListAndFilters() {
        World w = world();
        Fixture other = fixture(w.admin());
        contractId(createContract(w.admin(), other.schoolId(), sameSalaryBody(other, director().userId(), 2, "15000")));

        Resp theirs = get(BASE + "?size=100", other.manager().token());
        assertThat(content(theirs)).hasSize(1);
        assertThat(content(theirs).get(0).get("schoolId")).isEqualTo(other.schoolId().toString());
        assertThat(theirs.body()).doesNotContain(w.withVacancy().toString());

        // an Admin sees every School, including both Managers'
        // (a page holds at most 100 rows and the shared test database holds many Schools, so ask for each Manager)
        Resp first = get(BASE + "?size=100&managerId=" + w.manager().managerId(), w.admin());
        Resp second = get(BASE + "?size=100&managerId=" + other.manager().managerId(), w.admin());
        assertThat(rowOf(first, w.withVacancy())).isNotNull();
        assertThat(rowOf(second, other.schoolId())).isNotNull();
    }

    @Test
    void aZoneManagerWithNoSchoolsGetsAnEmptyListAndTeacherAndSystemAreRefused() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx idle = newManager(admin);

        Resp empty = get(BASE, idle.token());

        assertThat(empty.status()).isEqualTo(200);
        assertThat(content(empty)).isEmpty();
        assertThat(total(empty)).isZero();
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            assertThat(get(BASE, signInAs(role).token()).status()).as(role.name()).isEqualTo(403);
        }
        assertThat(get(BASE, null).status()).isEqualTo(401);
    }
}
