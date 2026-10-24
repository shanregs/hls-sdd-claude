package com.hls.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.ExpensesTestBase;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 015 US6 (FR-025): the monthly CSV has one row per claim, neutralises formulas, and is audited. */
class ClaimCsvTest extends ExpensesTestBase {

    @Test
    void oneRowPerClaimByExpenseDateWithFormulasNeutralisedAndTheExportAudited() {
        String admin = admin().token();
        String director = director().token();
        String claimant = signInAs(Role.MANAGER).token();
        LocalDate date = LocalDate.of(2025, 3, 14);
        // the 60-day window only applies at submission, so the row is written directly
        UUID inMonth = newSubmittedClaim(claimant, "321.50");
        jdbc.update("update expense_claim set expense_date = ?, note = ? where id = ?", date, "=HYPERLINK(\"http://x\")", inMonth);
        UUID outside = newSubmittedClaim(claimant, "100.00");
        jdbc.update("update expense_claim set expense_date = ? where id = ?", date.plusMonths(1), outside);
        UUID draft = newDraft(claimant, claimBody(today(), "5.00", "TRAVEL_FUEL"));
        jdbc.update("update expense_claim set expense_date = ? where id = ?", date, draft);
        assertThat(approve(director, inMonth).status()).isEqualTo(200);

        Resp csv = get(REVIEW + "/export?month=" + YearMonth.from(date), admin);
        assertThat(csv.status()).isEqualTo(200);
        String body = csv.body();
        assertThat(body).startsWith("﻿").contains("Claim id,Claimant,Expense date,Category");
        assertThat(body).contains(inMonth.toString()).doesNotContain(outside.toString()).doesNotContain(draft.toString());
        assertThat(body).contains("14/03/2025").contains("321.50").contains("Approved");
        assertThat(body).contains("'=HYPERLINK").doesNotContain(",=HYPERLINK");
        assertChangeRecorded(admin, "EXPENSE_EXPORT", UUID.nameUUIDFromBytes("2025-03".getBytes(java.nio.charset.StandardCharsets.UTF_8)), "exported");
    }

    @Test
    void onlyThoseWithExportMayDownload() {
        String claimant = signInAs(Role.MANAGER).token();
        assertThat(get(REVIEW + "/export?month=2025-03", claimant).status()).isEqualTo(403);
        assertThat(get(REVIEW + "/export?month=2025-03", signInAs(Role.TEACHER).token()).status()).isEqualTo(403);
        assertThat(get(REVIEW + "/export?month=bad", admin().token()).status()).isEqualTo(400);
        assertThat(get(REVIEW + "/export?month=2025-03", director().token()).status()).isEqualTo(200);
    }
}
