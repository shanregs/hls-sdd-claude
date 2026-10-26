package com.hls.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.TaskTestBase;
import com.hls.tasks.api.TaskCreation;
import com.hls.tasks.api.TaskCreation.FollowUpRequest;
import com.hls.tasks.api.TaskProgress;
import com.hls.tasks.api.TaskType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 029 T020: the TaskProgress interface verifies or sends back a Completed task addressed by its source and key. */
class TaskProgressTest extends TaskTestBase {

    @Autowired
    TaskCreation creation;

    @Autowired
    TaskProgress progress;

    private UUID make(UUID assignee, String key) {
        return creation.createFollowUp(new FollowUpRequest(
                        TaskType.CONTENT_SUBMISSION, "Content task " + key, null, assignee, today().plusDays(2), null,
                        "CONTENT", UUID.randomUUID(), "CNT-1", null, "TEST_PROGRESS", key, null))
                .id();
    }

    private String status(UUID task) {
        return jdbc.queryForObject("select status from task where id = ?", String.class, task);
    }

    @Test
    void aCompletedTaskIsVerifiedBySourceAndKey() {
        ManagerCtx manager = manager(signInAs(Role.ADMIN).token());
        UUID reviewer = signInAs(Role.DIRECTOR).userId();
        String key = UUID.randomUUID().toString();
        UUID task = make(manager.signed().userId(), key);
        assertThat(progress.verify("TEST_PROGRESS", key, reviewer)).as("not completed yet").isFalse();
        assertThat(creation.complete("TEST_PROGRESS", key, "Submitted version 1 for review.", manager.signed().userId())).isTrue();
        assertThat(status(task)).isEqualTo("COMPLETED");
        assertThat(progress.verify("TEST_PROGRESS", key, reviewer)).isTrue();
        assertThat(status(task)).isEqualTo("VERIFIED");
        assertThat(progress.verify("TEST_PROGRESS", key, reviewer)).as("already verified").isFalse();
    }

    @Test
    void aCompletedTaskIsSentBackWithAReasonAndCanBeCompletedAgain() {
        ManagerCtx manager = manager(signInAs(Role.ADMIN).token());
        UUID reviewer = signInAs(Role.DIRECTOR).userId();
        String key = UUID.randomUUID().toString();
        UUID task = make(manager.signed().userId(), key);
        creation.complete("TEST_PROGRESS", key, "Submitted version 1 for review.", manager.signed().userId());
        assertThat(progress.sendBack("TEST_PROGRESS", key, "Audio too quiet", reviewer)).isTrue();
        assertThat(status(task)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("select count(*) from task_history where task_id = ? and event = 'SENT_BACK'", Integer.class, task)).isEqualTo(1);
        assertThat(creation.complete("TEST_PROGRESS", key, "Submitted version 2 for review.", manager.signed().userId())).isTrue();
        assertThat(status(task)).isEqualTo("COMPLETED");
    }

    @Test
    void anUnknownSourceOrKeyChangesNothing() {
        UUID reviewer = signInAs(Role.DIRECTOR).userId();
        assertThat(progress.verify("TEST_PROGRESS", "nope", reviewer)).isFalse();
        assertThat(progress.sendBack("TEST_PROGRESS", "nope", "reason", reviewer)).isFalse();
        assertThat(progress.verify(null, null, reviewer)).isFalse();
    }
}
