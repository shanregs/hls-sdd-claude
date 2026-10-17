package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PayrollRunTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 013b T038 (grown in later phases): each change to a run leaves an audit entry with the actor, the run and the reason. */
class PayrollAuditTest extends PayrollRunTestBase {

    private static final String ENTITY = "PAYROLL_RUN";

    @BeforeEach
    void clean() {
        resetRuns();
    }

    private static List<String> fields(List<Map<String, Object>> entries) {
        return entries.stream().map(e -> (String) e.get("field")).toList();
    }

    @Test
    void startRecomputeAndCancelAreAudited() {
        Signed admin = signInAs(Role.ADMIN);
        UUID run = startAndCompute(admin.token());
        assertThat(post(RUNS + "/" + run + "/recompute", admin.token(), Map.of()).status()).isEqualTo(202);
        awaitComputed(admin.token(), run);
        assertThat(post(RUNS + "/" + run + "/cancel", admin.token(), Map.of("reason", "Wrong month")).status()).isEqualTo(200);

        List<Map<String, Object>> entries = changeEntries(admin.token(), ENTITY, run, 4);

        assertThat(fields(entries)).contains("created", "recomputed", "status", "cancelled");
        assertThat(entries).allSatisfy(e -> assertThat(e).containsEntry("actorUserId", admin.userId().toString()));
        assertThat(entries)
                .filteredOn(e -> "cancelled".equals(e.get("field")))
                .singleElement()
                .satisfies(e -> assertThat(e.get("afterValue")).isEqualTo("Wrong month"));
        assertThat(entries)
                .filteredOn(e -> "status".equals(e.get("field")))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.get("beforeValue")).isEqualTo("DRAFT");
                    assertThat(e.get("afterValue")).isEqualTo("CANCELLED");
                });
    }

    @Test
    void submitReturnAndApproveAreAuditedWithTheirActorsAndTheReason() {
        Signed admin = signInAs(Role.ADMIN);
        Signed director = signInAs(Role.DIRECTOR);
        Draft draft = readyDraft(admin.token());
        submitRun(admin.token(), draft.run());
        returnRun(director.token(), draft.run(), "Look again");
        submitRun(admin.token(), draft.run());
        approveRun(director.token(), draft.run());

        List<Map<String, Object>> entries = changeEntries(admin.token(), ENTITY, draft.run(), 6);

        List<String> transitions = entries.stream()
                .filter(e -> "status".equals(e.get("field")))
                .map(e -> e.get("beforeValue") + ">" + e.get("afterValue") + " by " + e.get("actorUserId"))
                .toList();
        assertThat(transitions)
                .contains(
                        "DRAFT>AWAITING_APPROVAL by " + admin.userId(),
                        "AWAITING_APPROVAL>DRAFT by " + director.userId(),
                        "AWAITING_APPROVAL>APPROVED by " + director.userId());
        assertThat(entries)
                .filteredOn(e -> "returned".equals(e.get("field")))
                .singleElement()
                .satisfies(e -> assertThat(e.get("afterValue")).isEqualTo("Look again"));
    }

    @Test
    void aRefusedChangeLeavesNoAuditEntry() {
        Signed admin = signInAs(Role.ADMIN);
        Draft draft = readyDraft(admin.token());
        int before = changeCount(admin.token(), ENTITY, draft.run());

        assertThat(post(RUNS + "/" + draft.run() + "/cancel", admin.token(), Map.of()).status()).isEqualTo(400);
        assertThat(returnRun(signInAs(Role.DIRECTOR).token(), draft.run(), "x").status()).isEqualTo(409);

        assertThat(changeCount(admin.token(), ENTITY, draft.run())).isEqualTo(before);
    }

    @Test
    void recordingAndWithdrawingAnAdjustmentAreAuditedWithTheActorTheAmountAndTheReason() {
        Signed admin = signInAs(Role.ADMIN);
        UUID teacher = teacher(admin.token());
        Resp created = post(
                "/api/v1/payroll/adjustments",
                admin.token(),
                Map.of("personKind", "TEACHER", "personId", teacher.toString(), "kind", "CORRECTION", "amount", 2000, "reason", "August was underpaid"));
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        Resp withdrawn = post(
                "/api/v1/payroll/adjustments/" + created.id() + "/withdraw", admin.token(), Map.of("reason", "Entered twice"));
        assertThat(withdrawn.status()).as(withdrawn.body()).isEqualTo(200);

        List<Map<String, Object>> entries = changeEntries(admin.token(), "PAYROLL_ADJUSTMENT", created.id(), 3);

        assertThat(fields(entries)).contains("created", "status", "withdrawn");
        assertThat(entries).allSatisfy(e -> assertThat(e).containsEntry("actorUserId", admin.userId().toString()));
        assertThat(entries)
                .filteredOn(e -> "created".equals(e.get("field")))
                .singleElement()
                .satisfies(e -> assertThat((String) e.get("afterValue")).contains("2,000.00").contains("August was underpaid"));
        assertThat(entries)
                .filteredOn(e -> "status".equals(e.get("field")))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.get("beforeValue")).isEqualTo("WAITING");
                    assertThat(e.get("afterValue")).isEqualTo("WITHDRAWN");
                });
        assertThat(entries)
                .filteredOn(e -> "withdrawn".equals(e.get("field")))
                .singleElement()
                .satisfies(e -> assertThat(e.get("afterValue")).isEqualTo("Entered twice"));
    }


    @Test
    void startingASupplementaryRunIsAuditedWithItsNumberAndTheActor() {
        Signed admin = signInAs(Role.ADMIN);
        Site site = site(admin.token());
        UUID paid = teacherAt(admin.token(), site.schoolId(), java.time.LocalDate.now().minusDays(200));
        teacherSalary(admin.token(), paid, "26000", java.time.LocalDate.now().minusDays(300));
        lockTeacherMonth(paid);
        UUID leftOut = teacherAt(admin.token(), site.schoolId(), java.time.LocalDate.now().minusDays(200));
        teacherSalary(admin.token(), leftOut, "31200", java.time.LocalDate.now().minusDays(300));
        UUID primary = startAndCompute(admin.token());
        submitAndApprove(admin.token(), primary);

        Resp started = post(RUNS, admin.token(), Map.of("month", month.toString(), "supplementary", true));

        assertThat(started.status()).as(started.body()).isEqualTo(202);
        List<Map<String, Object>> entries = changeEntries(admin.token(), ENTITY, started.id(), 1);
        assertThat(entries).anySatisfy(e -> {
            assertThat(e).containsEntry("field", "created");
            assertThat(e).containsEntry("actorUserId", admin.userId().toString());
            assertThat((String) e.get("afterValue")).contains("supplementary run 1");
        });
    }

    @Test
    void recordingAndCorrectingAPaymentAreAuditedWithTheActorTheDateTheModeAndTheReference() {
        Signed admin = signInAs(Role.ADMIN);
        Site site = site(admin.token());
        UUID paid = teacherAt(admin.token(), site.schoolId(), java.time.LocalDate.now().minusDays(200));
        teacherSalary(admin.token(), paid, "26000", java.time.LocalDate.now().minusDays(300));
        lockTeacherMonth(paid);
        UUID run = startAndCompute(admin.token());
        submitAndApprove(admin.token(), run);
        Object lineId = lineOf(admin.token(), run, paid).get("id");
        String uri = RUNS + "/" + run + "/lines/" + lineId + "/payment";
        String today = java.time.LocalDate.now().toString();

        assertThat(put(uri, admin.token(), Map.of("paid", true, "paidOn", today, "mode", "CASH", "reference", "R-1")).status())
                .isEqualTo(200);
        assertThat(put(uri, admin.token(), Map.of("paid", true, "paidOn", today, "mode", "UPI", "reference", "R-2")).status())
                .isEqualTo(200);
        // a refused entry leaves nothing behind
        assertThat(put(uri, admin.token(), Map.of("paid", true, "paidOn", today, "mode", "NOPE")).status()).isEqualTo(400);

        List<Map<String, Object>> entries = changeEntries(admin.token(), "PAYROLL_PAYMENT", lineId, 2);

        assertThat(entries).hasSize(2).allSatisfy(e -> assertThat(e).containsEntry("actorUserId", admin.userId().toString()));
        assertThat(fields(entries)).containsExactlyInAnyOrder("recorded", "corrected");
        assertThat(entries)
                .filteredOn(e -> "recorded".equals(e.get("field")))
                .singleElement()
                .satisfies(e -> assertThat((String) e.get("afterValue")).contains("CASH").contains("R-1"));
        assertThat(entries)
                .filteredOn(e -> "corrected".equals(e.get("field")))
                .singleElement()
                .satisfies(e -> assertThat((String) e.get("afterValue")).contains("UPI").contains("R-2"));
    }
}
