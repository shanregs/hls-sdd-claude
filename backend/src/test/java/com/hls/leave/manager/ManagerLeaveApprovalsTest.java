package com.hls.leave.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 028 T043: the MANAGER_LEAVE inbox kind equals the pending Manager leave list, and a decided request is gone. */
class ManagerLeaveApprovalsTest extends ManagerLeaveTestBase {

    private static final String INBOX = "/api/v1/dashboard/inbox?kind=MANAGER_LEAVE&size=100";

    @SuppressWarnings("unchecked")
    private static List<String> inboxIds(Resp resp) {
        return ((List<Map<String, Object>>) resp.map().get("items")).stream().map(i -> (String) i.get("itemId")).toList();
    }

    @Test
    void aPendingRequestIsInTheInboxUntilItIsDecided() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate[] days = twoWorkingDays();
        UUID id = ask(manager.token(), days[0], days[0]).id();

        Resp before = get(INBOX, admin);
        assertThat(before.status()).as(before.body()).isEqualTo(200);
        assertThat(inboxIds(before)).contains(id.toString());
        assertThat(inboxIds(before)).hasSameSizeAs(listOf(get(LEAVE + "?status=PENDING", admin)));

        assertThat(post(LEAVE + "/" + id + "/approve", admin, Map.of()).status()).isEqualTo(200);
        assertThat(inboxIds(get(INBOX, admin))).doesNotContain(id.toString());
    }
}
