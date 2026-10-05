package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Spec 012 US3: mapping Teachers to the positions of a contract, and what keeps a position to one Teacher. */
class PositionAssignmentTest extends SchoolContractsTestBase {

    @SuppressWarnings("unchecked")
    private Map<String, Object> position(String token, UUID school, int number) {
        return positionsOf(contractsOf(token, school).get(0)).stream()
                .filter(p -> ((Number) p.get("number")).intValue() == number)
                .findFirst()
                .orElseThrow();
    }

    private UUID positionId(String token, UUID school, int number) {
        return UUID.fromString((String) position(token, school, number).get("id"));
    }

    @Test
    void aSameSalaryContractFillsTheNextVacantPositionUntilEveryPositionIsFilled() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        contractId(createContract(admin, f.schoolId(), sameSalaryBody(f, director().userId(), 3, "15000")));
        List<UUID> teachers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            teachers.add(teacher(admin));
        }

        for (int i = 0; i < 3; i++) {
            Resp mapped = assign(admin, teachers.get(i), f.schoolId(), null, today);
            assertThat(mapped.status()).as(mapped.body()).isEqualTo(200);
        }
        Resp fourth = assign(admin, teachers.get(3), f.schoolId(), null, today);

        assertThat(fourth.status()).isEqualTo(409);
        assertThat(fourth.body()).contains("All 3 positions are filled");
        for (int n = 1; n <= 3; n++) {
            assertThat(position(admin, f.schoolId(), n).get("teacherId")).isEqualTo(teachers.get(n - 1).toString());
        }
        // the Teacher's view says which position they fill
        String view = get("/api/v1/teachers/" + teachers.get(0), admin).body();
        assertThat(view).contains("\"positionNumber\":1");
        // spec 012 SC-002: the interim placement is gone from every response
        assertThat(view.toLowerCase()).doesNotContain("interim");
    }

    @Test
    void aDifferentSalaryContractNeedsAPositionToBeChosenAndKeepsItsSalary() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        contractId(createContract(admin, f.schoolId(), perTeacherBody(f, director().userId(), "15000", "18000")));
        UUID teacherA = teacher(admin);
        UUID teacherB = teacher(admin);

        assertThat(assign(admin, teacherA, f.schoolId(), null, today).status()).isEqualTo(400);
        assertThat(assign(admin, teacherA, f.schoolId(), UUID.randomUUID(), today).status()).isEqualTo(400);

        Resp chosen = assign(admin, teacherA, f.schoolId(), positionId(admin, f.schoolId(), 2), today);
        assertThat(chosen.status()).as(chosen.body()).isEqualTo(200);
        assertThat(position(admin, f.schoolId(), 2).get("teacherId")).isEqualTo(teacherA.toString());
        assertThat(position(admin, f.schoolId(), 2).get("salary")).isEqualTo("18000.00");

        Resp taken = assign(admin, teacherB, f.schoolId(), positionId(admin, f.schoolId(), 2), today);
        assertThat(taken.status()).isEqualTo(409);
        assertThat(taken.body()).contains("already filled");
        assertThat(assign(admin, teacherB, f.schoolId(), positionId(admin, f.schoolId(), 1), today).status())
                .isEqualTo(200);
    }

    @Test
    void aPositionOfAnotherSchoolsContractIsRefused() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture mine = fixture(admin);
        Fixture other = fixture(admin);
        UUID dir = director().userId();
        contractId(createContract(admin, mine.schoolId(), perTeacherBody(mine, dir, "15000")));
        contractId(createContract(admin, other.schoolId(), perTeacherBody(other, dir, "16000")));
        UUID teacher = teacher(admin);

        Resp refused = assign(admin, teacher, mine.schoolId(), positionId(admin, other.schoolId(), 1), today);

        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.body()).contains("not on this School");
    }

    @Test
    void aTeacherWhoLeavesFreesThePositionFromTheNextDay() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        contractId(createContract(admin, f.schoolId(), startedDaysAgo(sameSalaryBody(f, director().userId(), 1, "15000"), 10)));
        UUID leaver = teacher(admin);
        UUID successor = teacher(admin);
        assertThat(assign(admin, leaver, f.schoolId(), null, today.minusDays(3)).status()).isEqualTo(200);
        assertThat(assign(admin, successor, f.schoolId(), null, today).status()).isEqualTo(409);

        Resp exit = post(
                "/api/v1/teachers/" + leaver + "/status",
                admin,
                Map.of("status", "EXITED", "effectiveOn", today.toString()));
        assertThat(exit.status()).as(exit.body()).isEqualTo(200);

        // the leaver's assignment ended the day before the exit date, so the position is free today
        assertThat(assign(admin, successor, f.schoolId(), null, today).status()).isEqualTo(200);
        assertThat(position(admin, f.schoolId(), 1).get("teacherId")).isEqualTo(successor.toString());
    }

    @Test
    void aScheduledMoveLeavesTheCurrentPositionUntilItTakesEffect() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture here = fixture(admin);
        Fixture there = fixture(admin);
        UUID dir = director().userId();
        contractId(createContract(admin, here.schoolId(), startedDaysAgo(sameSalaryBody(here, dir, 2, "15000"), 10)));
        contractId(createContract(admin, there.schoolId(), startedDaysAgo(sameSalaryBody(there, dir, 2, "16000"), 10)));
        UUID teacher = teacher(admin);
        assertThat(assign(admin, teacher, here.schoolId(), null, today.minusDays(2)).status()).isEqualTo(200);

        Resp scheduled = assign(admin, teacher, there.schoolId(), null, today.plusDays(10));

        assertThat(scheduled.status()).as(scheduled.body()).isEqualTo(200);
        assertThat(position(admin, here.schoolId(), 1).get("teacherId")).isEqualTo(teacher.toString());
        assertThat(position(admin, there.schoolId(), 1).get("teacherId")).isNull();
    }

    @Test
    void submittingTheSameSchoolAgainKeepsTheTeachersPosition() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        contractId(createContract(admin, f.schoolId(), sameSalaryBody(f, director().userId(), 3, "15000")));
        UUID a = teacher(admin);
        UUID b = teacher(admin);
        UUID c = teacher(admin);
        for (UUID t : List.of(a, b, c)) {
            assertThat(assign(admin, t, f.schoolId(), null, today).status()).isEqualTo(200);
        }
        assertThat(post("/api/v1/teachers/" + a + "/status", admin, Map.of("status", "EXITED", "effectiveOn", today.toString()))
                        .status())
                .isEqualTo(200);

        // position 1 is vacant again, but C stays on position 3: asking again is "already placed", not a silent move
        Resp again = assign(admin, c, f.schoolId(), null, today);

        assertThat(again.status()).isEqualTo(409);
        assertThat(position(admin, f.schoolId(), 3).get("teacherId")).isEqualTo(c.toString());
        assertThat(position(admin, f.schoolId(), 1).get("teacherId")).isNull();
    }

    @Test
    void aScheduledMoveCanBeRescheduledToTheSamePosition() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture here = fixture(admin);
        Fixture there = fixture(admin);
        contractId(createContract(admin, there.schoolId(), startedDaysAgo(perTeacherBody(there, director().userId(), "15000", "16000"), 10)));
        UUID teacher = teacher(admin);
        assertThat(assign(admin, teacher, here.schoolId(), null, today.minusDays(2)).status()).isEqualTo(200);
        UUID position = positionId(admin, there.schoolId(), 1);
        assertThat(assign(admin, teacher, there.schoolId(), position, today.plusDays(10)).status()).isEqualTo(200);

        Resp rescheduled = assign(admin, teacher, there.schoolId(), position, today.plusDays(15));

        assertThat(rescheduled.status()).as(rescheduled.body()).isEqualTo(200);
    }

    @Test
    void aTeacherStillInTrainingCannotBeMappedUntilTheyAreActive() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        contractId(createContract(admin, f.schoolId(), sameSalaryBody(f, director().userId(), 2, "15000")));
        Resp created = post("/api/v1/teachers", admin, Map.of("name", uniqueName("Recruit"), "status", "IN_TRAINING"));
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        UUID recruit = created.id();

        Resp refused = assign(admin, recruit, f.schoolId(), null, today);

        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains("still in training");
        assertThat(position(admin, f.schoolId(), 1).get("teacherId")).isNull();
        // a Zone Manager is refused the same way
        assertThat(assign(f.manager().token(), recruit, f.schoolId(), null, today).status()).isEqualTo(409);

        // induction sign-off makes the recruit active; from then on they can be mapped, and may wait unplaced until then
        assertThat(post("/api/v1/teachers/" + recruit + "/status", admin, Map.of("status", "ACTIVE")).status()).isEqualTo(200);
        assertThat(assign(admin, recruit, f.schoolId(), null, today).status()).isEqualTo(200);
        assertThat(position(admin, f.schoolId(), 1).get("teacherId")).isEqualTo(recruit.toString());
    }

    @Test
    void everyMapMoveAndExitIsAudited() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        contractId(createContract(admin, f.schoolId(), sameSalaryBody(f, director().userId(), 2, "15000")));
        UUID teacher = teacher(admin);

        assertThat(assign(admin, teacher, f.schoolId(), null, today).status()).isEqualTo(200);

        assertChangeRecorded(admin, "TEACHER_PLACEMENT", teacher, "school");
        assertChangeRecorded(admin, "TEACHER_PLACEMENT", teacher, "position");
    }

    @Test
    void twoSimultaneousRequestsForTheSamePositionLetOnlyOneThrough() throws Exception {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        contractId(createContract(admin, f.schoolId(), perTeacherBody(f, director().userId(), "15000", "16000")));
        UUID position = positionId(admin, f.schoolId(), 1);
        UUID teacherA = teacher(admin);
        UUID teacherB = teacher(admin);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (UUID t : List.of(teacherA, teacherB)) {
                results.add(pool.submit(() -> {
                    go.await();
                    return assign(admin, t, f.schoolId(), position, today).status();
                }));
            }
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> r : results) {
                statuses.add(r.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        long filled = positionsOf(contractsOf(admin, f.schoolId()).get(0)).stream()
                .filter(p -> p.get("teacherId") != null)
                .count();
        assertThat(filled).isEqualTo(1);
    }
}
