package com.hls.recruitment.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The pipeline: stage moves, hold, lost and reopen, the Final Stage rule, the review, and the board. */
class PipelineApiTest extends MarketingTestBase {

    private Resp stage(String token, UUID prospect, String stage, String reason) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("stage", stage);
        body.put("reason", reason);
        return post(M + "/prospects/" + prospect + "/stage", token, body);
    }

    private Resp proposal(String token, UUID prospect) {
        return post(M + "/prospects/" + prospect + "/proposals", token,
                Map.of("teacherCount", 4, "startMonth", "2026-12", "salaryMode", "SAME_FOR_ALL", "rate", "15000"));
    }

    private UUID finalStage(String token, UUID zone) {
        UUID prospect = prospect(token, zone);
        assertThat(proposal(token, prospect).status()).isEqualTo(201);
        assertThat(stage(token, prospect, "FINAL_STAGE", null).status()).isEqualTo(200);
        return prospect;
    }

    private String stageOf(String token, UUID prospect) {
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) get(M + "/prospects/" + prospect, token).map().get("row");
        return (String) row.get("stage");
    }

    @Test
    void aProspectMovesForwardAndBackAmongTheActiveStagesAndEveryMoveIsRecorded() {
        String admin = admin();
        UUID prospect = prospect(admin, zone(admin));

        for (String next : List.of("CONTACTED", "VISIT", "FOLLOW_UP", "INTERESTED", "NEGOTIATION", "INTERESTED")) {
            Resp moved = stage(admin, prospect, next, null);
            assertThat(moved.status()).as(next + " " + moved.body()).isEqualTo(200);
        }

        assertThat(stageOf(admin, prospect)).isEqualTo("INTERESTED");
        assertThat(stage(admin, prospect, "INTERESTED", null).status()).isEqualTo(409);
        assertThat(stage(admin, prospect, "MOU", null).status()).isEqualTo(400);
        assertThat(stage(admin, prospect, "NOWHERE", null).status()).isEqualTo(400);
        String history = get(M + "/prospects/" + prospect, admin).body();
        assertThat(history).contains("\"stageHistory\"", "CONTACTED", "NEGOTIATION");
        assertChangeRecorded(admin, "PROSPECT", prospect, "stage");
    }

    @Test
    void onHoldResumesToTheStageItLeftAndLostNeedsAReasonAndReopensToo() {
        String admin = admin();
        UUID prospect = prospect(admin, zone(admin));
        stage(admin, prospect, "NEGOTIATION", null);

        assertThat(stage(admin, prospect, "ON_HOLD", null).status()).isEqualTo(200);
        assertThat(stageOf(admin, prospect)).isEqualTo("ON_HOLD");
        assertThat(stage(admin, prospect, "VISIT", null).status()).isEqualTo(409);
        assertThat(stage(admin, prospect, "RESUME", null).status()).isEqualTo(200);
        assertThat(stageOf(admin, prospect)).isEqualTo("NEGOTIATION");

        assertThat(stage(admin, prospect, "LOST", " ").status()).isEqualTo(400);
        assertThat(stage(admin, prospect, "LOST", "Chose another vendor").status()).isEqualTo(200);
        assertThat(get(M + "/prospects/" + prospect, admin).body()).contains("Chose another vendor");
        assertThat(stage(admin, prospect, "RESUME", null).status()).isEqualTo(409);
        assertThat(stage(admin, prospect, "REOPEN", null).status()).isEqualTo(200);
        assertThat(stageOf(admin, prospect)).isEqualTo("NEGOTIATION");
        assertThat(stage(admin, prospect, "REOPEN", null).status()).isEqualTo(409);
    }

    @Test
    void finalStageNeedsAProposalFirst() {
        String admin = admin();
        UUID prospect = prospect(admin, zone(admin));

        Resp refused = stage(admin, prospect, "FINAL_STAGE", null);
        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains("proposal");
        proposal(admin, prospect);
        assertThat(stage(admin, prospect, "FINAL_STAGE", null).status()).isEqualTo(200);
    }

    @Test
    void aDirectorApprovesAnyAZoneManagerOnlyTheirZoneAndAdminIsRefused() {
        String admin = admin();
        UUID zoneA = zone(admin);
        UUID zoneB = zone(admin);
        ManagerCtx managerA = newManager(admin, zoneA);
        UUID inA = finalStage(admin, zoneA);
        UUID inB = finalStage(admin, zoneB);
        UUID second = finalStage(admin, zoneA);
        String review = "/review";

        assertThat(post(M + "/prospects/" + inA + review, admin, Map.of("decision", "APPROVE")).status()).isEqualTo(403);
        assertThat(post(M + "/prospects/" + inB + review, managerA.token(), Map.of("decision", "APPROVE")).status()).isEqualTo(404);
        Resp approved = post(M + "/prospects/" + inA + review, managerA.token(), Map.of("decision", "APPROVE"));
        assertThat(approved.status()).as(approved.body()).isEqualTo(200);
        assertThat(approved.body()).contains("\"won\":true", "REVIEW_APPROVED", "\"effectiveStage\":\"WON\"");
        assertThat(post(M + "/prospects/" + inB + review, directorToken(), Map.of("decision", "APPROVE")).status()).isEqualTo(200);
        assertThat(post(M + "/prospects/" + inA + review, directorToken(), Map.of("decision", "APPROVE")).status()).isEqualTo(409);
        assertThat(post(M + "/prospects/" + second + review, directorToken(), Map.of("decision", "MAYBE")).status()).isEqualTo(400);
        assertChangeRecorded(admin, "PROSPECT", inA, "review");
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            assertThat(post(M + "/prospects/" + second + review, signInAs(role).token(), Map.of("decision", "APPROVE")).status()).isEqualTo(403);
        }
    }

    @Test
    void aRejectionNeedsAReasonAndReturnsTheProspectToNegotiation() {
        String admin = admin();
        UUID prospect = finalStage(admin, zone(admin));
        String director = directorToken();

        assertThat(post(M + "/prospects/" + prospect + "/review", director, Map.of("decision", "REJECT")).status()).isEqualTo(400);
        Resp rejected = post(M + "/prospects/" + prospect + "/review", director, Map.of("decision", "REJECT", "reason", "Rate too low"));

        assertThat(rejected.status()).as(rejected.body()).isEqualTo(200);
        assertThat(stageOf(admin, prospect)).isEqualTo("NEGOTIATION");
        assertThat(rejected.body()).contains("REVIEW_REJECTED", "Rate too low");
        // a prospect not in Final Stage cannot be reviewed, and a won prospect cannot be moved
        assertThat(post(M + "/prospects/" + prospect + "/review", director, Map.of("decision", "APPROVE")).status()).isEqualTo(409);
        stage(admin, prospect, "FINAL_STAGE", null);
        post(M + "/prospects/" + prospect + "/review", director, Map.of("decision", "APPROVE"));
        assertThat(stage(admin, prospect, "VISIT", null).status()).isEqualTo(409);
    }

    @Test
    void theBoardCountsMatchTheListAndAZoneManagerSeesOnlyTheirZone() {
        String admin = admin();
        UUID zoneA = zone(admin);
        UUID zoneB = zone(admin);
        ManagerCtx managerA = newManager(admin, zoneA);
        UUID a1 = prospect(admin, zoneA);
        UUID a2 = prospect(admin, zoneA);
        UUID b1 = prospect(admin, zoneB);
        stage(admin, a2, "VISIT", null);
        stage(admin, b1, "VISIT", null);

        Resp board = get(M + "/pipeline?zone=" + zoneA, admin);
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) board.map().get("counts");
        assertThat(counts).containsEntry("PROSPECT", 1).containsEntry("VISIT", 1);
        assertThat(board.body()).contains(a1.toString(), a2.toString()).doesNotContain(b1.toString());
        Resp mine = get(M + "/pipeline", managerA.token());
        assertThat(mine.body()).contains(a1.toString()).doesNotContain(b1.toString());
        assertThat(get(M + "/prospects?stage=VISIT&zone=" + zoneA, admin).body()).contains(a2.toString()).doesNotContain(a1.toString());
        assertThat(get(M + "/pipeline", signInAs(Role.TEACHER).token()).status()).isEqualTo(403);
    }
}
