package com.hls.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.ExpensesTestBase;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 015 US7 and SC-008: a claimant reads only their own claims, with what they are still owed. */
class MyExpensesApiTest extends ExpensesTestBase {

    @Test
    void onlyOwnClaimsNewestFirstWithStatusAndMonthFilters() {
        String manager = signInAs(Role.MANAGER).token();
        String other = signInAs(Role.MANAGER).token();
        UUID older = newDraft(manager, claimBody(today().minusDays(30), "10.00", "OFFICE_SUPPLIES"));
        UUID newer = newDraft(manager, claimBody(today().minusDays(1), "20.00", "OFFICE_SUPPLIES"));
        UUID submitted = newSubmittedClaim(manager, "30.00");
        UUID theirs = newSubmittedClaim(other, "99.00");

        List<String> ids = ids(get(MINE, manager));
        assertThat(ids).containsExactlyInAnyOrder(older.toString(), newer.toString(), submitted.toString());
        assertThat(ids).doesNotContain(theirs.toString());
        assertThat(ids.indexOf(older.toString())).isGreaterThan(ids.indexOf(newer.toString()));

        assertThat(ids(get(MINE + "?status=DRAFT", manager))).containsExactlyInAnyOrder(older.toString(), newer.toString());
        assertThat(ids(get(MINE + "?status=SUBMITTED", manager))).containsExactly(submitted.toString());
        assertThat(get(MINE + "?status=NOPE", manager).status()).isEqualTo(400);

        String lastMonth = YearMonth.from(today().minusDays(30)).toString();
        List<String> inMonth = ids(get(MINE + "?month=" + lastMonth, manager));
        assertThat(inMonth).contains(older.toString());
        assertThat(inMonth).doesNotContain(theirs.toString());
        assertThat(get(MINE + "?month=not-a-month", manager).status()).isEqualTo(400);
    }

    @Test
    void owedIsTheSumOfOwnApprovedPersonalClaimsNotYetPaid() {
        String manager = signInAs(Role.MANAGER).token();
        String admin = admin().token();
        UUID personal = newSubmittedClaim(manager, "800.00");
        UUID another = newSubmittedClaim(manager, "200.50");
        UUID rejected = newSubmittedClaim(manager, "55.00");
        newSubmittedClaim(manager, "66.00");
        UUID company = newDraft(manager, withPaidBy(travelBody("77.00"), "COMPANY"));
        uploadBill(manager, company, "bill.jpg", JPG);
        assertThat(submit(manager, company).status()).isEqualTo(200);

        assertThat(get(MINE, manager).map()).containsEntry("owed", "0.00");
        assertThat(approve(admin, personal).status()).isEqualTo(200);
        assertThat(approve(admin, another).status()).isEqualTo(200);
        assertThat(reject(admin, rejected, "No").status()).isEqualTo(200);
        assertThat(approve(admin, company).status()).isEqualTo(200);

        assertThat(get(MINE, manager).map()).containsEntry("owed", "1000.50");

        // recorded paid outside: no longer owed
        assertThat(recordPaid(admin, personal, today(), "BANK_TRANSFER", "UTR1").status()).isEqualTo(200);
        assertThat(get(MINE, manager).map()).containsEntry("owed", "200.50");
    }

    @Test
    void anotherManagerCannotReadAClaimOrItsBill() {
        String manager = signInAs(Role.MANAGER).token();
        String other = signInAs(Role.MANAGER).token();
        UUID id = newReadyClaim(manager, "45.00");
        Resp detail = get(MINE + "/" + id, manager);
        String billId = (String) billsOf(detail).get(0).get("id");

        assertThat(get(MINE + "/" + id, other).status()).isEqualTo(404);
        assertThat(get(MINE + "/" + id + "/bills/" + billId, other).status()).isEqualTo(404);
        assertThat(put(MINE + "/" + id, other, Map.of("version", 0)).status()).isIn(400, 404);
        assertThat(delete(MINE + "/" + id, other).status()).isEqualTo(404);
        assertThat(uploadBill(other, id, "x.jpg", JPG).status()).isEqualTo(404);
        assertThat(get(MINE + "/" + id + "/bills/" + billId, manager).status()).isEqualTo(200);
    }

    @Test
    void aTeacherReachesOnlyMyExpensesAndSystemNothing() {
        Signed teacher = teacherUser();
        UUID id = newReadyClaim(teacher.token(), "15.00");
        assertThat(get(MINE + "/" + id, teacher.token()).status()).isEqualTo(200);
        assertThat(get(REVIEW, teacher.token()).status()).isEqualTo(403);
        assertThat(get(REVIEW + "/" + id, teacher.token()).status()).isEqualTo(403);

        String system = signInAs(Role.SYSTEM).token();
        assertThat(get(MINE, system).status()).isEqualTo(403);
        assertThat(createClaim(system, travelBody("5.00")).status()).isEqualTo(403);
        assertThat(get(REVIEW, system).status()).isEqualTo(403);
        assertThat(get(REVIEW + "/budgets?month=2026-10", system).status()).isEqualTo(403);
        assertThat(get(MINE, null).status()).isEqualTo(401);
    }

    protected static Map<String, Object> withPaidBy(Map<String, Object> body, String paidBy) {
        body.put("paidBy", paidBy);
        return body;
    }

    protected static List<String> ids(Resp list) {
        return content(list).stream().map(m -> (String) m.get("id")).toList();
    }
}
