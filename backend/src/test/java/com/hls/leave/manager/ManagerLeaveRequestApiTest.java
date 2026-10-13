package com.hls.leave.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 032 US3: a Manager asks for leave, and what is refused. */
class ManagerLeaveRequestApiTest extends ManagerLeaveTestBase {

    @Test
    void aManagerRequestsLeaveAndSeesItPending() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate[] days = twoWorkingDays();

        Resp resp = ask(manager.token(), days[0], days[1]);

        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        assertThat(resp.map()).containsEntry("status", "PENDING").containsEntry("leaveTypeName", "Casual");
        List<Map<String, Object>> mine = listOf(get(MY_LEAVE, manager.token()));
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0)).containsEntry("reason", "Family function");
    }

    @Test
    void theTypesListMarksTheLossOfPayType() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);

        List<Map<String, Object>> types = listOf(get(MY_LEAVE + "/types", manager.token()));

        assertThat(types).extracting(t -> t.get("name")).contains("Casual", "Loss of Pay");
        assertThat(types.stream().filter(t -> "Loss of Pay".equals(t.get("name"))).findFirst().orElseThrow())
                .containsEntry("lossOfPay", true);
        assertThat(types.stream().filter(t -> "Casual".equals(t.get("name"))).findFirst().orElseThrow())
                .containsEntry("lossOfPay", false);
    }

    @Test
    void invalidRequestsAreRefusedWith400() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate[] days = twoWorkingDays();
        UUID casual = type("CASUAL");

        assertThat(ask(manager.token(), days[1], days[0]).status()).as("reversed dates").isEqualTo(400);
        assertThat(ask(manager.token(), anOffDay(), anOffDay()).status()).as("no working day").isEqualTo(400);
        assertThat(post(MY_LEAVE, manager.token(), body(casual, days[0], days[1], false, false, " ")).status())
                .as("blank reason")
                .isEqualTo(400);
        assertThat(post(MY_LEAVE, manager.token(), body(casual, days[0], days[1], false, false, null)).status())
                .as("missing reason")
                .isEqualTo(400);
        assertThat(post(MY_LEAVE, manager.token(), body(casual, days[0], days[1], false, false, "x".repeat(501))).status())
                .as("long reason")
                .isEqualTo(400);
        assertThat(post(MY_LEAVE, manager.token(), body(null, days[0], days[1], false, false, "Reason")).status())
                .as("no type")
                .isEqualTo(400);
        assertThat(post(MY_LEAVE, manager.token(), body(UUID.randomUUID(), days[0], days[1], false, false, "Reason")).status())
                .as("unknown type")
                .isEqualTo(400);
        assertThat(post(
                                MY_LEAVE,
                                manager.token(),
                                Map.of("leaveTypeId", casual.toString(), "firstDate", "soon", "lastDate", "later", "reason", "x"))
                        .status())
                .as("bad dates")
                .isEqualTo(400);
        assertThat(leaveCount(manager)).isZero();
    }

    @Test
    void aDateBeforeTheJoiningDateAndNoJoiningDateAreRefused() {
        String admin = signInAs(Role.ADMIN).token();
        LocalDate[] days = twoWorkingDays();

        ManagerCtx late = newManager(admin);
        setJoining(late.managerId(), days[1], null);
        assertThat(ask(late.token(), days[0], days[1]).status()).as("before joining").isEqualTo(400);

        ManagerCtx none = newManager(admin);
        Resp noJoining = ask(none.token(), days[0], days[1]);
        assertThat(noJoining.status()).isEqualTo(400);
        assertThat(noJoining.body()).contains("joining date");

        ManagerCtx gone = newManager(admin);
        setJoining(gone.managerId(), days[0].minusDays(30), days[0]);
        assertThat(ask(gone.token(), days[0], days[1]).status()).as("after exit").isEqualTo(400);
    }

    @Test
    void aHalfDayMustFallOnAWorkingDayAndOneDayIsNotTwoHalves() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate[] across = acrossAnOffDay();
        LocalDate[] days = twoWorkingDays();
        UUID casual = type("CASUAL");
        LocalDate off = across[0].plusDays(1);

        assertThat(post(MY_LEAVE, manager.token(), body(casual, off, across[1], true, false, "Reason")).status())
                .as("half day on a day off")
                .isEqualTo(400);
        assertThat(post(MY_LEAVE, manager.token(), body(casual, days[0], days[0], true, true, "Reason")).status())
                .as("two halves of one day")
                .isEqualTo(400);
        assertThat(post(MY_LEAVE, manager.token(), body(casual, days[0], days[1], true, true, "Reason")).status())
                .as("a half at each end")
                .isEqualTo(201);
    }

    @Test
    void daysOffInsideTheRangeAreAllowed() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate[] across = acrossAnOffDay();

        assertThat(ask(manager.token(), across[0], across[1]).status()).isEqualTo(201);
    }

    @Test
    void anOverlapWithAPendingOrApprovedRequestIs409ButNotWithAClosedOne() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate[] days = twoWorkingDays();
        UUID first = ask(manager.token(), days[0], days[0]).id();

        assertThat(ask(manager.token(), days[0], days[1]).status()).as("pending overlap").isEqualTo(409);

        assertThat(post(LEAVE + "/" + first + "/approve", admin, Map.of()).status()).isEqualTo(200);
        assertThat(ask(manager.token(), days[0], days[1]).status()).as("approved overlap").isEqualTo(409);

        assertThat(post(LEAVE + "/" + first + "/revoke", admin, Map.of("reason", "Plans changed")).status())
                .isEqualTo(200);
        UUID again = ask(manager.token(), days[0], days[1]).id();
        assertThat(delete(MY_LEAVE + "/" + again, manager.token()).status()).isEqualTo(204);
        assertThat(ask(manager.token(), days[0], days[1]).status()).as("after revoke and cancel").isEqualTo(201);
    }

    @Test
    void aManagerCancelsAPendingRequestButNotAnApprovedOneOrAnotherManagers() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        ManagerCtx other = longJoinedManager(admin);
        LocalDate[] days = twoWorkingDays();
        UUID pending = ask(manager.token(), days[0], days[0]).id();
        UUID approved = ask(manager.token(), days[1], days[1]).id();
        post(LEAVE + "/" + approved + "/approve", admin, Map.of());

        assertThat(delete(MY_LEAVE + "/" + pending, other.token()).status()).as("another Manager").isEqualTo(404);
        assertThat(delete(MY_LEAVE + "/" + approved, manager.token()).status()).as("approved").isEqualTo(409);
        assertThat(delete(MY_LEAVE + "/" + pending, manager.token()).status()).isEqualTo(204);
        assertThat(delete(MY_LEAVE + "/" + pending, manager.token()).status()).as("twice").isEqualTo(409);

        assertThat(leaveStatus(pending)).isEqualTo("CANCELLED");
        assertThat(leaveStatus(approved)).isEqualTo("APPROVED");
        assertThat(listOf(get(MY_LEAVE, other.token()))).isEmpty();
    }

    private long leaveCount(ManagerCtx manager) {
        return jdbc.queryForObject(
                "select count(*) from manager_leave_request where manager_id = ?", Long.class, manager.managerId());
    }
}
