package com.hls.leave.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.attendance.api.PendingManagerRequests.Pending;
import com.hls.identity.user.Role;
import com.hls.leave.manager.internal.ManagerLeavePendingRequests;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 032 US6: the leave module tells the month lock which Manager leave requests are still waiting. */
class ManagerLeavePendingRequestsTest extends ManagerLeaveTestBase {

    @Autowired
    private ManagerLeavePendingRequests pending;

    private List<UUID> idsFor(YearMonth month, UUID managerId) {
        return pending.pendingFor(month).stream()
                .filter(p -> p.managerId().equals(managerId))
                .map(Pending::requestId)
                .toList();
    }

    @Test
    void aPendingRequestInTheMonthIsListedWithItsKindAndDates() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate[] days = twoWorkingDays();
        UUID id = ask(manager.token(), days[0], days[1]).id();

        List<Pending> found = pending.pendingFor(lastMonth()).stream()
                .filter(p -> p.requestId().equals(id))
                .toList();

        assertThat(found).hasSize(1);
        assertThat(found.get(0).kind()).isEqualTo("LEAVE");
        assertThat(found.get(0).managerId()).isEqualTo(manager.managerId());
        assertThat(found.get(0).firstDate()).isEqualTo(days[0]);
        assertThat(found.get(0).lastDate()).isEqualTo(days[1]);
    }

    @Test
    void aRequestThatOnlyTouchesTheMonthAtItsEdgeIsListedAndOneInAnotherMonthIsNot() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        YearMonth month = lastMonth();
        List<LocalDate> working = workingDates(month);
        // the leave type, dates and Manager only matter to the query, so the rows are written directly
        UUID spanning = insert(manager.managerId(), month.atDay(1).minusDays(2), working.get(0));
        UUID elsewhere = insert(manager.managerId(), month.minusMonths(2).atDay(5), month.minusMonths(2).atDay(6));

        List<UUID> ids = idsFor(month, manager.managerId());

        assertThat(ids).contains(spanning).doesNotContain(elsewhere);
        assertThat(idsFor(month.minusMonths(2), manager.managerId())).contains(elsewhere).doesNotContain(spanning);
    }

    @Test
    void aRequestStopsAppearingOnceDecidedOrCancelled() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate[] days = twoWorkingDays();
        UUID approved = ask(manager.token(), days[0], days[0]).id();
        UUID rejected = ask(manager.token(), days[1], days[1]).id();
        assertThat(idsFor(lastMonth(), manager.managerId())).containsExactlyInAnyOrder(approved, rejected);

        post(LEAVE + "/" + approved + "/approve", admin, Map.of());
        assertThat(idsFor(lastMonth(), manager.managerId())).containsExactly(rejected);

        delete(MY_LEAVE + "/" + rejected, manager.token());
        assertThat(idsFor(lastMonth(), manager.managerId())).isEmpty();

        UUID later = ask(manager.token(), days[1], days[1]).id();
        post(LEAVE + "/" + later + "/reject", admin, Map.of("reason", "Month end"));
        assertThat(idsFor(lastMonth(), manager.managerId())).isEmpty();
    }

    private UUID insert(UUID managerId, LocalDate first, LocalDate last) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into manager_leave_request (id, manager_id, leave_type_id, first_date, last_date, first_half, last_half,"
                        + " reason, status, requested_at, version) values (?, ?, ?, ?, ?, false, false, 'Direct', 'PENDING', now(), 0)",
                id,
                managerId,
                type("CASUAL"),
                first,
                last);
        return id;
    }
}
