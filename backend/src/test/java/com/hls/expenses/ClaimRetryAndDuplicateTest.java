package com.hls.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.ExpensesTestBase;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 015 US1 (FR-008, FR-009, SC-010): a retry never makes two claims, and a likely duplicate needs confirmation. */
class ClaimRetryAndDuplicateTest extends ExpensesTestBase {

    private String manager;
    private String admin;

    @BeforeEach
    void signIn() {
        manager = signInAs(Role.MANAGER).token();
        admin = admin().token();
    }

    private int claimCount(String token) {
        return ((Number) get(MINE, token).map().get("totalElements")).intValue();
    }

    @Test
    void theSameRequestIdTwiceReturnsOneClaim() {
        Map<String, Object> body = travelBody("120.00");
        Resp first = createClaim(manager, body);
        Resp second = createClaim(manager, body);
        assertThat(first.status()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(claimCount(manager)).isEqualTo(1);
        assertThat(eventCount(first.id(), "CREATED")).isEqualTo(1);
    }

    @Test
    void aDifferentBodyWithTheSameRequestIdIsRefused() {
        Map<String, Object> body = travelBody("120.00");
        assertThat(createClaim(manager, body).status()).isEqualTo(201);
        Map<String, Object> other = new HashMap<>(body);
        other.put("amount", "121.00");
        Resp refused = createClaim(manager, other);
        assertThat(refused.status()).isEqualTo(409);
        assertThat(claimCount(manager)).isEqualTo(1);
    }

    @Test
    void anotherClaimantMayUseTheSameRequestId() {
        Map<String, Object> body = travelBody("120.00");
        String other = signInAs(Role.TEACHER).token();
        assertThat(createClaim(manager, body).status()).isEqualTo(201);
        assertThat(createClaim(other, body).status()).isEqualTo(201);
    }

    @Test
    void submittingAtOnceIsAlsoIdempotent() {
        Map<String, Object> body = travelBody("130.00");
        body.put("noBillReason", "Toll booth, no receipt");
        body.put("submit", true);
        Resp first = createClaim(manager, body);
        Resp second = createClaim(manager, body);
        assertThat(first.status()).as(first.body()).isEqualTo(201);
        assertThat(first.map()).containsEntry("status", "SUBMITTED");
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(eventCount(first.id(), "SUBMITTED")).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aLikelyDuplicateNeedsConfirmationAndTheConfirmationIsRecorded() {
        LocalDate date = today().minusDays(2);
        UUID first = newDraft(manager, claimBody(date, "300.00", "TRAVEL_FUEL"));
        uploadBill(manager, first, "bill.jpg", JPG);
        assertThat(submit(manager, first).status()).isEqualTo(200);

        UUID second = newDraft(manager, claimBody(date, "300.00", "TRAVEL_FUEL"));
        uploadBill(manager, second, "bill.jpg", JPG);
        Resp warned = submit(manager, second);
        assertThat(warned.status()).isEqualTo(409);
        assertThat((List<String>) warned.map().get("matches")).containsExactly(first.toString());
        assertThat(statusOf(manager, second)).isEqualTo("DRAFT");

        Resp confirmed = post(MINE + "/" + second + "/submit", manager,
                Map.of("version", versionOf(manager, second), "confirmDuplicate", true));
        assertThat(confirmed.status()).as(confirmed.body()).isEqualTo(200);
        assertThat(historyOf(confirmed)).extracting(h -> h.get("event")).contains("DUPLICATE_CONFIRMED");
        assertThat(eventCount(second, "DUPLICATE_CONFIRMED")).isEqualTo(1);
    }

    @Test
    void aRejectedClaimDoesNotCountAsADuplicate() {
        LocalDate date = today().minusDays(3);
        UUID first = newDraft(manager, claimBody(date, "310.00", "TRAVEL_FUEL"));
        uploadBill(manager, first, "bill.jpg", JPG);
        assertThat(submit(manager, first).status()).isEqualTo(200);
        assertThat(reject(admin, first, "Wrong bill").status()).isEqualTo(200);

        UUID again = newDraft(manager, claimBody(date, "310.00", "TRAVEL_FUEL"));
        uploadBill(manager, again, "bill.jpg", JPG);
        assertThat(submit(manager, again).status()).isEqualTo(200);
    }

    @Test
    void sameDateAmountButAnotherCategoryOrClaimantIsNotADuplicate() {
        LocalDate date = today().minusDays(4);
        UUID first = newDraft(manager, claimBody(date, "320.00", "TRAVEL_FUEL"));
        uploadBill(manager, first, "bill.jpg", JPG);
        assertThat(submit(manager, first).status()).isEqualTo(200);

        UUID otherCategory = newDraft(manager, claimBody(date, "320.00", "OFFICE_SUPPLIES"));
        uploadBill(manager, otherCategory, "bill.jpg", JPG);
        assertThat(submit(manager, otherCategory).status()).isEqualTo(200);

        String colleague = signInAs(Role.MANAGER).token();
        UUID theirs = newDraft(colleague, claimBody(date, "320.00", "TRAVEL_FUEL"));
        uploadBill(colleague, theirs, "bill.jpg", JPG);
        assertThat(submit(colleague, theirs).status()).isEqualTo(200);
    }
}
