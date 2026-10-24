package com.hls.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.ExpensesTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 015 US1 (FR-001 to FR-006): what a claim, a Draft and a submission must satisfy, and what bills may be. */
class ClaimValidationTest extends ExpensesTestBase {

    private String manager;

    @BeforeEach
    void signIn() {
        manager = signInAs(Role.MANAGER).token();
    }

    @Test
    void amountMustBePositiveWithAtMostTwoDecimals() {
        assertThat(createClaim(manager, claimBody(today(), "0", "TRAVEL_FUEL")).status()).isEqualTo(400);
        assertThat(createClaim(manager, claimBody(today(), "-10.00", "TRAVEL_FUEL")).status()).isEqualTo(400);
        assertThat(createClaim(manager, claimBody(today(), "0.505", "TRAVEL_FUEL")).status()).isEqualTo(400);
        Resp ok = createClaim(manager, claimBody(today(), "0.50", "TRAVEL_FUEL"));
        assertThat(ok.status()).as(ok.body()).isEqualTo(201);
        assertThat(ok.map()).containsEntry("amount", "0.50").containsEntry("status", "DRAFT");
    }

    @Test
    void aDraftSavesWithoutTheSubmissionChecksButSubmitEnforcesThem() {
        // future date, category Other without a reason, no bill: all fine as a Draft
        UUID future = newDraft(manager, claimBody(today().plusDays(3), "100.00", "OTHER"));
        Resp refused = submit(manager, future);
        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.body()).contains("future");
        assertThat(statusOf(manager, future)).isEqualTo("DRAFT");
    }

    @Test
    void theDateWindowIsSixtyDaysBackAndNeverTheFuture() {
        UUID tooOld = newDraft(manager, claimBody(today().minusDays(61), "100.00", "TRAVEL_FUEL"));
        uploadBill(manager, tooOld, "bill.jpg", JPG);
        Resp old = submit(manager, tooOld);
        assertThat(old.status()).isEqualTo(400);
        assertThat(old.body()).contains("60 days");

        UUID exactly = newDraft(manager, claimBody(today().minusDays(60), "101.00", "TRAVEL_FUEL"));
        uploadBill(manager, exactly, "bill.jpg", JPG);
        assertThat(submit(manager, exactly).status()).isEqualTo(200);

        UUID tomorrow = newDraft(manager, claimBody(today().plusDays(1), "102.00", "TRAVEL_FUEL"));
        uploadBill(manager, tomorrow, "bill.jpg", JPG);
        assertThat(submit(manager, tomorrow).status()).isEqualTo(400);
        UUID todayClaim = newDraft(manager, claimBody(today(), "103.00", "TRAVEL_FUEL"));
        uploadBill(manager, todayClaim, "bill.jpg", JPG);
        assertThat(submit(manager, todayClaim).status()).isEqualTo(200);
    }

    @Test
    void categoryOtherNeedsAReason() {
        UUID id = newDraft(manager, claimBody(today(), "250.00", "OTHER"));
        uploadBill(manager, id, "bill.jpg", JPG);
        Resp refused = submit(manager, id);
        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.body()).contains("reason required");

        Map<String, Object> body = claimBody(today(), "251.00", "OTHER");
        body.put("reason", "Courier to the Gandhinagar School");
        UUID withReason = newDraft(manager, body);
        uploadBill(manager, withReason, "bill.jpg", JPG);
        assertThat(submit(manager, withReason).status()).isEqualTo(200);
    }

    @Test
    void trainingStayNeedsABatch() {
        UUID id = newDraft(manager, claimBody(today(), "900.00", "TRAINING_STAY"));
        uploadBill(manager, id, "bill.jpg", JPG);
        Resp refused = submit(manager, id);
        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.body()).contains("batch required");
    }

    @Test
    void aBillOrANoBillReasonIsRequiredAndTheReasonShowsAsNoBill() {
        UUID id = newDraft(manager, claimBody(today(), "60.00", "OFFICE_SUPPLIES"));
        Resp refused = submit(manager, id);
        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.body()).contains("no-bill reason required");

        Map<String, Object> body = claimBody(today(), "61.00", "OFFICE_SUPPLIES");
        body.put("noBillReason", "Auto-rickshaw, no receipt given");
        UUID noBill = newDraft(manager, body);
        Resp ok = submit(manager, noBill);
        assertThat(ok.status()).as(ok.body()).isEqualTo(200);
        assertThat(ok.map()).containsEntry("noBillReason", "Auto-rickshaw, no receipt given");
        assertThat(billsOf(ok)).isEmpty();
    }

    @Test
    void billsMustBeJpgPngOrPdfAtMostTenMegabytesAndFivePerClaim() {
        UUID id = newDraft(manager, travelBody("70.00"));
        assertThat(uploadBill(manager, id, "bill.docx", new byte[] {'P', 'K', 3, 4, 0}).status()).isEqualTo(400);
        assertThat(uploadBill(manager, id, "bill.txt", "plain".getBytes()).status()).isEqualTo(400);
        // a renamed program is refused by the content check
        assertThat(uploadBill(manager, id, "bill.jpg", "not an image".getBytes()).status()).isEqualTo(400);
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        big[0] = (byte) 0xFF;
        big[1] = (byte) 0xD8;
        big[2] = (byte) 0xFF;
        assertThat(uploadBill(manager, id, "big.jpg", big).status()).isEqualTo(400);
        assertThat(billsOf(get(MINE + "/" + id, manager))).isEmpty();

        for (int i = 1; i <= 5; i++) {
            Resp added = uploadBill(manager, id, "bill" + i + ".jpg", JPG);
            assertThat(added.status()).as(added.body()).isEqualTo(201);
        }
        Resp sixth = uploadBill(manager, id, "bill6.jpg", JPG);
        assertThat(sixth.status()).isEqualTo(400);
        assertThat(sixth.body()).contains("at most 5");
        Resp detail = get(MINE + "/" + id, manager);
        assertThat(billsOf(detail)).hasSize(5);
        assertThat(billsOf(detail).get(0)).containsKeys("addedByName", "addedAt");
        assertThat(detail.map()).containsEntry("status", "DRAFT");
    }

    @Test
    void anUnknownCategoryOrAreaIsRefused() {
        assertThat(createClaim(manager, claimBody(today(), "10.00", "HOLIDAY")).status()).isEqualTo(400);
        Map<String, Object> body = travelBody("10.00");
        body.put("zoneId", UUID.randomUUID().toString());
        assertThat(createClaim(manager, body).status()).isEqualTo(400);
    }

    @Test
    void anAreaIsAZoneOrHeadOffice() {
        String adminToken = admin().token();
        UUID zone = zone(adminToken);
        Map<String, Object> body = travelBody("10.00");
        body.put("zoneId", zone.toString());
        Resp inZone = createClaim(manager, body);
        assertThat(inZone.status()).as(inZone.body()).isEqualTo(201);
        assertThat(inZone.map().get("zoneId")).isEqualTo(zone.toString());
        Resp headOffice = createClaim(manager, travelBody("11.00"));
        assertThat(headOffice.map()).containsEntry("areaName", "Head office");
        Resp areas = get(MINE + "/areas", manager);
        assertThat(areas.status()).isEqualTo(200);
        assertThat(areas.body()).contains("Head office").contains(zone.toString());
    }
}
