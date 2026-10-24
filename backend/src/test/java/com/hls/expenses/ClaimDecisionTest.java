package com.hls.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.ExpensesTestBase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 015 US3 (FR-012 to FR-016): approve and reject once, with a reason for a rejection, and a complete history. */
class ClaimDecisionTest extends ExpensesTestBase {

    private Signed claimant;
    private Signed admin;
    private Signed director;

    @BeforeEach
    void signIn() {
        claimant = signInAs(Role.MANAGER);
        admin = admin();
        director = director();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> decisionOf(Resp detail) {
        return (Map<String, Object>) detail.map().get("decision");
    }

    @Test
    void approvalRecordsTheApproverAndTimeAndNotifiesTheClaimant() {
        UUID id = newSubmittedClaim(claimant.token(), "640.00");
        assertThat(allowedActions(get(REVIEW + "/" + id, director.token())))
                .containsExactlyInAnyOrder("APPROVE", "REJECT", "REMOVE_BILL");

        Resp approved = approve(director.token(), id);
        assertThat(approved.status()).as(approved.body()).isEqualTo(200);
        assertThat(approved.map()).containsEntry("status", "APPROVED");
        assertThat(decisionOf(approved)).containsEntry("by", director.userId().toString()).containsKey("at");
        assertThat(notificationCount(claimant.userId(), "EXPENSE_DECIDED")).isEqualTo(1);
        assertThat(historyOf(approved)).extracting(h -> h.get("event")).contains("APPROVED", "PAYROLL_OFFER_SKIPPED");
        assertChangeRecorded(admin.token(), "EXPENSE_CLAIM", id, "status");
    }

    @Test
    void aRejectionNeedsAReasonAndKeepsEveryEarlierDecisionInTheHistory() {
        UUID id = newSubmittedClaim(claimant.token(), "90.00");
        assertThat(reject(admin.token(), id, "").status()).isEqualTo(400);
        assertThat(reject(admin.token(), id, "   ").status()).isEqualTo(400);
        assertThat(statusOf(claimant.token(), id)).isEqualTo("SUBMITTED");

        Resp rejected = reject(admin.token(), id, "Bill is not legible");
        assertThat(rejected.status()).as(rejected.body()).isEqualTo(200);
        assertThat(decisionOf(rejected)).containsEntry("reason", "Bill is not legible");
        assertThat(notificationCount(claimant.userId(), "EXPENSE_DECIDED")).isEqualTo(1);

        assertThat(submit(claimant.token(), id).status()).isEqualTo(200);
        assertThat(reject(director.token(), id, "Still not legible").status()).isEqualTo(200);
        assertThat(submit(claimant.token(), id).status()).isEqualTo(200);
        Resp approved = approve(admin.token(), id);
        assertThat(approved.status()).isEqualTo(200);
        List<String> events = historyOf(approved).stream().map(h -> (String) h.get("event")).toList();
        assertThat(events).containsSubsequence("SUBMITTED", "REJECTED", "RESUBMITTED", "REJECTED", "RESUBMITTED", "APPROVED");
        assertThat(historyOf(approved).stream().map(h -> (String) h.get("reason")).toList())
                .contains("Bill is not legible", "Still not legible");
    }

    @Test
    void anApprovedClaimIsFrozenForTheClaimant() {
        UUID id = newSubmittedClaim(claimant.token(), "120.00");
        assertThat(approve(admin.token(), id).status()).isEqualTo(200);
        Resp detail = get(MINE + "/" + id, claimant.token());
        Map<String, Object> body = new HashMap<>(detail.map());
        for (String[] change : new String[][] {{"amount", "1.00"}, {"category", "OTHER"}, {"expenseDate", today().minusDays(2).toString()}}) {
            Map<String, Object> edit = new HashMap<>(body);
            edit.remove("link");
            edit.put(change[0], change[1]);
            assertThat(put(MINE + "/" + id, claimant.token(), edit).status()).as(change[0]).isEqualTo(409);
        }
        assertThat(uploadBill(claimant.token(), id, "swap.jpg", JPG).status()).isEqualTo(409);
        assertThat(delete(MINE + "/" + id, claimant.token()).status()).isEqualTo(409);
    }

    @Test
    void aSecondDecisionIsRefusedNamingWhoDecidedAndWhen() {
        UUID id = newSubmittedClaim(claimant.token(), "75.00");
        long version = reviewVersion(admin.token(), id);
        assertThat(post(REVIEW + "/" + id + "/approve", admin.token(), Map.of("version", version)).status()).isEqualTo(200);

        Resp second = post(REVIEW + "/" + id + "/reject", director.token(), Map.of("version", version, "reason", "Too late"));
        assertThat(second.status()).isEqualTo(409);
        assertThat(second.body()).contains("already decided").contains("approved by").contains("Tester");
    }

    @Test
    void ofTwoSimultaneousDecisionsExactlyOneWins() throws Exception {
        UUID id = newSubmittedClaim(claimant.token(), "310.00");
        long version = reviewVersion(admin.token(), id);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Resp>> calls = new ArrayList<>();
            calls.add(() -> post(REVIEW + "/" + id + "/approve", admin.token(), Map.of("version", version)));
            calls.add(() -> post(REVIEW + "/" + id + "/approve", director.token(), Map.of("version", version)));
            List<Integer> statuses = new ArrayList<>();
            String loserBody = null;
            for (Future<Resp> f : pool.invokeAll(calls)) {
                Resp r = f.get();
                statuses.add(r.status());
                if (r.status() != 200) {
                    loserBody = r.body();
                }
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
            assertThat(loserBody).contains("already decided");
        } finally {
            pool.shutdown();
        }
        assertThat(eventCount(id, "APPROVED")).isEqualTo(1);
        assertThat(notificationCount(claimant.userId(), "EXPENSE_DECIDED")).isEqualTo(1);
    }

    @Test
    void theReviewQueueIsOldestFirstWithAgeAndFilters() {
        String token = claimant.token();
        UUID first = newSubmittedClaim(token, "11.00");
        UUID second = newSubmittedClaim(token, "12.00");
        jdbc.update("update expense_claim set submitted_at = now() - interval '5 days' where id = ?", first);

        Resp queue = get(REVIEW + "?claimant=" + claimant.userId(), admin.token());
        assertThat(queue.status()).as(queue.body()).isEqualTo(200);
        List<Map<String, Object>> rows = content(queue);
        assertThat(rows).extracting(r -> r.get("id")).containsExactly(first.toString(), second.toString());
        assertThat(((Number) rows.get(0).get("ageDays")).intValue()).isEqualTo(5);
        assertThat(rows.get(0)).containsKeys("claimantName", "categoryLabel", "areaName", "amount", "billCount", "remainingBudget");

        assertThat(content(get(REVIEW + "?claimant=" + claimant.userId() + "&category=OFFICE_SUPPLIES", admin.token()))).isEmpty();
        assertThat(content(get(REVIEW + "?claimant=" + claimant.userId() + "&zone=head-office", admin.token()))).hasSize(2);
        assertThat(approve(admin.token(), first).status()).isEqualTo(200);
        assertThat(content(get(REVIEW + "?claimant=" + claimant.userId() + "&status=APPROVED", admin.token())))
                .extracting(r -> r.get("id"))
                .containsExactly(first.toString());
        assertThat(content(get(REVIEW + "?claimant=" + claimant.userId() + "&status=ALL", admin.token()))).hasSize(2);
    }

    @Test
    void someoneElsesDraftIsNeverShownToReviewers() {
        UUID draft = newDraft(claimant.token(), travelBody("5.00"));
        assertThat(get(REVIEW + "/" + draft, admin.token()).status()).isEqualTo(404);
        assertThat(content(get(REVIEW + "?claimant=" + claimant.userId() + "&status=ALL", admin.token()))).isEmpty();
        assertThat(content(get(REVIEW + "?status=DRAFT", admin.token()))).isEmpty();
        assertThat(post(REVIEW + "/" + draft + "/approve", admin.token(), Map.of("version", 0)).status()).isEqualTo(404);
    }

    @Test
    void aBillCanBeRemovedByAReviewerWithAReasonAndTheClaimStaysAsItWas() {
        UUID id = newSubmittedClaim(claimant.token(), "33.00");
        Resp detail = get(REVIEW + "/" + id, admin.token());
        String billId = (String) billsOf(detail).get(0).get("id");

        assertThat(delete(REVIEW + "/" + id + "/bills/" + billId, admin.token()).status()).isEqualTo(400);
        Resp removed = send(org.springframework.http.HttpMethod.DELETE, REVIEW + "/" + id + "/bills/" + billId, admin.token(), Map.of("reason", "Shows another person"));
        assertThat(removed.status()).as(removed.body()).isEqualTo(200);
        assertThat(removed.map()).containsEntry("status", "SUBMITTED");
        Map<String, Object> bill = billsOf(removed).get(0);
        assertThat(bill).containsEntry("removed", true).containsEntry("removalReason", "Shows another person");
        assertThat(get(REVIEW + "/" + id + "/bills/" + billId, admin.token()).status()).isEqualTo(404);
        assertThat(historyOf(removed)).extracting(h -> h.get("event")).contains("BILL_REMOVED");
        // the claimant cannot remove it
        assertThat(send(org.springframework.http.HttpMethod.DELETE, REVIEW + "/" + id + "/bills/" + billId, claimant.token(), Map.of("reason", "x")).status())
                .isEqualTo(403);
    }
}
