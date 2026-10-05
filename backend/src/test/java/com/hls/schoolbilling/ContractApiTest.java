package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.school.api.NotFoundException;
import com.hls.schoolbilling.internal.ContractDtos;
import com.hls.schoolbilling.internal.ContractService;
import com.hls.schoolbilling.internal.SalaryMode;
import com.hls.teacher.api.TeacherDirectory;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spec 012 US1: the MoU contract. Rules (positions, salary modes, signing details, one active contract, the
 * Zone Manager) and the per-role and per-scope matrix of contracts/school-contracts-api.md.
 */
class ContractApiTest extends SchoolContractsTestBase {

    @Autowired
    private TeacherDirectory directory;

    @Autowired
    private ContractService service;

    @Test
    void anAdminRecordsAMouForAllTeachersAtOneSalary() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        Signed dir = director();

        Resp created = createContract(admin, f.schoolId(), sameSalaryBody(f, dir.userId(), 4, "15000.00"));

        assertThat(created.status()).as(created.body()).isEqualTo(201);
        Map<String, Object> contract = created.map();
        assertThat(contract.get("state")).isEqualTo("ACTIVE");
        assertThat(contract.get("status")).isEqualTo("ACTIVE");
        assertThat(contract.get("salaryMode")).isEqualTo("SAME_FOR_ALL");
        assertThat(contract.get("teacherCount")).isEqualTo(4);
        assertThat(contract.get("rate")).isEqualTo("15000.00");
        List<Map<String, Object>> positions = positionsOf(contract);
        assertThat(positions).hasSize(4);
        assertThat(positions).allSatisfy(p -> assertThat(p.get("salary")).isEqualTo("15000.00"));
        assertThat(positions.stream().map(p -> p.get("number")).toList()).containsExactly(1, 2, 3, 4);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> signatories = (List<Map<String, Object>>) contract.get("signatories");
        assertThat(signatories).extracting(s -> s.get("party")).containsExactlyInAnyOrder("SCHOOL", "HLS", "HLS");
        assertThat(signatories).extracting(s -> s.get("designation")).contains("Principal", "Zone Manager", "Director");
        assertThat(contract.get("signedOn")).isEqualTo(today.minusDays(1).toString());
    }

    @Test
    void aDifferentSalaryMouNeedsAnAmountForEachPositionAndKeepsThem() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        Signed dir = director();

        Resp created = createContract(admin, f.schoolId(), perTeacherBody(f, dir.userId(), "15000.00", "18000.50", "20000"));

        assertThat(created.status()).as(created.body()).isEqualTo(201);
        assertThat(created.map().get("rate")).isNull();
        assertThat(positionsOf(created.map()))
                .extracting(p -> p.get("salary"))
                .containsExactly("15000.00", "18000.50", "20000.00");
        assertThat(positionsOf(created.map())).extracting(p -> p.get("title")).containsExactly("Position 1", "Position 2", "Position 3");
    }

    @Test
    void salaryAmountsAndCountsAreValidated() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        UUID dir = director().userId();

        Map<String, Object> fewer = perTeacherBody(f, dir, "15000", "16000");
        fewer.put("teacherCount", 3);
        assertThat(createContract(admin, f.schoolId(), fewer).status()).isEqualTo(400);

        Map<String, Object> zeroSalary = perTeacherBody(f, dir, "15000", "0");
        assertThat(createContract(admin, f.schoolId(), zeroSalary).status()).isEqualTo(400);

        Map<String, Object> sameWithPositions = sameSalaryBody(f, dir, 2, "15000");
        sameWithPositions.put("positions", List.of(Map.of("salary", "1")));
        assertThat(createContract(admin, f.schoolId(), sameWithPositions).status()).isEqualTo(400);

        Map<String, Object> perWithRate = perTeacherBody(f, dir, "15000");
        perWithRate.put("rate", "15000");
        assertThat(createContract(admin, f.schoolId(), perWithRate).status()).isEqualTo(400);

        assertThat(createContract(admin, f.schoolId(), sameSalaryBody(f, dir, 0, "15000")).status()).isEqualTo(400);
        assertThat(createContract(admin, f.schoolId(), sameSalaryBody(f, dir, 501, "15000")).status()).isEqualTo(400);
        assertThat(createContract(admin, f.schoolId(), sameSalaryBody(f, dir, 2, "-5")).status()).isEqualTo(400);
        assertThat(createContract(admin, f.schoolId(), sameSalaryBody(f, dir, 2, "15000.123")).status()).isEqualTo(400);
        assertThat(contractsOf(admin, f.schoolId())).isEmpty();
    }

    @Test
    void theSigningDetailsAreRequired() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        UUID dir = director().userId();

        Map<String, Object> noDate = sameSalaryBody(f, dir, 2, "15000");
        noDate.remove("signedOn");
        assertThat(createContract(admin, f.schoolId(), noDate).status()).isEqualTo(400);

        Map<String, Object> future = sameSalaryBody(f, dir, 2, "15000");
        future.put("signedOn", today.plusDays(1).toString());
        assertThat(createContract(admin, f.schoolId(), future).status()).isEqualTo(400);

        Map<String, Object> noSchoolSigner = sameSalaryBody(f, dir, 2, "15000");
        noSchoolSigner.put("schoolSignatories", List.of());
        assertThat(createContract(admin, f.schoolId(), noSchoolSigner).status()).isEqualTo(400);

        Map<String, Object> blankName = sameSalaryBody(f, dir, 2, "15000");
        blankName.put("schoolSignatories", List.of(schoolSigner("  ", "Principal")));
        assertThat(createContract(admin, f.schoolId(), blankName).status()).isEqualTo(400);

        Map<String, Object> noDesignation = sameSalaryBody(f, dir, 2, "15000");
        noDesignation.put("schoolSignatories", List.of(schoolSigner("R. Kumar", "")));
        assertThat(createContract(admin, f.schoolId(), noDesignation).status()).isEqualTo(400);

        Map<String, Object> noHls = sameSalaryBody(f, dir, 2, "15000");
        noHls.put("hlsSignatories", List.of());
        assertThat(createContract(admin, f.schoolId(), noHls).status()).isEqualTo(400);

        // a user who is neither this School's Zone Manager nor a Director cannot sign for HLS
        Map<String, Object> stranger = sameSalaryBody(f, dir, 2, "15000");
        stranger.put("hlsSignatories", List.of(hlsSigner(signInAs(Role.MANAGER).userId(), "Zone Manager")));
        assertThat(createContract(admin, f.schoolId(), stranger).status()).isEqualTo(400);
        stranger.put("hlsSignatories", List.of(hlsSigner(signInAs(Role.TEACHER).userId(), "Director")));
        assertThat(createContract(admin, f.schoolId(), stranger).status()).isEqualTo(400);

        Map<String, Object> twice = sameSalaryBody(f, dir, 2, "15000");
        twice.put("hlsSignatories", List.of(hlsSigner(dir, "Director"), hlsSigner(dir, "Director")));
        assertThat(createContract(admin, f.schoolId(), twice).status()).isEqualTo(400);

        assertThat(contractsOf(admin, f.schoolId())).isEmpty();
    }

    @Test
    void theHlsSideMayBeTheZoneManagerADirectorOrBothAndMoreThanOneDirector() {
        String admin = signInAs(Role.ADMIN).token();
        UUID dirA = director().userId();
        UUID dirB = director().userId();

        Fixture managerOnly = fixture(admin);
        Map<String, Object> m = sameSalaryBody(managerOnly, dirA, 1, "10000");
        m.put("hlsSignatories", List.of(hlsSigner(managerOnly.manager().signed().userId(), "Zone Manager")));
        assertThat(createContract(admin, managerOnly.schoolId(), m).status()).isEqualTo(201);

        Fixture directorOnly = fixture(admin);
        Map<String, Object> d = sameSalaryBody(directorOnly, dirA, 1, "10000");
        d.put("hlsSignatories", List.of(hlsSigner(dirA, "Director")));
        assertThat(createContract(admin, directorOnly.schoolId(), d).status()).isEqualTo(201);

        Fixture twoDirectors = fixture(admin);
        Map<String, Object> two = sameSalaryBody(twoDirectors, dirA, 1, "10000");
        two.put("hlsSignatories", List.of(hlsSigner(dirA, "Director"), hlsSigner(dirB, "Director")));
        Resp created = createContract(admin, twoDirectors.schoolId(), two);
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        assertThat(created.body()).contains("\"party\":\"HLS\"");
    }

    @Test
    void aSchoolWithNoZoneManagerCannotGetAContract() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolWithoutManager(admin);
        Fixture other = fixture(admin);

        Resp refused = createContract(admin, school, sameSalaryBody(other, director().userId(), 2, "15000"));

        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains("Zone Manager");
    }

    @Test
    void aNewMouEndsTheCurrentOneTheDayBeforeAndKeepsBothInTheHistory() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        UUID dir = director().userId();
        UUID first = contractId(createContract(admin, f.schoolId(), sameSalaryBody(f, dir, 2, "15000")));

        Map<String, Object> next = sameSalaryBody(f, dir, 3, "16000");
        next.put("startsOn", today.plusDays(30).toString());
        Resp second = createContract(admin, f.schoolId(), next);

        assertThat(second.status()).as(second.body()).isEqualTo(201);
        List<Map<String, Object>> history = contractsOf(admin, f.schoolId());
        assertThat(history).hasSize(2);
        assertThat(history.get(0).get("id")).isEqualTo(second.id().toString());
        Map<String, Object> old = history.get(1);
        assertThat(old.get("id")).isEqualTo(first.toString());
        assertThat(old.get("endsOn")).isEqualTo(today.plusDays(29).toString());
        assertThat(old.get("rate")).isEqualTo("15000.00");
        assertThat(positionsOf(old)).hasSize(2);
    }

    @Test
    void datesThatOverlapAnotherContractAreRefused() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        UUID dir = director().userId();
        contractId(createContract(admin, f.schoolId(), sameSalaryBody(f, dir, 2, "15000")));

        // the same start date as the current contract
        assertThat(createContract(admin, f.schoolId(), sameSalaryBody(f, dir, 2, "16000")).status()).isEqualTo(409);

        // a contract before the current one that runs into it
        Map<String, Object> earlier = sameSalaryBody(f, dir, 2, "14000");
        earlier.put("startsOn", today.minusDays(60).toString());
        earlier.put("endsOn", today.plusDays(5).toString());
        assertThat(createContract(admin, f.schoolId(), earlier).status()).isEqualTo(409);

        // an end date before the start date
        Map<String, Object> backwards = sameSalaryBody(f, dir, 2, "14000");
        backwards.put("startsOn", today.plusDays(40).toString());
        backwards.put("endsOn", today.plusDays(39).toString());
        assertThat(createContract(admin, f.schoolId(), backwards).status()).isEqualTo(400);
        assertThat(contractsOf(admin, f.schoolId())).hasSize(1);
    }

    @Test
    void theMouIsRecordedOnceOnAPendingContract() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        UUID dir = director().userId();
        UUID teacher = teacher(admin);
        assertThat(assign(admin, teacher, f.schoolId(), null, today).status()).isEqualTo(200);
        List<Map<String, Object>> before = contractsOf(admin, f.schoolId());
        assertThat(before).hasSize(1);
        assertThat(before.get(0).get("state")).isEqualTo("RATE_PENDING");
        assertThat(before.get(0).get("status")).isEqualTo("MOU_PENDING");
        assertThat(positionsOf(before.get(0))).isEmpty();
        String pendingId = (String) before.get(0).get("id");

        Map<String, Object> mou = sameSalaryBody(f, dir, 3, "12000");
        mou.remove("startsOn");
        Resp recorded = put(BASE + "/contracts/" + pendingId + "/mou", admin, mou);

        assertThat(recorded.status()).as(recorded.body()).isEqualTo(200);
        assertThat(recorded.map().get("state")).isEqualTo("ACTIVE");
        assertThat(positionsOf(recorded.map())).hasSize(3);
        assertThat(put(BASE + "/contracts/" + pendingId + "/mou", admin, mou).status()).isEqualTo(409);
    }

    @Test
    void signedRowsCannotBeChangedOrRemovedEvenWithDirectDatabaseAccess() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        UUID contract = contractId(createContract(admin, f.schoolId(), sameSalaryBody(f, director().userId(), 2, "15000")));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("update contract_position set salary = 1 where contract_id = ?", contract));
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("delete from contract_signatory where contract_id = ?", contract));
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("update contract_signatory set name = 'x' where contract_id = ?", contract));
    }

    @Test
    void endingAndCancellingAContract() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        UUID dir = director().userId();
        UUID contract = contractId(createContract(admin, f.schoolId(), sameSalaryBody(f, dir, 2, "15000")));

        Resp ended = post(BASE + "/contracts/" + contract + "/end", admin, Map.of("endsOn", today.plusDays(10).toString()));
        assertThat(ended.status()).as(ended.body()).isEqualTo(200);
        assertThat(ended.map().get("endsOn")).isEqualTo(today.plusDays(10).toString());
        assertThat(ended.map().get("status")).isEqualTo("ENDS_SOON");
        assertThat(post(BASE + "/contracts/" + contract + "/end", admin, Map.of("endsOn", today.minusDays(5).toString())).status())
                .isEqualTo(400);

        Resp cancelled = post(BASE + "/contracts/" + contract + "/cancel", admin, null);
        assertThat(cancelled.status()).as(cancelled.body()).isEqualTo(200);
        assertThat(cancelled.map().get("state")).isEqualTo("CANCELLED");
        assertThat(post(BASE + "/contracts/" + contract + "/cancel", admin, null).status()).isEqualTo(409);
        // the dates are free again
        assertThat(createContract(admin, f.schoolId(), sameSalaryBody(f, dir, 2, "15500")).status()).isEqualTo(201);
    }

    @Test
    void aContractWithMappedTeachersCannotBeCancelledAndEndingItLeavesTheirAssignmentsAlone() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        UUID dir = director().userId();
        UUID contract = contractId(createContract(admin, f.schoolId(), startedDaysAgo(sameSalaryBody(f, dir, 2, "15000"), 10)));
        UUID teacher = teacher(admin);
        assertThat(assign(admin, teacher, f.schoolId(), null, today.minusDays(3)).status()).isEqualTo(200);

        Resp cancel = post(BASE + "/contracts/" + contract + "/cancel", admin, null);
        assertThat(cancel.status()).isEqualTo(409);
        assertThat(cancel.body()).contains("Teachers are assigned");

        Resp ended = post(BASE + "/contracts/" + contract + "/end", admin, Map.of("endsOn", today.plusDays(5).toString()));
        assertThat(ended.status()).as(ended.body()).isEqualTo(200);
        // the Teacher stays placed well after the contract end: ending a contract does not shorten assignments
        List<com.hls.teacher.api.TeacherDirectory.PlacementSpan> spans =
                directory.placementsOverlapping(List.of(teacher), today.plusDays(20), today.plusDays(21));
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).endsOn()).isNull();
    }

    @Test
    void contractWritesAreScopedEvenIfAZoneManagerIsGrantedTheAction() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture mine = fixture(admin);
        Fixture theirs = fixture(admin);
        UUID dir = director().userId();
        UUID theirContract = contractId(createContract(admin, theirs.schoolId(), sameSalaryBody(theirs, dir, 2, "15000")));
        UUID managerUser = mine.manager().signed().userId();
        Set<Role> asManager = Set.of(Role.MANAGER);
        ContractDtos.NewContractRequest request = new ContractDtos.NewContractRequest(
                1,
                SalaryMode.SAME_FOR_ALL,
                new BigDecimal("9000.00"),
                null,
                today,
                List.of(new ContractDtos.SchoolSignatoryInput("R. Kumar", "Principal")),
                List.of(new ContractDtos.HlsSignatoryInput(dir, "Director")),
                today,
                null);

        // the permission matrix is runtime-editable, so the scope check must hold on its own (Principle III)
        org.junit.jupiter.api.Assertions.assertThrows(
                NotFoundException.class, () -> service.create(managerUser, asManager, theirs.schoolId(), request));
        org.junit.jupiter.api.Assertions.assertThrows(
                NotFoundException.class, () -> service.end(managerUser, asManager, theirContract, today.plusDays(9)));
        org.junit.jupiter.api.Assertions.assertThrows(
                NotFoundException.class, () -> service.cancel(managerUser, asManager, theirContract));
        assertThat(contractsOf(admin, theirs.schoolId())).hasSize(1);
        assertThat(contractsOf(admin, theirs.schoolId()).get(0).get("endsOn")).isNull();
        // their own School is theirs to write if the action were granted
        assertThat(service.create(managerUser, asManager, mine.schoolId(), request).teacherCount()).isEqualTo(1);
    }

    @Test
    void aPendingContractHoldingPlacementsCannotBeCancelled() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        assertThat(assign(admin, teacher(admin), f.schoolId(), null, today).status()).isEqualTo(200);
        String pendingId = (String) contractsOf(admin, f.schoolId()).get(0).get("id");

        assertThat(post(BASE + "/contracts/" + pendingId + "/cancel", admin, null).status()).isEqualTo(409);
    }

    @Test
    void theSignatoryCandidatesAreTheZoneManagerAndTheActiveDirectorsOnly() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        Signed dir = director();
        Fixture other = fixture(admin);

        Resp resp = get(BASE + "/signatory-candidates?schoolId=" + f.schoolId(), admin);

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.body()).contains(f.manager().signed().userId().toString(), dir.userId().toString());
        assertThat(resp.body()).doesNotContain(other.manager().signed().userId().toString());
        assertThat(resp.body()).contains("\"designation\":\"Zone Manager\"").contains("\"designation\":\"Director\"");
    }

    @Test
    void whoMayDoWhatAndSeeWhichSchools() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture mine = fixture(admin);
        Fixture theirs = fixture(admin);
        UUID dirUser = director().userId();
        UUID myContract = contractId(createContract(admin, mine.schoolId(), sameSalaryBody(mine, dirUser, 2, "15000")));
        contractId(createContract(admin, theirs.schoolId(), sameSalaryBody(theirs, dirUser, 2, "15000")));
        String manager = mine.manager().token();
        Map<String, Object> body = sameSalaryBody(mine, dirUser, 2, "15000");
        Map<String, Object> mou = new HashMap<>(body);

        // the Zone Manager reads their own School and no one else's
        assertThat(schoolContracts(manager, mine.schoolId()).status()).isEqualTo(200);
        assertThat(schoolContracts(manager, theirs.schoolId()).status()).isEqualTo(404);
        assertThat(get(BASE + "/signatory-candidates?schoolId=" + theirs.schoolId(), manager).status()).isEqualTo(404);
        // ... and cannot create, record, end or cancel
        assertThat(createContract(manager, mine.schoolId(), body).status()).isEqualTo(403);
        assertThat(put(BASE + "/contracts/" + myContract + "/mou", manager, mou).status()).isEqualTo(403);
        assertThat(post(BASE + "/contracts/" + myContract + "/end", manager, Map.of("endsOn", today.plusDays(9).toString())).status())
                .isEqualTo(403);
        assertThat(post(BASE + "/contracts/" + myContract + "/cancel", manager, null).status()).isEqualTo(403);

        // a Director does what an Admin does, anywhere
        String directorToken = director().token();
        assertThat(schoolContracts(directorToken, theirs.schoolId()).status()).isEqualTo(200);
        Fixture third = fixture(admin);
        assertThat(createContract(directorToken, third.schoolId(), sameSalaryBody(third, dirUser, 1, "9000")).status()).isEqualTo(201);

        // Teacher and System never reach contracts
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            String token = signInAs(role).token();
            assertThat(schoolContracts(token, mine.schoolId()).status()).as(role.name()).isEqualTo(403);
            assertThat(createContract(token, mine.schoolId(), body).status()).as(role.name()).isEqualTo(403);
            assertThat(get(BASE + "/signatory-candidates?schoolId=" + mine.schoolId(), token).status())
                    .as(role.name())
                    .isEqualTo(403);
        }
        assertThat(get(BASE + "/schools/" + mine.schoolId(), null).status()).isEqualTo(401);
    }
}
