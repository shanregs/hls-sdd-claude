package com.hls.recruitment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutcomeAssessmentTest extends RecruitmentTestBase {

    @Test
    void anOutcomeChangeKeepsTheEarlierOneInTheHistoryAndWaitlistedCanBecomeSelected() {
        String admin = admin();
        UUID candidate = candidate(admin, drive(admin));

        assertThat(outcome(admin, candidate, "WAITLISTED").status()).isEqualTo(200);
        Resp selected = outcome(admin, candidate, "SELECTED");
        assertThat(selected.status()).as(selected.body()).isEqualTo(200);
        assertThat(selected.map()).containsEntry("outcome", "SELECTED");
        assertThat(outcome(admin, candidate, "SELECTED").status()).isEqualTo(409);

        Resp history = get(BASE + "/candidates/" + candidate + "/history", admin);
        assertThat(history.status()).isEqualTo(200);
        assertThat(history.body()).contains("WAITLISTED").contains("SELECTED").contains("Tester");
        assertThat(outcome(admin, candidate, "MAYBE").status()).isEqualTo(400);
    }

    @Test
    void aReAssessmentAddsRowsAndTheLatestIsCurrent() {
        String admin = admin();
        UUID candidate = candidate(admin, drive(admin));

        Resp first = post(BASE + "/candidates/" + candidate + "/assessment", admin,
                Map.of("scores", Map.of("SPEAKING", 3, "ENGLISH", 4, "COMMUNICATION", 3), "remarks", "first"));
        assertThat(first.status()).as(first.body()).isEqualTo(200);
        Resp second = post(BASE + "/candidates/" + candidate + "/assessment", admin,
                Map.of("scores", Map.of("SPEAKING", 5, "ENGLISH", 4, "COMMUNICATION", 5), "remarks", "second"));

        @SuppressWarnings("unchecked")
        Map<String, Object> current = (Map<String, Object>) second.map().get("assessment");
        assertThat(current).containsEntry("number", 2).containsEntry("remarks", "second");
        assertThat(current.get("scores").toString()).contains("SPEAKING=5");
        Integer rows = jdbc.queryForObject("select count(*) from assessment_score where candidate_id = ?", Integer.class, candidate);
        assertThat(rows).isEqualTo(6);
        assertThat(get(BASE + "/candidates/" + candidate + "/history", admin).body()).contains("ASSESSMENT 1", "ASSESSMENT 2");
    }

    @Test
    void scoresOutsideOneToFiveOrAnUnknownCriterionAreA400() {
        String admin = admin();
        UUID candidate = candidate(admin, drive(admin));
        String url = BASE + "/candidates/" + candidate + "/assessment";

        assertThat(post(url, admin, Map.of("scores", Map.of("SPEAKING", 6))).status()).isEqualTo(400);
        assertThat(post(url, admin, Map.of("scores", Map.of("SPEAKING", 0))).status()).isEqualTo(400);
        assertThat(post(url, admin, Map.of("scores", Map.of("HANDWRITING", 3))).status()).isEqualTo(400);
        assertThat(post(url, admin, Map.of("scores", Map.of())).status()).isEqualTo(400);
        Integer rows = jdbc.queryForObject("select count(*) from assessment_score where candidate_id = ?", Integer.class, candidate);
        assertThat(rows).isZero();
    }

    @Test
    void fourSelectedTwoWaitlistedFourRejectedAreCountedOnTheDrive() {
        String admin = admin();
        UUID drive = drive(admin);
        List<String> outcomes = List.of("SELECTED", "SELECTED", "SELECTED", "SELECTED", "WAITLISTED", "WAITLISTED",
                "REJECTED", "REJECTED", "REJECTED", "REJECTED");
        for (String o : outcomes) {
            assertThat(outcome(admin, candidate(admin, drive), o).status()).isEqualTo(200);
        }

        Resp view = get(BASE + "/drives/" + drive, admin);

        assertThat(view.map()).containsEntry("candidates", 10);
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) view.map().get("outcomes");
        assertThat(counts).containsEntry("SELECTED", 4).containsEntry("WAITLISTED", 2).containsEntry("REJECTED", 4).containsEntry("PENDING", 0);
        assertThat(get(BASE + "/candidates?drive=" + drive + "&outcome=SELECTED", admin).body().split("\"outcome\":\"SELECTED\"").length - 1).isEqualTo(4);
    }
}
