package com.hls.leave.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 028 T020: the Manager leave tiles equal the Manager Leave list and the pending forgotten check-ins. */
class ManagerLeaveTilesTest extends ManagerLeaveTestBase {

    private static final String T = "/api/v1/dashboard/areas/MANAGERS/tiles/";

    @SuppressWarnings("unchecked")
    private static long value(Resp tile, String label) {
        for (Map<String, Object> v : (List<Map<String, Object>>) tile.map().get("values")) {
            if (label.equals(v.get("label"))) {
                return ((Number) v.get("value")).longValue();
            }
        }
        throw new AssertionError("No value " + label + " in " + tile.body());
    }

    @Test
    void pendingRequestsEqualThePendingLeaveListPlusPendingForgottenCheckIns() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx one = longJoinedManager(admin);
        ManagerCtx two = longJoinedManager(admin);
        LocalDate[] days = twoWorkingDays();
        assertThat(ask(one.token(), days[0], days[0]).status()).isEqualTo(201);
        assertThat(ask(two.token(), days[1], days[1]).status()).isEqualTo(201);

        Resp tile = get(T + "managers.pendingRequests", admin);
        assertThat(tile.status()).as(tile.body()).isEqualTo(200);
        int pendingLeave = listOf(get(LEAVE + "?status=PENDING", admin)).size();
        long pendingCheckIns = jdbc.queryForObject(
                "select count(*) from manager_regularization where status = 'PENDING'", Long.class);
        assertThat(value(tile, "Leave")).isEqualTo(pendingLeave).isGreaterThanOrEqualTo(2);
        assertThat(value(tile, "Forgotten check-ins")).isEqualTo(pendingCheckIns);

        // a Manager sees only their own pending request
        assertThat(value(get(T + "managers.pendingRequests", one.token()), "Leave")).isEqualTo(1);
    }

    @Test
    void onLeaveTodayCountsDistinctManagersWithApprovedLeaveCoveringToday() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate t = today();
        jdbc.update(
                "insert into manager_leave_request (id, manager_id, leave_type_id, first_date, last_date, first_half, last_half,"
                        + " reason, status, requested_at, version) values (gen_random_uuid(), ?, ?, ?, ?, false, false,"
                        + " 'Family function', 'APPROVED', now(), 0)",
                manager.managerId(), type("CASUAL"), t, t);
        long expected = jdbc.queryForObject(
                "select count(distinct manager_id) from manager_leave_request where status = 'APPROVED'"
                        + " and first_date <= ? and last_date >= ?",
                Long.class, t, t);

        Resp tile = get(T + "managers.onLeaveToday", admin);

        assertThat(tile.status()).as(tile.body()).isEqualTo(200);
        assertThat(value(tile, "On leave")).isEqualTo(expected).isGreaterThanOrEqualTo(1);
        assertThat(value(get(T + "managers.onLeaveToday", manager.token()), "On leave")).isEqualTo(1);
    }
}
