package com.hls.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.reporting.api.Area;
import com.hls.support.MasterDataTestBase;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spec 028 SC-002: every area loads within 3 seconds on a demo-sized data set, and the number of statements an area issues
 * does not grow with the number of Managers, Teachers or tasks (no query per row).
 */
class AreaPerformanceTest extends MasterDataTestBase {

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private static final int LIMIT_MS = 3000;

    private Statistics stats() {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        return stats;
    }

    private record Load(long statements, long millis) {}

    private Map<Area, Load> loadEveryArea(String token) {
        Map<Area, Load> loads = new EnumMap<>(Area.class);
        for (Area area : Area.values()) {
            // one warm-up so class loading and connection set-up do not count
            get("/api/v1/dashboard/areas/" + area.name(), token);
            Statistics stats = stats();
            stats.clear();
            long start = System.nanoTime();
            Resp resp = get("/api/v1/dashboard/areas/" + area.name(), token);
            long millis = (System.nanoTime() - start) / 1_000_000;
            assertThat(resp.status()).as(area + " " + resp.body()).isEqualTo(200);
            assertThat(resp.body()).as(area.name()).doesNotContain("\"state\":\"ERROR\"");
            loads.put(area, new Load(stats.getPrepareStatementCount(), millis));
        }
        return loads;
    }

    private void seed(String admin, UUID zone, int managers, int teachers, int tasksPerManager, List<UUID> managerUsers) {
        for (int i = 0; i < managers; i++) {
            UUID user = newManager(admin, zone).signed().userId();
            managerUsers.add(user);
            for (int t = 0; t < tasksPerManager; t++) {
                Map<String, Object> body = new HashMap<>();
                body.put("type", "SCHOOL_VISIT");
                body.put("title", "Visit " + uniqueName("School"));
                body.put("assigneeUserId", user);
                body.put("dueOn", LocalDate.now().plusDays(1 + t).toString());
                assertThat(post("/api/v1/tasks", admin, body).status()).isEqualTo(201);
            }
        }
        for (int i = 0; i < teachers; i++) {
            teacher(admin);
        }
    }

    @Test
    void everyAreaLoadsFastAndWithAStatementCountThatIgnoresTheDataVolume() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zone = zone(admin);
        List<UUID> managerUsers = new ArrayList<>();

        seed(admin, zone, 4, 4, 3, managerUsers);
        Map<Area, Load> small = loadEveryArea(admin);

        seed(admin, zone, 12, 20, 5, managerUsers);
        Map<Area, Load> large = loadEveryArea(admin);

        for (Area area : Area.values()) {
            assertThat(large.get(area).millis()).as(area + " load time in ms").isLessThan(LIMIT_MS);
            assertThat(large.get(area).statements())
                    .as(area + " statements with more Managers, Teachers and tasks")
                    .isEqualTo(small.get(area).statements());
        }
    }
}
