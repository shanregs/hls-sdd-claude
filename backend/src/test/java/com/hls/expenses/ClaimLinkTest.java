package com.hls.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.support.ExpensesTestBase;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 015 US2 (FR-010, FR-011): what a claim may link to, and that the link is kept as it was at submission. */
class ClaimLinkTest extends ExpensesTestBase {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> linkOf(Resp detail) {
        return (Map<String, Object>) detail.map().get("link");
    }

    @Test
    void trainingStayLinksToABatchAndKeepsItsNameAndDates() {
        String claimant = admin().token();
        LocalDate starts = today().minusDays(10);
        LocalDate ends = today().plusDays(10);
        String name = uniqueName("Batch");
        UUID batch = insertBatch(name, starts, ends);

        Map<String, Object> body = claimBody(today(), "1500.00", "TRAINING_STAY");
        body.put("link", linkTo("INDUCTION_BATCH", batch));
        UUID id = newDraft(claimant, body);
        uploadBill(claimant, id, "bill.jpg", JPG);
        assertThat(submit(claimant, id).status()).isEqualTo(200);

        Resp detail = get(MINE + "/" + id, claimant);
        assertThat(linkOf(detail)).containsEntry("kind", "INDUCTION_BATCH").containsEntry("ref", batch.toString());
        assertThat((String) linkOf(detail).get("label")).contains(name).contains(starts.toString()).contains(ends.toString());

        // the batch is cancelled afterwards: the claim still shows what it was linked to
        jdbc.update("update induction_batch set status = 'CANCELLED' where id = ?", batch);
        assertThat((String) linkOf(get(MINE + "/" + id, claimant)).get("label")).contains(name);
    }

    @Test
    void trainingStayRefusesALinkThatIsNotABatch() {
        String claimant = admin().token();
        Map<String, Object> body = claimBody(today(), "100.00", "TRAINING_STAY");
        body.put("link", linkTo("INDUCTION_BATCH", UUID.randomUUID()));
        assertThat(createClaim(claimant, body).status()).isEqualTo(400);
        Map<String, Object> wrongKind = claimBody(today(), "101.00", "TRAINING_STAY");
        wrongKind.put("link", linkTo("TASK", UUID.randomUUID()));
        assertThat(createClaim(claimant, wrongKind).status()).isEqualTo(400);
    }

    @Test
    void travelAndFuelMayLinkToTheClaimantsOwnVisitAndTheLinkSurvivesItsCancellation() {
        String claimant = admin().token();
        Object[] visit = marketingVisit(claimant, today());
        UUID activity = (UUID) visit[0];
        String prospectName = (String) visit[1];

        Resp options = get(MINE + "/link-options?category=TRAVEL_FUEL&date=" + today(), claimant);
        assertThat(options.status()).as(options.body()).isEqualTo(200);
        assertThat(options.body()).contains(activity.toString()).contains(prospectName);

        Map<String, Object> body = claimBody(today(), "420.00", "TRAVEL_FUEL");
        body.put("link", linkTo("SCHOOL_VISIT", activity));
        UUID id = newDraft(claimant, body);
        uploadBill(claimant, id, "bill.jpg", JPG);
        assertThat(submit(claimant, id).status()).isEqualTo(200);

        Resp detail = get(MINE + "/" + id, claimant);
        assertThat(linkOf(detail))
                .containsEntry("kind", "SCHOOL_VISIT")
                .containsEntry("kindLabel", "School visit")
                .containsEntry("date", today().toString());
        assertThat((String) linkOf(detail).get("label")).contains(prospectName);

        jdbc.update("update marketing_activity set status = 'CANCELLED', cancel_reason = 'Principal away' where id = ?", activity);
        Resp after = get(MINE + "/" + id, claimant);
        assertThat(linkOf(after)).isEqualTo(linkOf(detail));
        // the options no longer offer a cancelled visit, but the stored link is kept when a Rejected claim is corrected
        assertThat(get(MINE + "/link-options?category=TRAVEL_FUEL&date=" + today(), claimant).body())
                .doesNotContain(activity.toString());
    }

    @Test
    void travelAndFuelWithoutALinkShowsNotLinked() {
        String claimant = signInAs(com.hls.identity.user.Role.MANAGER).token();
        UUID id = newReadyClaim(claimant, "88.00");
        assertThat(get(MINE + "/" + id, claimant).map().get("link")).isNull();
        assertThat(submit(claimant, id).status()).isEqualTo(200);
    }

    @Test
    void onlyTheClaimantsOwnActivitiesAreOffered() {
        String owner = admin().token();
        String other = admin().token();
        UUID activity = (UUID) marketingVisit(owner, today())[0];

        assertThat(get(MINE + "/link-options?category=TRAVEL_FUEL&date=" + today(), other).body())
                .doesNotContain(activity.toString());
        Map<String, Object> body = claimBody(today(), "50.00", "TRAVEL_FUEL");
        body.put("link", linkTo("SCHOOL_VISIT", activity));
        Resp refused = createClaim(other, body);
        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.body()).contains("not one of yours");
    }

    @Test
    void aTaskOfTheClaimantCanBeLinked() {
        String adminToken = admin().token();
        ManagerCtx manager = newManager(adminToken);
        Map<String, Object> task = new HashMap<>();
        task.put("type", "SCHOOL_VISIT");
        task.put("title", "Visit " + uniqueName("School"));
        task.put("assigneeUserId", manager.signed().userId());
        task.put("dueOn", today().toString());
        Resp created = post("/api/v1/tasks", adminToken, task);
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        String taskId = (String) ((Map<?, ?>) created.map().get("task")).get("id");

        Map<String, Object> body = claimBody(today(), "75.00", "TRAVEL_FUEL");
        body.put("link", linkTo("TASK", UUID.fromString(taskId)));
        Resp claim = createClaim(manager.token(), body);
        assertThat(claim.status()).as(claim.body()).isEqualTo(201);
        assertThat(linkOf(claim)).containsEntry("kind", "TASK").containsEntry("kindLabel", "Task");
    }

    @Test
    void aCategoryThatTakesNoLinkRefusesOne() {
        String claimant = admin().token();
        UUID activity = (UUID) marketingVisit(claimant, today())[0];
        for (String category : List.of("OFFICE_SUPPLIES", "RENT_UTILITIES", "MARKETING", "OTHER")) {
            Map<String, Object> body = claimBody(today(), "20.00", category);
            body.put("link", linkTo("SCHOOL_VISIT", activity));
            Resp resp = createClaim(claimant, body);
            assertThat(resp.status()).as(category).isEqualTo(400);
            assertThat(resp.body()).contains("does not take a link");
        }
        // and the options for such a category are empty
        assertThat(get(MINE + "/link-options?category=OFFICE_SUPPLIES&date=" + today(), claimant).body()).isEqualTo("[]");
    }

    @Test
    void trainingStayOptionsListBatches() {
        String claimant = admin().token();
        String name = uniqueName("Option batch");
        UUID batch = insertBatch(name, today().minusDays(2), today().plusDays(20));
        Resp options = get(MINE + "/link-options?category=TRAINING_STAY&date=" + today(), claimant);
        assertThat(options.status()).isEqualTo(200);
        assertThat(options.body()).contains(batch.toString()).contains("INDUCTION_BATCH");
    }
}
