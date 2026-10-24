package com.hls.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.TaskTestBase;
import com.hls.tasks.api.TaskActivities;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 035 T019: plannedBetween of tasks is unchanged by the amendment. */
class TaskPlannedBetweenUnchangedTest extends TaskTestBase {

    @Autowired
    TaskActivities feed;

    @Test
    void plannedBetweenStillExcludesCancelledTasksAndKeepsTheSameValues() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx m = manager(admin);
        UUID open = newTask(admin, m.signed().userId(), today().plusDays(2));
        UUID cancelled = newTask(admin, m.signed().userId(), today().plusDays(2));
        act(admin, cancelled, "cancel", Map.of("reason", "not needed"));

        var found = feed.plannedBetween(today(), today().plusDays(5), m.signed().userId());

        assertThat(found).extracting(TaskActivities.PlannedActivity::id).containsExactly(open);
        var p = found.get(0);
        assertThat(p.kind()).isEqualTo("SCHOOL_VISIT");
        assertThat(p.ownerUserId()).isEqualTo(m.signed().userId());
        assertThat(p.date()).isEqualTo(today().plusDays(2));
        assertThat(p.status()).isEqualTo("ASSIGNED");
    }
}
