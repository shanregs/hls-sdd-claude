package com.hls.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.identity.user.Role;
import com.hls.support.TaskTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 025 FR-010 and FR-015: the task history cannot be changed, and notifications carry the task's details and link. */
class TaskAuditAndNotificationTest extends TaskTestBase {

    @Test
    void theTaskHistoryRefusesUpdateAndDelete() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = manager(admin);
        UUID task = newTask(admin, manager.signed().userId(), today().plusDays(1));

        assertThatThrownBy(() -> jdbc.update("update task_history set note = 'x' where task_id = ?", task))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from task_history where task_id = ?", task)).hasMessageContaining("append-only");
        assertThat(jdbc.queryForObject("select count(*) from task_history where task_id = ?", Integer.class, task)).isEqualTo(1);
    }

    @Test
    void theAssignedNotificationNamesTheTaskAndLinksToIt() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = manager(admin);
        UUID task = newTask(admin, manager.signed().userId(), today().plusDays(2));

        Resp list = get("/api/v1/me/notifications", manager.token());
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body()).contains("TASK_ASSIGNED", "/operations/tasks/" + task);
        String message = jdbc.queryForObject(
                "select message from notification where recipient_user_id = ? and type = 'TASK_ASSIGNED'",
                String.class,
                manager.signed().userId());
        assertThat(message).contains("due " + String.format("%02d/%02d/%04d", today().plusDays(2).getDayOfMonth(),
                today().plusDays(2).getMonthValue(), today().plusDays(2).getYear()));
    }

    @Test
    void aManagersOwnTaskDoesNotNotifyThemselves() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = manager(admin);
        newTask(manager.token(), manager.signed().userId(), today().plusDays(2));
        assertThat(notificationCount(manager.signed().userId(), "TASK_ASSIGNED")).isZero();
    }
}
