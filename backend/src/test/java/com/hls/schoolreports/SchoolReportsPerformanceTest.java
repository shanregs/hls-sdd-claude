package com.hls.schoolreports;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.schoolbilling.ReceivablesTestBase;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spec 014b T029: with a growing number of Teachers and Managers across five billed Schools the Margin, Collections and School
 * reports answer their first page in under 3 seconds and their export in under 30, and the number of statements does not grow
 * with the number of people (no query per row).
 */
class SchoolReportsPerformanceTest extends ReceivablesTestBase {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private void seed(String admin, UUID zone, UUID[] schools, int managers, int teachers) {
        for (int i = 0; i < managers; i++) {
            newManager(admin, zone);
        }
        for (int i = 0; i < teachers; i++) {
            UUID teacher = teacher(admin);
            Resp placed = placeTeacherRaw(admin, teacher, schools[i % schools.length], LocalDate.now().minusDays(150));
            assertThat(placed.status()).as(placed.body()).isEqualTo(200);
        }
    }

    private long statementsOf(String url, String token) {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        get(url, token);
        stats.clear();
        Resp resp = get(url, token);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return stats.getPrepareStatementCount();
    }

    @Test
    void theThreeReportsAreFastAtFullSizeAndTheirStatementsIgnoreTheVolume() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zone = zone(admin);
        UUID[] schools = new UUID[5];
        for (int i = 0; i < schools.length; i++) {
            schools[i] = school(admin, place(admin, zone));
        }
        Map<String, String> urls = new LinkedHashMap<>();
        for (String key : new String[] {"margin", "collections", "school"}) {
            urls.put(key, "/api/v1/reports/" + key + "?month=" + thisMonth());
        }

        seed(admin, zone, schools, 5, 20);
        Map<String, Long> small = new LinkedHashMap<>();
        urls.forEach((key, url) -> small.put(key, statementsOf(url, admin)));

        seed(admin, zone, schools, 45, 480);
        for (var report : urls.entrySet()) {
            String key = report.getKey();
            String url = report.getValue();
            long start = System.nanoTime();
            Resp first = get(url, admin);
            long firstMillis = (System.nanoTime() - start) / 1_000_000;
            assertThat(first.status()).as(key + ": " + first.body()).isEqualTo(200);
            start = System.nanoTime();
            Resp export = get(url.replace("/api/v1/reports/" + key, "/api/v1/reports/" + key + "/export"), admin);
            long exportMillis = (System.nanoTime() - start) / 1_000_000;
            assertThat(export.status()).as(key + " export").isEqualTo(200);
            long large = statementsOf(url, admin);

            assertThat(small.get(key)).as(key + " statements counted").isGreaterThan(0);
            assertThat(firstMillis).as(key + " first page in ms").isLessThan(3_000);
            assertThat(exportMillis).as(key + " export in ms").isLessThan(30_000);
            assertThat(large).as(key + " statements at 500 Teachers and 50 Managers vs 20 and 5").isEqualTo(small.get(key));
        }
    }
}
