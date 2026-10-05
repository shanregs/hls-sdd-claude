package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Spec 012 FR-005 and the role matrix of contracts/school-contracts-api.md: who may map Teachers, and to
 * which Schools. Admin and Director map anywhere; a Zone Manager only into a School of theirs.
 */
class MappingApiTest extends SchoolContractsTestBase {

    private UUID firstPosition(String token, UUID school) {
        return UUID.fromString((String) positionsOf(contractsOf(token, school).get(0)).get(0).get("id"));
    }

    @Test
    void aZoneManagerMapsAnUnplacedTeacherIntoTheirOwnSchoolAndNoOther() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture mine = fixture(admin);
        Fixture theirs = fixture(admin);
        UUID dir = director().userId();
        contractId(createContract(admin, mine.schoolId(), perTeacherBody(mine, dir, "15000", "16000")));
        contractId(createContract(admin, theirs.schoolId(), sameSalaryBody(theirs, dir, 2, "15000")));
        String manager = mine.manager().token();
        UUID recruit = teacher(admin);

        assertThat(assign(manager, recruit, theirs.schoolId(), null, today).status()).isEqualTo(404);
        // a position is needed for a different-salary contract, and a foreign one is refused
        assertThat(assign(manager, recruit, mine.schoolId(), null, today).status()).isEqualTo(400);
        Resp mapped = assign(manager, recruit, mine.schoolId(), firstPosition(admin, mine.schoolId()), today);

        assertThat(mapped.status()).as(mapped.body()).isEqualTo(200);
        assertThat(mapped.body()).contains("\"positionNumber\":1");
        assertThat(positionsOf(contractsOf(manager, mine.schoolId()).get(0)).get(0).get("teacherId"))
                .isEqualTo(recruit.toString());
    }

    @Test
    void aZoneManagerCannotMoveATeacherOutOfAnotherManagersSchool() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture mine = fixture(admin);
        Fixture theirs = fixture(admin);
        UUID dir = director().userId();
        contractId(createContract(admin, mine.schoolId(), sameSalaryBody(mine, dir, 2, "15000")));
        contractId(createContract(admin, theirs.schoolId(), startedDaysAgo(sameSalaryBody(theirs, dir, 2, "15000"), 10)));
        UUID theirTeacher = teacher(admin);
        assertThat(assign(admin, theirTeacher, theirs.schoolId(), null, today.minusDays(3)).status()).isEqualTo(200);

        Resp refused = assign(mine.manager().token(), theirTeacher, mine.schoolId(), null, today);

        assertThat(refused.status()).isEqualTo(404);
        assertThat(positionsOf(contractsOf(admin, theirs.schoolId()).get(0)).get(0).get("teacherId"))
                .isEqualTo(theirTeacher.toString());
    }

    @Test
    void aZoneManagerCannotOverwriteAMoveScheduledIntoAnotherManagersSchoolOrBackdate() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture mine = fixture(admin);
        Fixture theirs = fixture(admin);
        UUID dir = director().userId();
        contractId(createContract(admin, mine.schoolId(), sameSalaryBody(mine, dir, 2, "15000")));
        contractId(createContract(admin, theirs.schoolId(), startedDaysAgo(sameSalaryBody(theirs, dir, 2, "15000"), 10)));
        UUID recruit = teacher(admin);
        // a move into the other Zone Manager's School is already scheduled; the recruit has no current School
        assertThat(assign(admin, recruit, theirs.schoolId(), null, today.plusDays(10)).status()).isEqualTo(200);

        assertThat(assign(mine.manager().token(), recruit, mine.schoolId(), null, today).status()).isEqualTo(404);
        // and a Zone Manager cannot rewrite history with a past date
        UUID other = teacher(admin);
        assertThat(assign(mine.manager().token(), other, mine.schoolId(), null, today.minusDays(1)).status()).isEqualTo(400);
        assertThat(assign(mine.manager().token(), other, mine.schoolId(), null, today).status()).isEqualTo(200);
    }

    @Test
    void aDirectorMapsAnywhereAndTeacherAndSystemNeverDo() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        contractId(createContract(admin, f.schoolId(), sameSalaryBody(f, director().userId(), 3, "15000")));
        UUID recruit = teacher(admin);

        assertThat(assign(director().token(), recruit, f.schoolId(), null, today).status()).isEqualTo(200);

        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            String token = signInAs(role).token();
            assertThat(assign(token, teacher(admin), f.schoolId(), null, today).status())
                    .as(role.name())
                    .isEqualTo(403);
            assertThat(post(BASE + "/contracts/" + UUID.randomUUID() + "/map-teachers", token, List.of()).status())
                    .as(role.name())
                    .isEqualTo(403);
        }
        assertThat(assign(null, recruit, f.schoolId(), null, today).status()).isEqualTo(401);
    }

    @Test
    void reMappingIsLimitedToTheZoneManagersOwnContracts() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture mine = fixture(admin);
        Fixture theirs = fixture(admin);
        UUID dir = director().userId();
        UUID myContract = contractId(createContract(admin, mine.schoolId(), sameSalaryBody(mine, dir, 2, "15000")));
        UUID theirContract = contractId(createContract(admin, theirs.schoolId(), sameSalaryBody(theirs, dir, 2, "15000")));
        UUID mineTeacher = teacher(admin);
        assertThat(assign(admin, mineTeacher, mine.schoolId(), null, today.minusDays(3)).status()).isEqualTo(200);
        Object theirPosition = positionsOf(contractsOf(admin, theirs.schoolId()).get(0)).get(1).get("id");
        Map<String, Object> entry = new HashMap<>();
        entry.put("teacherId", mineTeacher);
        entry.put("positionId", theirPosition);

        // another Zone Manager's contract is not found for this Zone Manager
        assertThat(post(BASE + "/contracts/" + theirContract + "/map-teachers", mine.manager().token(), List.of(entry))
                        .status())
                .isEqualTo(404);
        // their own contract is theirs to map; the Teacher is already on position 1 so asking for position 2 moves in place
        Object myPosition2 = positionsOf(contractsOf(admin, mine.schoolId()).get(0)).get(1).get("id");
        Map<String, Object> own = new HashMap<>();
        own.put("teacherId", mineTeacher);
        own.put("positionId", myPosition2);
        Resp resp = post(BASE + "/contracts/" + myContract + "/map-teachers", mine.manager().token(), List.of(own));
        assertThat(resp.status()).isIn(200, 409);
    }
}
