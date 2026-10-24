package com.hls.activitycalendar;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 035 T031 (SC-005): 500 activities across 20 Managers load in under 3 seconds with a query count that ignores the item count. */
class CalendarPerformanceTest extends ActivityCalendarTestBase {

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private long statements(String token) {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
        Resp resp = calendar(token, "2020-07");
        assertThat(resp.status()).isEqualTo(200);
        return stats.getPrepareStatementCount();
    }

    @Test
    void fiveHundredActivitiesLoadFastWithAQueryCountIndependentOfTheItemCount() {
        String admin = admin();
        UUID zone = zone(admin);
        List<UUID> managers = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            managers.add(newManager(admin, zone).signed().userId());
        }
        LocalDate future = today.plusDays(30);
        List<UUID> tasks = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            tasks.add(task(admin, managers.get(i % 20), future));
        }
        for (UUID t : tasks) {
            moveTaskTo(t, LocalDate.of(2020, 7, 1 + (tasks.indexOf(t) % 28)));
        }

        long small = statements(admin);

        for (int i = 100; i < 500; i++) {
            UUID t = task(admin, managers.get(i % 20), future);
            moveTaskTo(t, LocalDate.of(2020, 7, 1 + (i % 28)));
        }
        long start = System.nanoTime();
        Resp resp = calendar(admin, "2020-07");
        long millis = (System.nanoTime() - start) / 1_000_000;
        long large = statements(admin);

        assertThat(resp.status()).isEqualTo(200);
        assertThat(items(resp)).hasSize(500);
        assertThat(millis).as("load time in ms").isLessThan(3000);
        assertThat(large).as("statements for 500 items vs 100").isEqualTo(small);
    }
}
