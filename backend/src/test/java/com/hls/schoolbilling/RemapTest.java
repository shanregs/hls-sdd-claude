package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.PlacementSpan;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spec 012 FR-007 and FR-007a: re-mapping the School's Teachers to a new contract in one step without a gap,
 * all or nothing, and what happens to Teachers left on a contract that has ended.
 */
class RemapTest extends SchoolContractsTestBase {

    @Autowired
    private TeacherDirectory directory;

    private Resp remap(String token, UUID contract, List<Map<String, Object>> entries) {
        return post(BASE + "/contracts/" + contract + "/map-teachers", token, entries);
    }

    private static Map<String, Object> entry(UUID teacher, Object position) {
        Map<String, Object> m = new HashMap<>();
        m.put("teacherId", teacher);
        m.put("positionId", position);
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> positionsOfContract(String token, UUID school, UUID contract) {
        return contractsOf(token, school).stream()
                .filter(c -> contract.toString().equals(c.get("id")))
                .findFirst()
                .map(RemapTest::positionsOf)
                .orElseThrow();
    }

    /** A School with a current MoU for 3 Teachers (all mapped) and a new MoU starting in 30 days. */
    private record Setup(Fixture f, UUID oldContract, UUID newContract, List<UUID> teachers) {}

    private Setup setup(String admin) {
        Fixture f = fixture(admin);
        UUID dir = director().userId();
        UUID oldContract = contractId(createContract(admin, f.schoolId(), sameSalaryBody(f, dir, 3, "15000")));
        List<UUID> teachers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            UUID t = teacher(admin);
            assertThat(assign(admin, t, f.schoolId(), null, today.minusDays(5)).status()).isEqualTo(200);
            teachers.add(t);
        }
        Map<String, Object> next = perTeacherBody(f, dir, "20000", "21000", "22000");
        next.put("startsOn", today.plusDays(30).toString());
        UUID newContract = contractId(createContract(admin, f.schoolId(), next));
        return new Setup(f, oldContract, newContract, teachers);
    }

    @Test
    void currentTeachersMoveToTheNewPositionsWithNoGapInTheirPlacement() {
        String admin = signInAs(Role.ADMIN).token();
        Setup s = setup(admin);
        LocalDate start = today.plusDays(30);
        List<Map<String, Object>> positions = positionsOfContract(admin, s.f().schoolId(), s.newContract());
        List<Map<String, Object>> entries = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            entries.add(entry(s.teachers().get(i), positions.get(i).get("id")));
        }

        Resp remapped = remap(admin, s.newContract(), entries);

        assertThat(remapped.status()).as(remapped.body()).isEqualTo(200);
        assertThat(remapped.map().get("remapped")).isEqualTo(3);
        for (int i = 0; i < 3; i++) {
            UUID t = s.teachers().get(i);
            List<PlacementSpan> spans = directory.placementsOverlapping(List.of(t), today.minusDays(10), start.plusDays(40));
            // two adjacent spans at the same School: the old one ends the day before the new one starts
            assertThat(spans).hasSize(2);
            assertThat(spans.get(0).endsOn()).isEqualTo(start.minusDays(1));
            assertThat(spans.get(1).startsOn()).isEqualTo(start);
            assertThat(spans.get(1).endsOn()).isNull();
            assertThat(spans).allSatisfy(span -> assertThat(span.schoolId()).isEqualTo(s.f().schoolId()));
            assertThat(directory.placementsOverlapping(List.of(t), start.minusDays(1), start)).hasSize(2);
            assertChangeRecorded(admin, "CONTRACT_REMAP", t, "position");
        }
        assertThat(positionsOfContract(admin, s.f().schoolId(), s.newContract()))
                .extracting(p -> p.get("salary"))
                .containsExactly("20000.00", "21000.00", "22000.00");
    }

    @Test
    void aFailureOnOneTeacherChangesNothingForTheOthers() {
        String admin = signInAs(Role.ADMIN).token();
        Setup s = setup(admin);
        LocalDate start = today.plusDays(30);
        List<Map<String, Object>> positions = positionsOfContract(admin, s.f().schoolId(), s.newContract());
        UUID stranger = teacher(admin); // not placed at this School, so the third entry fails
        List<Map<String, Object>> entries = List.of(
                entry(s.teachers().get(0), positions.get(0).get("id")),
                entry(s.teachers().get(1), positions.get(1).get("id")),
                entry(stranger, positions.get(2).get("id")));

        Resp refused = remap(admin, s.newContract(), entries);

        assertThat(refused.status()).isEqualTo(409);
        for (UUID t : s.teachers()) {
            assertThat(directory.placementsOverlapping(List.of(t), today.minusDays(10), start.plusDays(40)))
                    .as("unchanged " + t)
                    .hasSize(1);
        }
        assertThat(positionsOfContract(admin, s.f().schoolId(), s.newContract()))
                .allSatisfy(p -> assertThat(p.get("teacherId")).isNull());
    }

    @Test
    void entriesAreValidated() {
        String admin = signInAs(Role.ADMIN).token();
        Setup s = setup(admin);
        List<Map<String, Object>> positions = positionsOfContract(admin, s.f().schoolId(), s.newContract());
        UUID t = s.teachers().get(0);

        assertThat(remap(admin, s.newContract(), List.of()).status()).isEqualTo(400);
        assertThat(remap(admin, s.newContract(), List.of(entry(t, UUID.randomUUID()))).status()).isEqualTo(400);
        assertThat(remap(admin, s.newContract(), List.of(entry(t, positions.get(0).get("id")), entry(t, positions.get(1).get("id"))))
                        .status())
                .isEqualTo(400);
        assertThat(remap(admin, s.newContract(), List.of(
                                entry(s.teachers().get(0), positions.get(0).get("id")),
                                entry(s.teachers().get(1), positions.get(0).get("id"))))
                        .status())
                .isEqualTo(400);
        // a pending or cancelled contract takes no mapping
        assertThat(remap(admin, UUID.randomUUID(), List.of(entry(t, positions.get(0).get("id")))).status()).isEqualTo(404);
    }

    @Test
    void anAssignmentMadeBeforeAnMouIsRecordedGetsItsPositionInPlace() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        UUID teacher = teacher(admin);
        assertThat(assign(admin, teacher, f.schoolId(), null, today).status()).isEqualTo(200);
        String pendingId = (String) contractsOf(admin, f.schoolId()).get(0).get("id");
        Map<String, Object> mou = sameSalaryBody(f, director().userId(), 2, "12000");
        mou.remove("startsOn");
        assertThat(put(BASE + "/contracts/" + pendingId + "/mou", admin, mou).status()).isEqualTo(200);
        UUID contract = UUID.fromString(pendingId);
        Map<String, Object> position = positionsOfContract(admin, f.schoolId(), contract).get(1);

        Resp mapped = remap(admin, contract, List.of(entry(teacher, position.get("id"))));

        assertThat(mapped.status()).as(mapped.body()).isEqualTo(200);
        assertThat(positionsOfContract(admin, f.schoolId(), contract).get(1).get("teacherId"))
                .isEqualTo(teacher.toString());
        // still one continuous span: the row was given its position, not split
        assertThat(directory.placementsOverlapping(List.of(teacher), today.minusDays(1), today.plusDays(60)))
                .hasSize(1);
    }

    @Test
    void aReMapKeepsTheEndOfAnAssignmentThatIsAlreadyLeaving() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        Fixture elsewhere = fixture(admin);
        UUID dir = director().userId();
        contractId(createContract(admin, f.schoolId(), startedDaysAgo(sameSalaryBody(f, dir, 2, "15000"), 10)));
        UUID teacher = teacher(admin);
        assertThat(assign(admin, teacher, f.schoolId(), null, today.minusDays(5)).status()).isEqualTo(200);
        // a move out is scheduled: the assignment here ends the day before it
        assertThat(assign(admin, teacher, elsewhere.schoolId(), null, today.plusDays(20)).status()).isEqualTo(200);
        Map<String, Object> next = perTeacherBody(f, dir, "20000", "21000");
        next.put("startsOn", today.plusDays(5).toString());
        UUID newContract = contractId(createContract(admin, f.schoolId(), next));
        Object position = positionsOfContract(admin, f.schoolId(), newContract).get(0).get("id");

        Resp mapped = remap(admin, newContract, List.of(entry(teacher, position)));

        assertThat(mapped.status()).as(mapped.body()).isEqualTo(200);
        List<PlacementSpan> spans =
                directory.placementsOverlapping(List.of(teacher), today.plusDays(6), today.plusDays(19));
        assertThat(spans).hasSize(1);
        // the new row stops where the old one did, so the Teacher is not placed here past the scheduled move
        assertThat(spans.get(0).endsOn()).isEqualTo(today.plusDays(19));
        assertThat(directory.placementsOverlapping(List.of(teacher), today.plusDays(20), today.plusDays(20)))
                .extracting(PlacementSpan::schoolId)
                .containsExactly(elsewhere.schoolId());
    }

    @Test
    void teachersLeftOnAnEndedContractStayPlacedAndAreReportedAsNotMapped() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        Map<String, Object> past = sameSalaryBody(f, director().userId(), 2, "15000");
        past.put("startsOn", today.minusDays(60).toString());
        past.put("endsOn", today.minusDays(10).toString());
        past.put("signedOn", today.minusDays(61).toString());
        contractId(createContract(admin, f.schoolId(), past));
        UUID teacher = teacher(admin);
        assertThat(assign(admin, teacher, f.schoolId(), null, today.minusDays(30)).status()).isEqualTo(200);

        // the contract has ended and no new MoU exists: the Teacher is still placed (attendance keeps working) ...
        assertThat(directory.placementsOverlapping(List.of(teacher), today, today)).hasSize(1);
        // ... but is shown as not mapped to an MoU in effect
        Resp school = schoolContracts(admin, f.schoolId());
        assertThat(school.status()).isEqualTo(200);
        assertThat(school.body()).contains("\"unmappedTeachers\":[{\"teacherId\":\"" + teacher + "\"");
    }
}
