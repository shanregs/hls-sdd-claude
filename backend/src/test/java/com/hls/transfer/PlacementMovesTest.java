package com.hls.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.school.api.ConflictException;
import com.hls.schoolbilling.api.PlacementMoves;
import com.hls.schoolbilling.api.PlacementMoves.AppliedMove;
import com.hls.schoolbilling.api.PlacementMoves.Kind;
import com.hls.schoolbilling.api.PlacementMoves.MoveCheck;
import com.hls.schoolbilling.api.PlacementMoves.MoveSpec;
import com.hls.schoolbilling.api.SchoolContracts;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spec 027 T015, T017 and T030: {@code PlacementMoves.check}, {@code apply}, {@code reverse} and the replacement branch,
 * called directly (the transfer module is covered through the API in the other tests).
 */
class PlacementMovesTest extends TransferTestBase {

    @Autowired
    private PlacementMoves moves;

    @Autowired
    private SchoolContracts contracts;

    @Autowired
    private PlatformTransactionManager transactions;

    private static final UUID ACTOR = UUID.randomUUID();

    private MoveSpec transfer(UUID teacher, UUID from, UUID to, UUID position, LocalDate on) {
        return new MoveSpec(Kind.TRANSFER, teacher, from, null, to, position, null, on);
    }

    private MoveSpec replacement(UUID outgoing, UUID school, UUID incoming, UUID destination, LocalDate on) {
        return new MoveSpec(Kind.REPLACEMENT, outgoing, school, incoming, null, null, destination, on);
    }

    private AppliedMove applyInTx(MoveSpec spec) {
        return new TransactionTemplate(transactions).execute(s -> moves.apply(ACTOR, spec));
    }

    // ---- check

    @Test
    void aValidTransferChecksOkAndWritesNothing() {
        World w = world();
        MoveCheck check = moves.check(transfer(w.t1(), w.schoolA1(), w.schoolB1(), null, in(3)));
        assertThat(check.ok()).as(check.problems().toString()).isTrue();
        assertThat(check.problems()).isEmpty();
        assertThat(rows(w.t1())).hasSize(1);
    }

    @Test
    void checkReportsEachTransferProblem() {
        World w = world();
        assertThat(moves.check(transfer(w.t3(), w.schoolA1(), w.schoolB1(), null, in(3))).problems())
                .anyMatch(p -> p.contains("not placed at this School"));
        assertThat(moves.check(transfer(w.t1(), w.schoolB1(), w.schoolA1(), null, in(3))).problems())
                .anyMatch(p -> p.contains("not placed at this School"));
        assertThat(moves.check(transfer(w.t1(), w.schoolA1(), w.schoolB1(), null, LocalDate.now())).problems())
                .anyMatch(p -> p.contains("must be after"));
        assertThat(moves.check(transfer(w.t1(), w.schoolA1(), w.schoolA1(), null, in(3))).problems())
                .anyMatch(p -> p.contains("different"));
        // a pending MoU has no positions to choose
        assertThat(moves.check(transfer(w.t1(), w.schoolA1(), w.schoolB1(), UUID.randomUUID(), in(3))).problems())
                .anyMatch(p -> p.contains("no MoU yet"));
    }

    @Test
    void checkRefusesAScheduledMoveAndAnInactiveSchool() {
        World w = world();
        assertThat(assign(w.admin(), w.t1(), w.schoolB1(), null, in(10)).status()).isEqualTo(200);
        assertThat(moves.check(transfer(w.t1(), w.schoolA1(), w.schoolB1(), null, in(3))).problems())
                .anyMatch(p -> p.contains("already scheduled"));
        World other = world();
        jdbc.update("update school set active = false where id = ?", other.schoolB1());
        assertThat(moves.check(transfer(other.t1(), other.schoolA1(), other.schoolB1(), null, in(3))).problems())
                .anyMatch(p -> p.contains("inactive"));
    }

    @Test
    void checkAppliesTheVacancyAndPositionRules() {
        World w = world();
        Fixture full = schoolWithMou(w, 1);
        UUID resident = teacher(w.admin());
        placeTeacher(w.admin(), resident, full.schoolId());
        assertThat(moves.check(transfer(w.t1(), w.schoolA1(), full.schoolId(), null, in(3))).problems())
                .anyMatch(p -> p.contains("filled"));
        Fixture perTeacher = schoolWithPerTeacherMou(w, 2);
        assertThat(moves.check(transfer(w.t1(), w.schoolA1(), perTeacher.schoolId(), null, in(3))).problems())
                .anyMatch(p -> p.contains("Choose the position"));
        assertThat(moves.check(transfer(w.t1(), w.schoolA1(), perTeacher.schoolId(), positionOf(perTeacher.schoolId(), 2), in(3))).ok())
                .isTrue();
        assertThat(moves.vacantPositions(perTeacher.schoolId(), in(3))).hasSize(2);
        assertThat(moves.vacantPositions(w.schoolB1(), in(3))).isEmpty();
    }

    // ---- apply and reverse

    @Test
    void applyEndsTheOutgoingRowTheDayBeforeAndStartsTheNewOneOnTheDate() {
        World w = world();
        AppliedMove applied = applyInTx(transfer(w.t1(), w.schoolA1(), w.schoolB1(), null, in(3)));
        assertThat(applied.endedAssignmentIds()).hasSize(1);
        assertThat(applied.createdAssignmentIds()).hasSize(1);
        for (int d = 0; d <= 6; d++) {
            assertThat(schoolOn(w.t1(), in(d))).isEqualTo(d < 3 ? w.schoolA1() : w.schoolB1());
        }
    }

    @Test
    void aCallerRollbackUndoesApply() {
        World w = world();
        new TransactionTemplate(transactions).execute(s -> {
            moves.apply(ACTOR, transfer(w.t1(), w.schoolA1(), w.schoolB1(), null, in(3)));
            s.setRollbackOnly();
            return null;
        });
        assertThat(rows(w.t1())).hasSize(1);
        assertThat(rows(w.t1()).get(0).get("ends_on")).isNull();
    }

    @Test
    void aFailingApplyIsAConflictAndWritesNothing() {
        World w = world();
        jdbc.update("update school set active = false where id = ?", w.schoolB1());
        assertThatThrownBy(() -> applyInTx(transfer(w.t1(), w.schoolA1(), w.schoolB1(), null, in(3))))
                .isInstanceOf(ConflictException.class);
        assertThat(rows(w.t1())).hasSize(1);
        assertThat(rows(w.t1()).get(0).get("ends_on")).isNull();
    }

    @Test
    void reverseCancelsFutureRowsAndRestoresTheOpenEndButRefusesAStartedRow() {
        World w = world();
        AppliedMove applied = applyInTx(transfer(w.t1(), w.schoolA1(), w.schoolB1(), null, in(3)));
        new TransactionTemplate(transactions).executeWithoutResult(s -> moves.reverse(ACTOR, applied));
        assertThat(rows(w.t1())).hasSize(1);
        assertThat(rows(w.t1()).get(0).get("ends_on")).isNull();

        World other = world();
        AppliedMove started = applyInTx(transfer(other.t1(), other.schoolA1(), other.schoolB1(), null, in(3)));
        jdbc.update("update contract_assignment set starts_on = current_date - 5, ends_on = current_date - 1 where id = ?", started.endedAssignmentIds().get(0));
        jdbc.update("update contract_assignment set starts_on = current_date where id = ?", started.createdAssignmentIds().get(0));
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(s -> moves.reverse(ACTOR, started)))
                .isInstanceOf(ConflictException.class);
    }

    // ---- replacement

    @Test
    void replacementReusesThePositionAndKeepsTheFilledCountEveryDay() {
        World w = world();
        Fixture mou = schoolWithMou(w, 2);
        UUID outgoing = teacher(w.admin());
        placeTeacher(w.admin(), outgoing, mou.schoolId());
        UUID incoming = w.t3();
        LocalDate date = in(3);
        int before = contracts.occupancyOf(mou.schoolId(), date.minusDays(1)).filled();
        UUID position = jdbc.queryForObject(
                "select position_id from contract_assignment where teacher_id = ? and status = 'ACTIVE'", UUID.class, outgoing);
        AppliedMove applied = applyInTx(replacement(outgoing, mou.schoolId(), incoming, null, date));
        assertThat(applied.createdAssignmentIds()).hasSize(1);
        assertThat(jdbc.queryForObject(
                        "select position_id from contract_assignment where teacher_id = ? and status = 'ACTIVE'", UUID.class, incoming))
                .isEqualTo(position);
        for (int d = 2; d <= 4; d++) {
            assertThat(contracts.occupancyOf(mou.schoolId(), in(d)).filled()).as("day " + d).isEqualTo(before);
        }
        assertThat(schoolOn(outgoing, date)).isNull();
        assertThat(schoolOn(incoming, date.minusDays(1))).isNull();
        assertThat(schoolOn(incoming, date)).isEqualTo(mou.schoolId());
    }

    @Test
    void replacementWithADestinationPlacesTheOutgoingTeacherThere() {
        World w = world();
        UUID incoming = w.t3();
        AppliedMove applied = applyInTx(replacement(w.t1(), w.schoolA1(), incoming, w.schoolB1(), in(3)));
        assertThat(applied.createdAssignmentIds()).hasSize(2);
        assertThat(schoolOn(w.t1(), in(2))).isEqualTo(w.schoolA1());
        assertThat(schoolOn(w.t1(), in(3))).isEqualTo(w.schoolB1());
        assertThat(schoolOn(incoming, in(3))).isEqualTo(w.schoolA1());
    }

    @Test
    void replacementChecksTheReplacementTeacher() {
        World w = world();
        UUID placed = teacher(w.admin());
        placeTeacher(w.admin(), placed, w.schoolB1());
        List<String> placedProblems = moves.check(replacement(w.t1(), w.schoolA1(), placed, null, in(3))).problems();
        assertThat(placedProblems).anyMatch(p -> p.contains("already placed"));
        assertThat(moves.check(replacement(w.t1(), w.schoolA1(), w.t1(), null, in(3))).problems())
                .anyMatch(p -> p.contains("replace themselves"));
        assertThat(moves.check(replacement(w.t1(), w.schoolA1(), w.t3(), null, in(3))).ok()).isTrue();
    }

    @Test
    void aFailingReplacementLeavesBothTeachersUnchanged() {
        World w = world();
        jdbc.update("update school set active = false where id = ?", w.schoolB1());
        assertThatThrownBy(() -> applyInTx(replacement(w.t1(), w.schoolA1(), w.t3(), w.schoolB1(), in(3))))
                .isInstanceOf(ConflictException.class);
        assertThat(rows(w.t1())).hasSize(1);
        assertThat(rows(w.t1()).get(0).get("ends_on")).isNull();
        assertThat(rows(w.t3())).isEmpty();
    }
}
