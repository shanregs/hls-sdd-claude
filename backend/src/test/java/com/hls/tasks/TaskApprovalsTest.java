package com.hls.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.TaskTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 028 T043: the TASK_VERIFICATION inbox kind equals the Tasks "awaiting" view, and a verified task is gone. */
class TaskApprovalsTest extends TaskTestBase {

    private static final String INBOX = "/api/v1/dashboard/inbox?kind=TASK_VERIFICATION&size=100";

    @SuppressWarnings("unchecked")
    private static List<String> inboxIds(Resp resp) {
        return ((List<Map<String, Object>>) resp.map().get("items")).stream().map(i -> (String) i.get("itemId")).toList();
    }

    @Test
    void aCompletedTaskIsInTheInboxUntilItIsVerified() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = manager(admin);
        UUID task = newTask(admin, manager.signed().userId(), today().plusDays(2));
        startTask(manager.token(), task);
        assertThat(completeTask(manager.token(), task).status()).isEqualTo(200);

        Resp before = get(INBOX, admin);
        assertThat(before.status()).as(before.body()).isEqualTo(200);
        assertThat(inboxIds(before)).contains(task.toString());
        assertThat(inboxIds(before)).hasSameSizeAs(ids(get(BASE + "?view=awaiting&size=100", admin)));

        assertThat(act(admin, task, "verify", null).status()).isEqualTo(200);
        assertThat(inboxIds(get(INBOX, admin))).doesNotContain(task.toString());
    }
}
