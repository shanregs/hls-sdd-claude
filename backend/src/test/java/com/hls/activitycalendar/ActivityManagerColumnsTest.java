package com.hls.activitycalendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.reporting.api.ManagerColumns;
import com.hls.reporting.api.TileContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 014c A7: the Manager columns equal the Activity Calendar summary of the month, per Manager. */
class ActivityManagerColumnsTest extends ActivityCalendarTestBase {

    @Autowired
    private List<ManagerColumns> sources;

    private ManagerColumns activity() {
        return sources.stream()
                .filter(s -> s.columns().stream().anyMatch(c -> c.key().equals("activity.planned")))
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
                return LocalDate.now();
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> managerRow(Resp calendar, UUID userId) {
        return ((List<Map<String, Object>>) summary(calendar).get("byManager")).stream()
                .filter(m -> userId.toString().equals(m.get("userId")))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void plannedDoneMissedAndRescheduledEqualTheCalendarSummaryAndAManagerWithNothingIsAbsent() {
        String admin = admin();
        UUID zone = zone(admin);
        ManagerCtx a = newManager(admin, zone);
        ManagerCtx quiet = newManager(admin, zone);
        UUID prospect = prospect(admin, zone);
        LocalDate far = today.plusDays(30);
        YearMonth march = YearMonth.of(2020, 3);

        UUID done = activity(a.token(), prospect, far);
        assertThat(complete(a.token(), done, "Met the principal", null).status()).isEqualTo(200);
        moveVisitTo(done, LocalDate.of(2020, 3, 5));
        jdbc.update("update marketing_activity set completed_on = ? where id = ?", LocalDate.of(2020, 3, 5), done);
        UUID openTask = task(admin, a.signed().userId(), far);
        moveTaskTo(openTask, LocalDate.of(2020, 3, 10));
        UUID moved = activity(a.token(), prospect, far);
        assertThat(post(M + "/activities/" + moved + "/reschedule", a.token(), Map.of("date", far.plusDays(3).toString())).status())
                .isEqualTo(200);
        moveVisitTo(moved, LocalDate.of(2020, 3, 20));
        jdbc.update("update activity_date_history set old_date = ?, new_date = ? where activity_id = ?",
                LocalDate.of(2020, 3, 14), LocalDate.of(2020, 3, 20), moved);

        Resp calendar = calendar(admin, march.toString());
        Map<String, Object> shown = managerRow(calendar, a.signed().userId());
        Map<UUID, Map<String, Long>> figures =
                activity().figures(context(march), Set.of(a.signed().userId(), quiet.signed().userId()));

        Map<String, Long> row = figures.get(a.signed().userId());
        assertThat(row.get("activity.planned")).isEqualTo(((Number) shown.get("planned")).longValue());
        assertThat(row.get("activity.done")).isEqualTo(((Number) shown.get("done")).longValue());
        assertThat(row.get("activity.missed")).isEqualTo(((Number) shown.get("missed")).longValue());
        assertThat(row.get("activity.rescheduled")).isEqualTo(((Number) shown.get("rescheduled")).longValue());
        assertThat(row.get("activity.planned")).isGreaterThanOrEqualTo(3L);
        assertThat(row.get("activity.rescheduled")).isGreaterThanOrEqualTo(1L);
        assertThat(figures).doesNotContainKey(quiet.signed().userId());
        assertThat(activity().columns()).extracting(ManagerColumns.Column::key)
                .containsExactly("activity.planned", "activity.done", "activity.missed", "activity.rescheduled");
    }
}
