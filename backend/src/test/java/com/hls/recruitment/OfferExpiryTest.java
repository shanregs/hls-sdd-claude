package com.hls.recruitment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** An issued offer past its deadline expires and the candidate can be offered again. */
class OfferExpiryTest extends RecruitmentTestBase {

    @Autowired
    com.hls.recruitment.internal.OfferService offerService;

    private UUID issuedPastDeadline(String director, UUID candidate) {
        UUID offer = issued(director, candidate, "15000");
        // the trigger guards issued terms, so the fixture lifts it for one statement (as a backdated import would)
        jdbc.execute("alter table job_offer disable trigger trg_job_offer_terms_locked");
        try {
            jdbc.update("update job_offer set response_deadline = ?, offer_date = ? where id = ?", today.minusDays(1), today.minusDays(5), offer);
        } finally {
            jdbc.execute("alter table job_offer enable trigger trg_job_offer_terms_locked");
        }
        return offer;
    }

    @Test
    void theJobExpiresOverdueOffersAndTheCandidateCanBeOfferedAgain() {
        String admin = admin();
        String director = directorToken();
        UUID candidate = selected(admin, phone());
        UUID offer = issuedPastDeadline(director, candidate);

        int expired = offerService.expireOverdue(new UUID(0L, 0L));

        assertThat(expired).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select status from job_offer where id = ?", String.class, offer)).isEqualTo("EXPIRED");
        assertThat(post(BASE + "/candidates/" + candidate + "/offers", director, offerBody("16000")).status()).isEqualTo(201);
    }

    @Test
    void acceptanceAfterTheDeadlineIsRefusedEvenBeforeTheJobRuns() {
        String admin = admin();
        String director = directorToken();
        UUID offer = issuedPastDeadline(director, selected(admin, phone()));

        Resp refused = post(BASE + "/offers/" + offer + "/accept", director, Map.of());

        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains("deadline");
        assertThat(jdbc.queryForObject("select status from job_offer where id = ?", String.class, offer)).isEqualTo("ISSUED");
    }

    @Test
    void anOfferStillWithinItsDeadlineIsLeftAlone() {
        String admin = admin();
        UUID offer = issued(directorToken(), selected(admin, phone()), "15000");

        offerService.expireOverdue(new UUID(0L, 0L));

        assertThat(jdbc.queryForObject("select status from job_offer where id = ?", String.class, offer)).isEqualTo("ISSUED");
    }
}
