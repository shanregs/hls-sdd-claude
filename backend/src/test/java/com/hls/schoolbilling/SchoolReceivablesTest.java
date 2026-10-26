package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.schoolbilling.api.SchoolReceivables;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 022 US7: the public receivables interface equals the screens, cancelled dues count for nothing, audit rows exist. */
class SchoolReceivablesTest extends ReceivablesTestBase {

    @Autowired
    private SchoolReceivables receivables;

    @Test
    void billedAmountsAndBalancesEqualTheScreens() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000", "16000");
        UUID first = mapTeacher(admin, f, 0, thisMonth().atDay(1));
        mapTeacher(admin, f, 1, thisMonth().atDay(1));
        UUID due = raiseOne(admin, thisMonth(), f.schoolId());
        assertThat(collect(admin, collectionBody(f.schoolId(), "10000")).status()).isEqualTo(201);

        var billed = receivables.billedPerTeacher(Set.of(first), thisMonth(), thisMonth());
        var months = receivables.schoolMonths(Set.of(f.schoolId()), thisMonth(), thisMonth());
        var balance = receivables.balances(Set.of(f.schoolId()), LocalDate.now().plusDays(40)).get(f.schoolId());

        assertThat(billed.get(first)).hasSize(1);
        assertThat(billed.get(first).get(0).amount()).isEqualByComparingTo("15000.00");
        assertThat(months.get(f.schoolId())).hasSize(1);
        assertThat(months.get(f.schoolId()).get(0).owed()).isEqualByComparingTo("31000.00");
        assertThat(months.get(f.schoolId()).get(0).collected()).isEqualByComparingTo("10000.00");
        assertThat(months.get(f.schoolId()).get(0).balance()).isEqualByComparingTo("21000.00");
        assertThat(balance.outstanding()).isEqualByComparingTo("21000.00");
        assertThat(balance.overdue()).isEqualByComparingTo("21000.00");
        assertThat(dec(dueOf(admin, due).get("balance"))).isEqualByComparingTo("21000.00");
    }

    @Test
    void aCancelledDueAndAMonthWithoutADueContributeNothing() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000");
        UUID teacher = mapTeacher(admin, f, 0, thisMonth().atDay(1));
        UUID due = raiseOne(admin, thisMonth(), f.schoolId());
        assertThat(post(BILLING + "/dues/" + due + "/cancel", admin, Map.of("reason", "Wrong month")).status()).isEqualTo(200);

        assertThat(receivables.billedPerTeacher(Set.of(teacher), thisMonth(), thisMonth())).doesNotContainKey(teacher);
        assertThat(receivables.schoolMonths(Set.of(f.schoolId()), thisMonth().minusMonths(3), thisMonth())).doesNotContainKey(f.schoolId());
        var balance = receivables.balances(List.of(f.schoolId()), LocalDate.now()).get(f.schoolId());
        assertThat(balance.owed()).isEqualByComparingTo("0.00");
        assertThat(receivables.balances(Set.of(), LocalDate.now())).isEmpty();
    }

    @Test
    void everyChangeIsWrittenToChangeHistory() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000");
        mapTeacher(admin, f, 0, thisMonth().atDay(1));
        UUID due = raiseOne(admin, thisMonth(), f.schoolId());
        UUID adjustment = post(BILLING + "/dues/" + due + "/adjustments", admin, Map.of("amount", "500", "reason", "Agreed deduction")).id();
        UUID collection = collect(admin, collectionBody(f.schoolId(), "1000")).id();
        post(BILLING + "/collections/" + collection + "/reverse", admin, Map.of("reason", "Bounced"));
        post(BILLING + "/dues/" + due + "/cancel", admin, Map.of("reason", "Raised in error"));
        put(BILLING + "/settings", admin, Map.of("paymentDueDays", 20, "escalationDays", 9));
        assertThat(get(BILLING + "/settings", admin).map()).containsEntry("paymentDueDays", 20).containsEntry("escalationDays", 9);
        put(BILLING + "/settings", admin, Map.of("paymentDueDays", 15, "escalationDays", 7));

        assertChangeRecorded(admin, "MONTHLY_DUE", due, "created");
        assertChangeRecorded(admin, "MONTHLY_DUE", due, "cancelReason");
        assertChangeRecorded(admin, "DUE_ADJUSTMENT", adjustment, "created");
        assertChangeRecorded(admin, "SCHOOL_COLLECTION", collection, "created");
        assertChangeRecorded(admin, "SCHOOL_COLLECTION", collection, "allocated");
        assertChangeRecorded(admin, "BILLING_SETTING", "billing.payment_due_days", "value");
    }

    @Test
    void settingsAreValidated() {
        String admin = signInAs(Role.ADMIN).token();
        assertThat(put(BILLING + "/settings", admin, Map.of("paymentDueDays", 0)).status()).isEqualTo(400);
        assertThat(put(BILLING + "/settings", admin, Map.of("escalationDays", 1)).status()).isEqualTo(400);
        assertThat(put(BILLING + "/settings", admin, Map.of("paymentDueDays", 121)).status()).isEqualTo(400);
    }

    @Test
    void theLedgerTablesRefuseEditsAndDeletes() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000");
        mapTeacher(admin, f, 0, thisMonth().atDay(1));
        UUID due = raiseOne(admin, thisMonth(), f.schoolId());
        UUID collection = collect(admin, collectionBody(f.schoolId(), "1000")).id();
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> jdbc.update("update monthly_due set total = 1 where id = ?", due));
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> jdbc.update("delete from monthly_due where id = ?", due));
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> jdbc.update("update due_line set amount = 1 where due_id = ?", due));
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> jdbc.update("update school_collection set amount = 1 where id = ?", collection));
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> jdbc.update("delete from collection_allocation where collection_id = ?", collection));
    }
}
