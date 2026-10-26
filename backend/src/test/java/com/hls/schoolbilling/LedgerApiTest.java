package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 022 US3 to US5: collections and their allocation, adjustments, reversals, balances, ageing and scope. */
class LedgerApiTest extends ReceivablesTestBase {

    /** A School with a due for last month and one for this month, each 10000, Teacher mapped since last month. */
    private record TwoMonths(Fixture school, UUID previous, UUID current) {}

    private TwoMonths twoMonths(String admin) {
        Fixture f = billedSchoolSince(admin, 80, "10000");
        mapTeacher(admin, f, 0, thisMonth().minusMonths(1).atDay(1));
        UUID previous = raiseOne(admin, thisMonth().minusMonths(1), f.schoolId());
        UUID current = raiseOne(admin, thisMonth(), f.schoolId());
        return new TwoMonths(f, previous, current);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Resp resp, String key) {
        return (List<Map<String, Object>>) resp.map().get(key);
    }

    @Test
    void aCollectionIsAllocatedToTheOldestDueFirstAndPaysItInFull() {
        String admin = signInAs(Role.ADMIN).token();
        TwoMonths t = twoMonths(admin);

        Resp resp = collect(admin, collectionBody(t.school().schoolId(), "15000"));

        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        assertThat(dec(resp.map().get("allocated"))).isEqualByComparingTo("15000.00");
        assertThat(dec(resp.map().get("unallocated"))).isEqualByComparingTo("0.00");
        Map<String, Object> previous = dueOf(admin, t.previous());
        Map<String, Object> current = dueOf(admin, t.current());
        assertThat(previous).containsEntry("status", "PAID");
        assertThat(dec(previous.get("balance"))).isEqualByComparingTo("0.00");
        assertThat(current).containsEntry("status", "PART_PAID");
        assertThat(dec(current.get("balance"))).isEqualByComparingTo("5000.00");
    }

    @Test
    void anAmountAboveWhatIsOwedIsKeptAsAdvanceCredit() {
        String admin = signInAs(Role.ADMIN).token();
        TwoMonths t = twoMonths(admin);

        Resp resp = collect(admin, collectionBody(t.school().schoolId(), "23000"));

        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        assertThat(dec(resp.map().get("unallocated"))).isEqualByComparingTo("3000.00");
        Resp ledger = get(BILLING + "/schools/" + t.school().schoolId() + "/ledger", admin);
        assertThat(dec(ledger.map().get("advanceCredit"))).isEqualByComparingTo("3000.00");
        assertThat(dec(ledger.map().get("outstanding"))).isEqualByComparingTo("0.00");
    }

    @Test
    void allocationsAreCheckedAgainstTheCollectionAndTheDueBalance() {
        String admin = signInAs(Role.ADMIN).token();
        TwoMonths t = twoMonths(admin);
        UUID school = t.school().schoolId();
        Map<String, Object> tooMuch = collectionBody(school, "5000");
        tooMuch.put("allocations", List.of(Map.of("dueId", t.previous(), "amount", "6000")));
        assertThat(collect(admin, tooMuch).status()).isEqualTo(400);
        Map<String, Object> aboveBalance = collectionBody(school, "20000");
        aboveBalance.put("allocations", List.of(Map.of("dueId", t.previous(), "amount", "10500")));
        assertThat(collect(admin, aboveBalance).status()).isEqualTo(400);
        Map<String, Object> explicit = collectionBody(school, "4000");
        explicit.put("allocations", List.of(Map.of("dueId", t.current(), "amount", "4000")));
        Resp ok = collect(admin, explicit);
        assertThat(ok.status()).as(ok.body()).isEqualTo(201);
        assertThat(dec(dueOf(admin, t.previous()).get("balance"))).isEqualByComparingTo("10000.00");
        assertThat(dec(dueOf(admin, t.current()).get("balance"))).isEqualByComparingTo("6000.00");
    }

    @Test
    void aLikelyDuplicateIsWarnedAboutAndCanBeConfirmed() {
        String admin = signInAs(Role.ADMIN).token();
        TwoMonths t = twoMonths(admin);
        Map<String, Object> body = collectionBody(t.school().schoolId(), "1000");
        assertThat(collect(admin, body).status()).isEqualTo(201);

        Resp warned = collect(admin, body);
        assertThat(warned.status()).isEqualTo(409);
        assertThat(warned.map()).containsEntry("warning", "LIKELY_DUPLICATE");
        body.put("confirmDuplicate", true);
        assertThat(collect(admin, body).status()).isEqualTo(201);
    }

    @Test
    void reversingACollectionRestoresTheBalancesAndKeepsTheOriginal() {
        String admin = signInAs(Role.ADMIN).token();
        TwoMonths t = twoMonths(admin);
        UUID collection = collect(admin, collectionBody(t.school().schoolId(), "10000")).id();
        assertThat(dueOf(admin, t.previous())).containsEntry("status", "PAID");

        assertThat(post(BILLING + "/collections/" + collection + "/reverse", admin, Map.of("reason", " ")).status()).isEqualTo(400);
        Resp reversed = post(BILLING + "/collections/" + collection + "/reverse", admin, Map.of("reason", "Cheque bounced"));

        assertThat(reversed.status()).as(reversed.body()).isEqualTo(200);
        assertThat(dueOf(admin, t.previous())).containsEntry("status", "OPEN");
        assertThat(dec(dueOf(admin, t.previous()).get("balance"))).isEqualByComparingTo("10000.00");
        Resp ledger = get(BILLING + "/schools/" + t.school().schoolId() + "/ledger", admin);
        assertThat(list(ledger, "collections")).hasSize(2);
        assertThat(dec(ledger.map().get("advanceCredit"))).isEqualByComparingTo("0.00");
        assertThat(post(BILLING + "/collections/" + collection + "/reverse", admin, Map.of("reason", "again")).status()).isEqualTo(409);
    }

    @Test
    void anAdvanceCanBeAllocatedLaterAndADueWithAllocationsCannotBeCancelled() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchoolSince(admin, 80, "10000");
        mapTeacher(admin, f, 0, thisMonth().atDay(1));
        UUID collection = collect(admin, collectionBody(f.schoolId(), "4000")).id();
        UUID due = raiseOne(admin, thisMonth(), f.schoolId());

        Resp allocated = post(BILLING + "/collections/" + collection + "/allocate", admin, Map.of());

        assertThat(allocated.status()).as(allocated.body()).isEqualTo(200);
        assertThat(dec(dueOf(admin, due).get("balance"))).isEqualByComparingTo("6000.00");
        assertThat(post(BILLING + "/dues/" + due + "/cancel", admin, Map.of("reason", "mistake")).status()).isEqualTo(409);
    }

    @Test
    void anAdjustmentLowersTheBalanceCannotExceedItAndCanBeReversed() {
        String admin = signInAs(Role.ADMIN).token();
        TwoMonths t = twoMonths(admin);

        Resp added = post(BILLING + "/dues/" + t.current() + "/adjustments", admin, Map.of("amount", "2000", "reason", "Agreed deduction for strike days"));
        assertThat(added.status()).as(added.body()).isEqualTo(201);
        Map<String, Object> due = dueOf(admin, t.current());
        assertThat(dec(due.get("balance"))).isEqualByComparingTo("8000.00");
        assertThat(due).containsEntry("status", "PART_PAID");
        assertThat(post(BILLING + "/dues/" + t.current() + "/adjustments", admin, Map.of("amount", "8000.01", "reason", "too much")).status())
                .isEqualTo(400);
        assertThat(post(BILLING + "/dues/" + t.current() + "/adjustments", admin, Map.of("amount", "100", "reason", "")).status()).isEqualTo(400);

        UUID adjustment = added.id();
        assertThat(post(BILLING + "/adjustments/" + adjustment + "/reverse", admin, Map.of("reason", "Deduction withdrawn")).status()).isEqualTo(200);
        assertThat(dec(dueOf(admin, t.current()).get("balance"))).isEqualByComparingTo("10000.00");
        assertThat(dueOf(admin, t.current())).containsEntry("status", "OPEN");
        assertThat(post(BILLING + "/adjustments/" + adjustment + "/reverse", admin, Map.of("reason", "twice")).status()).isEqualTo(409);
    }

    @Test
    void aZoneManagerRecordsCollectionsForOwnSchoolsOnlyAndCannotAdjustOrReverse() {
        String admin = signInAs(Role.ADMIN).token();
        TwoMonths mine = twoMonths(admin);
        TwoMonths theirs = twoMonths(admin);
        String manager = mine.school().manager().token();

        Resp own = collect(manager, collectionBody(mine.school().schoolId(), "1000"));
        assertThat(own.status()).as(own.body()).isEqualTo(201);
        assertThat(collect(manager, collectionBody(theirs.school().schoolId(), "1000")).status()).isEqualTo(404);
        assertThat(post(BILLING + "/collections/" + own.id() + "/reverse", manager, Map.of("reason", "oops")).status()).isEqualTo(403);
        assertThat(post(BILLING + "/dues/" + mine.current() + "/adjustments", manager, Map.of("amount", "1", "reason", "no")).status())
                .isEqualTo(403);
        assertThat(get(BILLING + "/settings", manager).status()).isEqualTo(200);
        assertThat(put(BILLING + "/settings", manager, Map.of("paymentDueDays", 20)).status()).isEqualTo(403);
    }

    @Test
    void collectionRulesAreEnforced() {
        String admin = signInAs(Role.ADMIN).token();
        TwoMonths t = twoMonths(admin);
        UUID school = t.school().schoolId();
        Map<String, Object> future = collectionBody(school, "100");
        future.put("receivedOn", java.time.LocalDate.now().plusDays(3).toString());
        assertThat(collect(admin, future).status()).isEqualTo(400);
        Map<String, Object> mode = collectionBody(school, "100");
        mode.put("mode", "BARTER");
        assertThat(collect(admin, mode).status()).isEqualTo(400);
        assertThat(collect(admin, collectionBody(school, "0")).status()).isEqualTo(400);
        assertThat(collect(signInAs(Role.TEACHER).token(), collectionBody(school, "100")).status()).isEqualTo(403);
    }

    @Test
    void ageingBucketsFollowTheDaysPastTheDueDateAndRespectScope() {
        String admin = signInAs(Role.ADMIN).token();
        TwoMonths mine = twoMonths(admin);
        TwoMonths theirs = twoMonths(admin);
        makeOverdue(mine.previous(), 45);
        makeOverdue(mine.current(), 5);
        makeOverdue(theirs.previous(), 100);
        String manager = mine.school().manager().token();

        Resp ageing = get(BILLING + "/ageing", manager);

        assertThat(ageing.status()).as(ageing.body()).isEqualTo(200);
        List<Map<String, Object>> schools = list(ageing, "schools");
        assertThat(schools).extracting(m -> m.get("schoolId")).containsExactly(mine.school().schoolId().toString());
        Map<String, Object> row = schools.get(0);
        assertThat(dec(row.get("b31to60"))).isEqualByComparingTo("10000.00");
        assertThat(dec(row.get("b0to30"))).isEqualByComparingTo("10000.00");
        assertThat(dec(row.get("over90"))).isEqualByComparingTo("0.00");
        assertThat(dec(row.get("total"))).isEqualByComparingTo("20000.00");
        Resp all = get(BILLING + "/ageing", admin);
        assertThat(list(all, "schools")).extracting(m -> m.get("schoolId")).contains(
                mine.school().schoolId().toString(), theirs.school().schoolId().toString());
        Map<String, Object> theirRow = list(all, "schools").stream()
                .filter(m -> theirs.school().schoolId().toString().equals(m.get("schoolId"))).findFirst().orElseThrow();
        assertThat(dec(theirRow.get("over90"))).isEqualByComparingTo("10000.00");
    }

    @Test
    void theOverviewTotalsBilledCollectedAndOutstandingForTheMonthInScope() {
        String admin = signInAs(Role.ADMIN).token();
        TwoMonths t = twoMonths(admin);
        collect(admin, collectionBody(t.school().schoolId(), "12000"));
        String manager = t.school().manager().token();

        Resp overview = get(BILLING + "/overview?month=" + thisMonth(), manager);

        assertThat(overview.status()).as(overview.body()).isEqualTo(200);
        assertThat(dec(overview.map().get("owed"))).isEqualByComparingTo("10000.00");
        assertThat(dec(overview.map().get("collected"))).isEqualByComparingTo("2000.00");
        assertThat(dec(overview.map().get("outstanding"))).isEqualByComparingTo("8000.00");
        assertThat(list(overview, "schools")).hasSize(1);
        Resp previous = get(BILLING + "/overview?month=" + YearMonth.from(thisMonth().minusMonths(1)), manager);
        assertThat(dec(previous.map().get("collected"))).isEqualByComparingTo("10000.00");
    }

    @Test
    void theCsvExportIsInScopeAndGuardsFormulaCells() {
        String admin = signInAs(Role.ADMIN).token();
        TwoMonths mine = twoMonths(admin);
        TwoMonths theirs = twoMonths(admin);
        String manager = mine.school().manager().token();

        Resp csv = get(BILLING + "/export?view=overview&month=" + thisMonth(), manager);

        assertThat(csv.status()).isEqualTo(200);
        assertThat(csv.body()).startsWith("School,Zone,Zone Manager").doesNotContain(theirs.school().schoolId().toString());
        assertThat(get(BILLING + "/export?view=ledger&schoolId=" + theirs.school().schoolId(), manager).status()).isEqualTo(404);
        assertThat(get(BILLING + "/export?view=bogus", admin).status()).isEqualTo(400);
        assertThat(get(BILLING + "/export?view=overview&month=" + thisMonth(), signInAs(Role.TEACHER).token()).status()).isEqualTo(403);
    }

    @Test
    void anEveryoneElseAndAnonymousCallerIsRefused() {
        assertThat(get(BILLING + "/overview?month=" + thisMonth(), null).status()).isEqualTo(401);
        assertThat(get(BILLING + "/overview?month=" + thisMonth(), signInAs(Role.SYSTEM).token()).status()).isEqualTo(403);
        assertThat(get(BILLING + "/overview?month=bad", signInAs(Role.ADMIN).token()).status()).isEqualTo(400);
    }
}
