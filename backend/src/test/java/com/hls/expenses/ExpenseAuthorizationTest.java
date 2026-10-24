package com.hls.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.ExpensesTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** Spec 015 US3, FR-027: every review endpoint refuses Manager, Teacher and System; Admin and Director see everything. */
class ExpenseAuthorizationTest extends ExpensesTestBase {

    private record Call(HttpMethod method, String path, Object body) {}

    @Test
    void everyExpensesEndpointRefusesManagerTeacherAndSystem() {
        String claimant = signInAs(Role.MANAGER).token();
        UUID id = newSubmittedClaim(claimant, "10.00");
        String billId = (String) billsOf(get(MINE + "/" + id, claimant)).get(0).get("id");
        UUID budget = UUID.randomUUID();
        List<Call> calls = List.of(
                new Call(HttpMethod.GET, REVIEW, null),
                new Call(HttpMethod.GET, REVIEW + "/" + id, null),
                new Call(HttpMethod.GET, REVIEW + "/" + id + "/bills/" + billId, null),
                new Call(HttpMethod.POST, REVIEW + "/" + id + "/approve", Map.of("version", 0)),
                new Call(HttpMethod.POST, REVIEW + "/" + id + "/reject", Map.of("version", 0, "reason", "x")),
                new Call(HttpMethod.POST, REVIEW + "/" + id + "/reverse", Map.of("version", 0, "reason", "x")),
                new Call(HttpMethod.POST, REVIEW + "/" + id + "/record-paid", Map.of("version", 0, "paidOn", today().toString(), "mode", "CASH")),
                new Call(HttpMethod.DELETE, REVIEW + "/" + id + "/bills/" + billId, Map.of("reason", "x")),
                new Call(HttpMethod.GET, REVIEW + "/budgets?month=2026-10", null),
                new Call(HttpMethod.GET, REVIEW + "/budgets/detail?month=2026-10", null),
                new Call(HttpMethod.PUT, REVIEW + "/budgets", Map.of("month", "2026-10", "amount", "100.00")),
                new Call(HttpMethod.GET, REVIEW + "/budgets/" + budget + "/history", null),
                new Call(HttpMethod.GET, REVIEW + "/pending", null),
                new Call(HttpMethod.GET, REVIEW + "/costs/per-school?from=2026-10-01&to=2026-10-31", null),
                new Call(HttpMethod.GET, REVIEW + "/costs/per-teacher?from=2026-10-01&to=2026-10-31", null),
                new Call(HttpMethod.GET, REVIEW + "/export?month=2026-10", null));
        for (Role role : List.of(Role.MANAGER, Role.TEACHER, Role.SYSTEM)) {
            String token = signInAs(role).token();
            for (Call call : calls) {
                assertThat(send(call.method(), call.path(), token, call.body()).status())
                        .as(role + " " + call.method() + " " + call.path())
                        .isEqualTo(403);
            }
        }
        for (Call call : calls) {
            assertThat(send(call.method(), call.path(), null, call.body()).status()).as("anonymous " + call.path()).isEqualTo(401);
        }
    }

    @Test
    void adminAndDirectorSeeEveryonesClaimsOrgWide() {
        String one = signInAs(Role.MANAGER).token();
        String two = signInAs(Role.TEACHER).token();
        UUID a = newSubmittedClaim(one, "21.00");
        UUID b = newSubmittedClaim(two, "22.00");
        for (String reviewer : List.of(admin().token(), director().token())) {
            assertThat(get(REVIEW + "/" + a, reviewer).status()).isEqualTo(200);
            assertThat(get(REVIEW + "/" + b, reviewer).status()).isEqualTo(200);
            List<String> queue = content(get(REVIEW + "?size=200", reviewer)).stream().map(r -> (String) r.get("id")).toList();
            assertThat(queue).contains(a.toString(), b.toString());
        }
    }

    @Test
    void onlyAdminMayRecordPaymentAndTheDirectorMayReverseButNotRecord() {
        String claimant = signInAs(Role.MANAGER).token();
        String admin = admin().token();
        String director = director().token();
        UUID id = newSubmittedClaim(claimant, "30.00");
        assertThat(approve(director, id).status()).isEqualTo(200);

        assertThat(recordPaid(director, id, today(), "CASH", null).status()).isEqualTo(403);
        assertThat(allowedActions(get(REVIEW + "/" + id, director))).containsExactlyInAnyOrder("REVERSE", "REMOVE_BILL");
        assertThat(allowedActions(get(REVIEW + "/" + id, admin))).containsExactlyInAnyOrder("REVERSE", "RECORD_PAID", "REMOVE_BILL");
        assertThat(recordPaid(admin, id, today(), "CASH", null).status()).isEqualTo(200);
    }
}
