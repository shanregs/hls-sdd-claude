package com.hls.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.reporting.api.ManagerColumns;
import com.hls.reporting.api.TileContext;
import com.hls.support.TaskTestBase;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 014c A6: tasks assigned and completed by month on the Manager columns; the existing three columns are unchanged. */
class TaskMonthColumnsTest extends TaskTestBase {

    @Autowired
    private List<ManagerColumns> sources;

    private ManagerColumns tasks() {
        return sources.stream()
                .filter(s -> s.columns().stream().anyMatch(c -> c.key().equals("tasks.assigned")))
                .findFirst()
                .orElseThrow();
    }

    private static TileContext context(YearMonth month) {
        return new TileContext() {
            public UUID userId() {
                return UUID.randomUUID();
            }

            public Set<Role> roles() {
                return Set.of(Role.ADMIN);
            }

            public LocalDate today() {
                return LocalDate.now(ZoneId.of("Asia/Kolkata"));
            }

            public YearMonth month() {
                return month;
            }

            public Optional<UUID> zoneId() {
                return Optional.empty();
            }

            @SuppressWarnings("unchecked")
            public <T> T memo(String key, Supplier<T> compute) {
                return compute.get();
            }
        };
    }

    @Test
    void assignedAndCompletedAreCountedByMonthForTwoManagersInOneQuery() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx a = manager(admin);
        ManagerCtx b = manager(admin);
        UUID ua = a.signed().userId();
        UUID ub = b.signed().userId();
        YearMonth now = YearMonth.from(today());
        YearMonth earlier = now.minusMonths(2);

        UUID done = newTask(admin, ua, today().plusDays(3));
        startTask(a.token(), done);
        assertThat(completeTask(a.token(), done).status()).isEqualTo(200);
        UUID open = newTask(admin, ua, today().plusDays(3));
        UUID old = newTask(admin, ua, today().plusDays(3));
        jdbc.update("update task set created_at = ?::timestamptz where id = ?", earlier.atDay(5) + "T10:00:00+05:30", old);
        UUID cancelled = newTask(admin, ub, today().plusDays(3));
        jdbc.update("update task set status = 'CANCELLED' where id = ?", cancelled);
        UUID bDone = newTask(admin, ub, today().plusDays(3));
        startTask(b.token(), bDone);
        assertThat(completeTask(b.token(), bDone).status()).isEqualTo(200);
        // completed two months ago, created then too
        jdbc.update(
                "update task set created_at = ?::timestamptz, completed_at = ?::timestamptz where id = ?",
                earlier.atDay(2) + "T09:00:00+05:30", earlier.atDay(3) + "T09:00:00+05:30", bDone);
        assertThat(open).isNotNull();

        Map<UUID, Map<String, Long>> thisMonth = tasks().figures(context(now), Set.of(ua, ub, UUID.randomUUID()));
        Map<UUID, Map<String, Long>> past = tasks().figures(context(earlier), Set.of(ua, ub));

        assertThat(thisMonth.get(ua)).containsEntry("tasks.assigned", 2L).containsEntry("tasks.completed", 1L);
        assertThat(thisMonth.get(ub)).containsEntry("tasks.assigned", 0L).containsEntry("tasks.completed", 0L);
        assertThat(past.get(ua)).containsEntry("tasks.assigned", 1L).containsEntry("tasks.completed", 0L);
        assertThat(past.get(ub)).containsEntry("tasks.assigned", 1L).containsEntry("tasks.completed", 1L);
        // the three existing columns are still there with their numbers
        assertThat(thisMonth.get(ua)).containsKeys("tasks.overdue", "tasks.awaiting", "tasks.escalated");
        assertThat(tasks().columns()).extracting(ManagerColumns.Column::key)
                .containsExactly("tasks.overdue", "tasks.awaiting", "tasks.escalated", "tasks.assigned", "tasks.completed");
    }
}
