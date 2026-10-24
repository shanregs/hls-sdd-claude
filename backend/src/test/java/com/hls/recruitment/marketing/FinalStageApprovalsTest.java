package com.hls.recruitment.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 028 T043: the FINAL_STAGE_PROSPECT inbox kind lists prospects awaiting review, and a reviewed one is gone. */
class FinalStageApprovalsTest extends MarketingTestBase {

    private static final String INBOX = "/api/v1/dashboard/inbox?kind=FINAL_STAGE_PROSPECT&size=100";

    @SuppressWarnings("unchecked")
    private static List<String> inboxIds(Resp resp) {
        return ((List<Map<String, Object>>) resp.map().get("items")).stream().map(i -> (String) i.get("itemId")).toList();
    }

    @Test
    void aProspectAtTheFinalStageIsInTheInboxUntilReviewed() {
        String admin = admin();
        String director = directorToken();
        UUID prospect = prospect(admin, zone(admin));
        assertThat(post(M + "/prospects/" + prospect + "/proposals", admin,
                        Map.of("teacherCount", 4, "startMonth", "2026-12", "salaryMode", "SAME_FOR_ALL", "rate", "15000")).status())
                .isEqualTo(201);
        assertThat(post(M + "/prospects/" + prospect + "/stage", admin, Map.of("stage", "FINAL_STAGE")).status()).isEqualTo(200);

        Resp before = get(INBOX, director);
        assertThat(before.status()).as(before.body()).isEqualTo(200);
        assertThat(inboxIds(before)).contains(prospect.toString());

        assertThat(post(M + "/prospects/" + prospect + "/review", director, Map.of("decision", "REJECT", "reason", "Rate too low")).status())
                .isEqualTo(200);
        assertThat(inboxIds(get(INBOX, director))).doesNotContain(prospect.toString());
    }
}
