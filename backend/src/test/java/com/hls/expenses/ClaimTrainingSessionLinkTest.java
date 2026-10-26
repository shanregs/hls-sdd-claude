package com.hls.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.support.ExpensesTestBase;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 024 T066 (T-015): a Training Stay claim may link to a training session; spec 015 behaviour is unchanged. */
class ClaimTrainingSessionLinkTest extends ExpensesTestBase {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> linkOf(Resp detail) {
        return (Map<String, Object>) detail.map().get("link");
    }

    private UUID session(String admin, String status) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "MONTHLY");
        body.put("topic", uniqueName("Claim training"));
        body.put("date", LocalDate.now().plusDays(5).toString());
        body.put("startsAt", "10:00");
        body.put("endsAt", "13:00");
        body.put("venueKind", "PHYSICAL");
        body.put("venue", "Head office");
        body.put("publish", true);
        Resp created = post("/api/v1/training/sessions", admin, body);
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        if (!"PUBLISHED".equals(status)) {
            jdbc.update("update training_session set status = ? where id = ?", status, created.id());
        }
        return created.id();
    }

    @Test
    void trainingStayLinksToASessionAndKeepsItsLabel() {
        String claimant = admin().token();
        UUID session = session(claimant, "PUBLISHED");
        Map<String, Object> body = claimBody(today(), "1500.00", "TRAINING_STAY");
        body.put("link", linkTo("TRAINING_SESSION", session));
        UUID id = newDraft(claimant, body);
        uploadBill(claimant, id, "bill.jpg", JPG);
        assertThat(submit(claimant, id).status()).isEqualTo(200);

        Resp detail = get(MINE + "/" + id, claimant);
        assertThat(linkOf(detail)).containsEntry("kind", "TRAINING_SESSION").containsEntry("ref", session.toString());
        assertThat((String) linkOf(detail).get("label")).contains("Training session");
        // the session is cancelled afterwards: the claim still shows what it was linked to
        jdbc.update("update training_session set status = 'CANCELLED' where id = ?", session);
        assertThat((String) linkOf(get(MINE + "/" + id, claimant)).get("label")).contains("Training session");
    }

    @Test
    void theOptionsListSessionsAndAnUnknownOrCancelledSessionIsRefused() {
        String claimant = admin().token();
        UUID open = session(claimant, "PUBLISHED");
        UUID cancelled = session(claimant, "CANCELLED");
        Resp options = get(MINE + "/link-options?category=TRAINING_STAY&date=" + today(), claimant);
        assertThat(options.status()).isEqualTo(200);
        assertThat(options.body()).contains(open.toString()).contains("TRAINING_SESSION").doesNotContain(cancelled.toString());

        for (UUID bad : new UUID[] {UUID.randomUUID(), cancelled}) {
            Map<String, Object> body = claimBody(today(), "100.00", "TRAINING_STAY");
            body.put("link", linkTo("TRAINING_SESSION", bad));
            assertThat(createClaim(claimant, body).status()).isEqualTo(400);
        }
    }

    @Test
    void travelAndFuelAndOtherCategoriesRefuseASessionLink() {
        String claimant = admin().token();
        UUID session = session(claimant, "PUBLISHED");
        Map<String, Object> travel = claimBody(today(), "100.00", "TRAVEL_FUEL");
        travel.put("link", linkTo("TRAINING_SESSION", session));
        assertThat(createClaim(claimant, travel).status()).isEqualTo(400);
        Map<String, Object> other = claimBody(today(), "100.00", "OFFICE_SUPPLIES");
        other.put("link", linkTo("TRAINING_SESSION", session));
        assertThat(createClaim(claimant, other).status()).isEqualTo(400);
        assertThat(get(MINE + "/link-options?category=TRAVEL_FUEL&date=" + today(), claimant).body()).doesNotContain(session.toString());
    }
}
