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
}
