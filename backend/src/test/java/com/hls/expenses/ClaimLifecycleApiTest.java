package com.hls.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.ExpensesTestBase;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 015 US1 (FR-004, FR-007): Draft, edit, delete, submit, withdraw, and correct and resubmit as the same claim. */
class ClaimLifecycleApiTest extends ExpensesTestBase {

    private String manager;
    private String admin;

    @BeforeEach
    void signIn() {
        manager = signInAs(Role.MANAGER).token();
        admin = admin().token();
    }

    private Map<String, Object> edited(Resp detail, String amount) {
        Map<String, Object> body = new HashMap<>(detail.map());
        body.put("amount", amount);
        body.remove("link");
        return body;
    }

    @Test
    void aDraftCanBeEditedSubmittedAndWithdrawnBackToDraft() {
        UUID id = newDraft(manager, travelBody("850.00"));
        Resp detail = get(MINE + "/" + id, manager);
        assertThat(allowedActions(detail)).containsExactlyInAnyOrder("EDIT", "SUBMIT", "ADD_BILL", "DELETE");

        Resp updated = put(MINE + "/" + id, manager, edited(detail, "900.00"));
        assertThat(updated.status()).as(updated.body()).isEqualTo(200);
        assertThat(updated.map()).containsEntry("amount", "900.00");

        assertThat(uploadBill(manager, id, "bill.jpg", JPG).status()).isEqualTo(201);
        Resp submitted = submit(manager, id);
        assertThat(submitted.status()).as(submitted.body()).isEqualTo(200);
        assertThat(submitted.map()).containsEntry("status", "SUBMITTED");
        assertThat(allowedActions(submitted)).containsExactly("WITHDRAW");

        Resp withdrawn = post(MINE + "/" + id + "/withdraw", manager, Map.of("version", versionOf(manager, id)));
        assertThat(withdrawn.status()).as(withdrawn.body()).isEqualTo(200);
        assertThat(withdrawn.map()).containsEntry("status", "DRAFT");
        assertThat(historyOf(withdrawn)).extracting(h -> h.get("event")).containsExactly("CREATED", "EDITED", "BILL_ADDED", "SUBMITTED", "WITHDRAWN");
    }

    @Test
    void onlyADraftCanBeDeletedAndItsHistoryIsKept() {
        UUID draft = newDraft(manager, travelBody("40.00"));
        Resp deleted = delete(MINE + "/" + draft, manager);
        assertThat(deleted.status()).as(deleted.body()).isEqualTo(204);
        assertThat(get(MINE + "/" + draft, manager).status()).isEqualTo(404);
        assertThat(eventCount(draft, "CREATED")).isEqualTo(1);

        UUID submitted = newSubmittedClaim(manager, "41.00");
        assertThat(delete(MINE + "/" + submitted, manager).status()).isEqualTo(409);
    }

    @Test
    void aRejectedClaimIsCorrectedAndResubmittedAsTheSameClaim() {
        UUID id = newSubmittedClaim(manager, "500.00");
        Resp rejected = reject(admin, id, "Receipt is for another month");
        assertThat(rejected.status()).as(rejected.body()).isEqualTo(200);
        assertThat(statusOf(manager, id)).isEqualTo("REJECTED");

        Resp detail = get(MINE + "/" + id, manager);
        assertThat(allowedActions(detail)).containsExactlyInAnyOrder("EDIT", "SUBMIT", "ADD_BILL");
        assertThat(((Map<?, ?>) detail.map().get("decision")).get("reason")).isEqualTo("Receipt is for another month");

        Resp corrected = put(MINE + "/" + id, manager, edited(detail, "450.00"));
        assertThat(corrected.status()).as(corrected.body()).isEqualTo(200);
        assertThat(corrected.map()).containsEntry("status", "REJECTED");
        Resp resubmitted = submit(manager, id);
        assertThat(resubmitted.status()).as(resubmitted.body()).isEqualTo(200);
        assertThat(resubmitted.map()).containsEntry("status", "SUBMITTED").containsEntry("id", id.toString());
        assertThat(historyOf(resubmitted))
                .extracting(h -> h.get("event"))
                .containsExactly("CREATED", "BILL_ADDED", "SUBMITTED", "REJECTED", "EDITED", "RESUBMITTED");
    }

    @Test
    void submittedApprovedAndPaidClaimsRefuseEdits() {
        UUID id = newSubmittedClaim(manager, "75.00");
        Resp detail = get(MINE + "/" + id, manager);
        assertThat(put(MINE + "/" + id, manager, edited(detail, "76.00")).status()).isEqualTo(409);
        assertThat(uploadBill(manager, id, "late.jpg", JPG).status()).isEqualTo(409);

        assertThat(approve(admin, id).status()).isEqualTo(200);
        Resp approved = get(MINE + "/" + id, manager);
        assertThat(put(MINE + "/" + id, manager, edited(approved, "77.00")).status()).isEqualTo(409);
        assertThat(post(MINE + "/" + id + "/withdraw", manager, Map.of()).status()).isEqualTo(409);

        // a company-paid claim goes straight to Paid by company at approval
        UUID byCompany = newDraft(manager, withPaidBy(travelBody("79.00"), "COMPANY"));
        uploadBill(manager, byCompany, "bill.jpg", JPG);
        assertThat(submit(manager, byCompany).status()).isEqualTo(200);
        assertThat(approve(admin, byCompany).status()).isEqualTo(200);
        assertThat(statusOf(manager, byCompany)).isEqualTo("PAID_BY_COMPANY");
        Resp settled = get(MINE + "/" + byCompany, manager);
        assertThat(put(MINE + "/" + byCompany, manager, edited(settled, "1.00")).status()).isEqualTo(409);
        assertThat(allowedActions(settled)).isEmpty();
    }

    @Test
    void aStaleVersionAnswers409() {
        UUID id = newDraft(manager, travelBody("30.00"));
        Resp detail = get(MINE + "/" + id, manager);
        assertThat(put(MINE + "/" + id, manager, edited(detail, "31.00")).status()).isEqualTo(200);
        // the same (now old) version again
        Resp stale = put(MINE + "/" + id, manager, edited(detail, "32.00"));
        assertThat(stale.status()).isEqualTo(409);
        assertThat(stale.body()).contains("changed by someone else");
        assertThat(post(MINE + "/" + id + "/submit", manager, Map.of("version", 0)).status()).isEqualTo(409);
    }

    @Test
    void aClaimantNeverDecidesAndSubmittingNotifiesTheReviewers() {
        Signed reviewer = admin();
        UUID id = newSubmittedClaim(manager, "55.00");
        // the notice is created in the submitting transaction
        assertThat(notificationCount(reviewer.userId(), "EXPENSE_SUBMITTED")).isEqualTo(1);
        assertThat(post(REVIEW + "/" + id + "/approve", manager, Map.of("version", 0)).status()).isEqualTo(403);
    }

    protected static Map<String, Object> withPaidBy(Map<String, Object> body, String paidBy) {
        body.put("paidBy", paidBy);
        return body;
    }
}
