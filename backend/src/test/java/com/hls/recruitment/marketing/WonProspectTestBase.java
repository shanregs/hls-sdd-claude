package com.hls.recruitment.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Takes a prospect all the way to "won" (proposal, Final Stage, Director approval) for the win and contract tests. */
public abstract class WonProspectTestBase extends MarketingTestBase {

    protected record Won(UUID zone, UUID prospect) {}

    protected Won wonProspect(String admin) {
        UUID zone = zone(admin);
        UUID prospect = prospect(admin, zone);
        Resp proposal = post(M + "/prospects/" + prospect + "/proposals", admin,
                Map.of("teacherCount", 4, "startMonth", today.plusMonths(1).toString().substring(0, 7), "salaryMode", "SAME_FOR_ALL", "rate", "15000"));
        assertThat(proposal.status()).as(proposal.body()).isEqualTo(201);
        assertThat(post(M + "/prospects/" + prospect + "/stage", admin, Map.of("stage", "FINAL_STAGE")).status()).isEqualTo(200);
        Resp review = post(M + "/prospects/" + prospect + "/review", directorToken(), Map.of("decision", "APPROVE"));
        assertThat(review.status()).as(review.body()).isEqualTo(200);
        return new Won(zone, prospect);
    }

    protected Map<String, Object> winBody(UUID place) {
        Map<String, Object> body = new HashMap<>();
        body.put("placeId", place);
        body.put("billingContact", "accounts@school.test");
        return body;
    }

    /** The School created by the win, for the 012 contract helpers. */
    protected UUID schoolOf(String admin, UUID prospect) {
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) get(M + "/prospects/" + prospect, admin).map().get("row");
        return UUID.fromString((String) row.get("schoolId"));
    }
}
