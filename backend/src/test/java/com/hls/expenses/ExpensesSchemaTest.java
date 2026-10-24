package com.hls.expenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.support.MasterDataTestBase;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/** Spec 015 T010: what the database itself refuses (constraints and triggers of V90). */
class ExpensesSchemaTest extends MasterDataTestBase {

    private UUID insertClaim(UUID claimant, UUID requestId, String amount, String status, String link) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into expense_claim (id, claimant_id, client_request_id, expense_date, amount, category, paid_by, status,"
                        + " paid_how, link_kind, link_ref, created_at, created_by, updated_at)"
                        + " values (?, ?, ?, ?, ?::numeric, 'TRAVEL_FUEL', 'CLAIMANT', ?, ?, ?, ?, now(), ?, now())",
                id,
                claimant,
                requestId,
                LocalDate.now(),
                amount,
                status,
                "PAID".equals(status) ? "OUTSIDE" : null,
                link,
                link == null ? null : UUID.randomUUID(),
                claimant);
        return id;
    }

    private UUID insertClaim(String amount, String status) {
        return insertClaim(UUID.randomUUID(), UUID.randomUUID(), amount, status, null);
    }

    @Test
    void aSecondClaimWithTheSameRequestIdIsRefused() {
        UUID claimant = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        insertClaim(claimant, request, "100.00", "DRAFT", null);
        assertThatThrownBy(() -> insertClaim(claimant, request, "100.00", "DRAFT", null)).isInstanceOf(DataAccessException.class);
        // another claimant may reuse the id
        insertClaim(UUID.randomUUID(), request, "100.00", "DRAFT", null);
    }

    @Test
    void zeroAndNegativeAmountsAreRefused() {
        assertThatThrownBy(() -> insertClaim("0", "DRAFT")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertClaim("-5.00", "DRAFT")).isInstanceOf(DataAccessException.class);
        insertClaim("0.01", "DRAFT");
    }

    @Test
    void aLinkKindWithoutARefIsRefused() {
        UUID id = insertClaim("10.00", "DRAFT");
        assertThatThrownBy(() -> jdbc.update("update expense_claim set link_kind = 'SCHOOL_VISIT' where id = ?", id))
                .isInstanceOf(DataAccessException.class);
        jdbc.update("update expense_claim set link_kind = 'SCHOOL_VISIT', link_ref = ? where id = ?", UUID.randomUUID(), id);
    }

    @Test
    void aPaidClaimCannotChangeItsMoneyFactsOrBeDeleted() {
        for (String status : new String[] {"PAID", "PAID_BY_COMPANY"}) {
            UUID id = insertClaim("250.00", status);
            for (String change : new String[] {
                "amount = 1.00",
                "expense_date = expense_date - 1",
                "category = 'OTHER'",
                "zone_id = gen_random_uuid()",
                "paid_by = 'COMPANY'",
                "claimant_id = gen_random_uuid()",
                "link_kind = 'X', link_ref = gen_random_uuid()",
                "status = 'APPROVED'"
            }) {
                assertThatThrownBy(() -> jdbc.update("update expense_claim set " + change + " where id = ?", id))
                        .as(status + " " + change)
                        .isInstanceOf(DataAccessException.class);
            }
            assertThatThrownBy(() -> jdbc.update("delete from expense_claim where id = ?", id)).isInstanceOf(DataAccessException.class);
            // a note can still be touched, so the claim row is not frozen entirely
            assertThat(jdbc.update("update expense_claim set note = 'late note' where id = ?", id)).isEqualTo(1);
        }
    }

    @Test
    void anApprovedClaimCanStillBeReversedInTheDatabase() {
        UUID id = insertClaim("75.00", "APPROVED");
        assertThat(jdbc.update("update expense_claim set status = 'SUBMITTED' where id = ?", id)).isEqualTo(1);
    }

    @Test
    void historyRowsCannotBeUpdatedOrDeleted() {
        UUID claim = insertClaim("30.00", "DRAFT");
        UUID event = UUID.randomUUID();
        jdbc.update(
                "insert into expense_claim_event (id, claim_id, at, actor_id, event) values (?, ?, now(), ?, 'CREATED')",
                event,
                claim,
                UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update("update expense_claim_event set reason = 'x' where id = ?", event))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from expense_claim_event where id = ?", event))
                .isInstanceOf(DataAccessException.class);

        UUID budget = UUID.randomUUID();
        jdbc.update(
                "insert into expense_area_budget (id, zone_id, month, amount, updated_at, updated_by) values (?, null, ?, 100, now(), ?)",
                budget,
                LocalDate.of(2031, 1, 1),
                UUID.randomUUID());
        UUID change = UUID.randomUUID();
        jdbc.update(
                "insert into expense_budget_change (id, budget_id, at, actor_id, old_amount, new_amount) values (?, ?, ?, ?, null, 100)",
                change,
                budget,
                Timestamp.from(Instant.now()),
                UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update("update expense_budget_change set new_amount = 1 where id = ?", change))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from expense_budget_change where id = ?", change))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void twoBudgetsForTheSameAreaAndMonthAreRefusedIncludingHeadOffice() {
        UUID zone = UUID.randomUUID();
        LocalDate month = LocalDate.of(2031, 2, 1);
        String sql = "insert into expense_area_budget (id, zone_id, month, amount, updated_at, updated_by) values (?, ?, ?, 10, now(), ?)";
        jdbc.update(sql, UUID.randomUUID(), zone, month, UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), zone, month, UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
        jdbc.update(sql, UUID.randomUUID(), null, month, UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), null, month, UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
        // a negative budget is refused too
        assertThatThrownBy(() -> jdbc.update(
                        "insert into expense_area_budget (id, zone_id, month, amount, updated_at, updated_by) values (?, null, ?, -1, now(), ?)",
                        UUID.randomUUID(),
                        LocalDate.of(2031, 3, 1),
                        UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void aRemovedBillNeedsAReason() {
        UUID claim = insertClaim("40.00", "DRAFT");
        UUID bill = UUID.randomUUID();
        jdbc.update(
                "insert into expense_bill (id, claim_id, file_id, name, added_by, added_at) values (?, ?, ?, 'a.jpg', ?, now())",
                bill,
                claim,
                UUID.randomUUID(),
                UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update("update expense_bill set removed_at = now() where id = ?", bill))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.update("update expense_bill set removed_at = now(), removal_reason = 'blurred' where id = ?", bill))
                .isEqualTo(1);
    }
}
