package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 028 T043: the FORGOTTEN_CHECK_IN inbox kind equals the pending requests, and a decided one is gone. */
class ForgottenCheckInApprovalsTest extends ManagerAttendanceTestBase {

    private static final String INBOX = "/api/v1/dashboard/inbox?kind=FORGOTTEN_CHECK_IN&size=100";

    @SuppressWarnings("unchecked")
    private static List<String> inboxIds(Resp resp) {
        return ((List<Map<String, Object>>) resp.map().get("items")).stream().map(i -> (String) i.get("itemId")).toList();
    }

    @Test
    void aPendingRequestIsInTheInboxUntilItIsDecided() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        Map<String, Object> body = new HashMap<>();
        body.put("date", today().minusDays(2).toString());
        body.put("proposedTime", "09:15");
        body.put("dayType", "OFFICE");
        body.put("reason", "Phone was off");
        UUID id = post(ME + "/regularizations", manager.token(), body).id();

        Resp before = get(INBOX, admin);
        assertThat(before.status()).as(before.body()).isEqualTo(200);
        assertThat(inboxIds(before)).contains(id.toString());
        long pending = jdbc.queryForObject("select count(*) from manager_regularization where status = 'PENDING'", Long.class);
        assertThat(inboxIds(before)).hasSize((int) pending);

        assertThat(post(ATT + "/regularizations/" + id + "/approve", admin, Map.of()).status()).isEqualTo(200);
        assertThat(inboxIds(get(INBOX, admin))).doesNotContain(id.toString());
    }
}
